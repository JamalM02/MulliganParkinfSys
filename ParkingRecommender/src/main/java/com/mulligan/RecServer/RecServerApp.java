package com.mulligan.RecServer;

import com.mongodb.client.MongoDatabase;
import com.mulligan.recommenderServices.RecommendationProcessor;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * The {@code RecServerApp} class is responsible for managing a distributed recommendation server.
 * It handles leader election, node registration, heartbeat monitoring, and recommendation processing.
 * This class ensures that nodes in the cluster can detect leaders, register themselves,
 * and participate in a consensus-based recommendation system.
 *
 *
 * The system is built using RabbitMQ for communication and MongoDB for data storage.
 * Nodes use a heartbeat mechanism to detect failures and dynamically elect a new leader if needed.
 *
 *
 *
 * Key features include:
 *
 *     Leader election using a distributed approach.
 *     Heartbeat mechanism to detect leader and node failures.
 *     Dynamic node registration and discovery.
 *     Recommendation processing distributed across nodes.
 *
 *
 *
 * @author Jamal Majadle
 * @version 2.10.0
 */
public class RecServerApp {
    private static final Logger logger = Logger.getLogger(RecServerApp.class.getName());
    private static final int HEARTBEAT_INTERVAL = 5;
    public static final int LEADER_CHECK_INTERVAL = 7;
    private static final int INITIAL_WAIT_TIME = 5;
    private static int MIN_NODES_REQUIRED = 0;
    private final String heartbeatExchange = "heartbeat_exchange";
    private final String exchangeName = "leader_election_exchange";


    private final String nodeId;
    private final Channel channel;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public volatile boolean isLeader = false;
    public volatile Set<String> activeNodes = new HashSet<>();
    public volatile String currentLeader = null;
    public RecommendationProcessor recommendationProcessor;
    public volatile long lastHeartbeatTime = System.currentTimeMillis();

    /**
     * Constructs a new instance of {@code RecServerApp} and starts monitoring for leader heartbeats.
     *
     * @param nodeId           The unique identifier of the node.
     * @param channel          The RabbitMQ channel for communication.
     * @param database         The MongoDB database instance used for recommendations.
     * @param minNodesRequired The minimum number of nodes required for leader election.
     */
    public RecServerApp(String nodeId, Channel channel, MongoDatabase database, int minNodesRequired) {
        this.nodeId = nodeId;
        this.channel = channel;
        MIN_NODES_REQUIRED = minNodesRequired;
        this.recommendationProcessor = new RecommendationProcessor(nodeId, channel, database, MIN_NODES_REQUIRED);
        monitorHeartbeatBeforeRegister();
    }

    /**
     * Listens for heartbeat messages before attempting registration.
     * <p>
     * This method waits for a predefined period to determine if a leader already exists.
     * If a leader is detected, the node synchronizes its active node list and monitors the leader.
     * If no leader is found, the node proceeds with registration to start the leader election process.
     * </p>
     */
    public void monitorHeartbeatBeforeRegister() {
        try {

            channel.exchangeDeclare(heartbeatExchange, "fanout");

            String tempQueue = channel.queueDeclare().getQueue();
            channel.queueBind(tempQueue, heartbeatExchange, "");

            logger.info(" [" + nodeId + "] Listening for heartbeats before registering...");

            String consumerTag = channel.basicConsume(tempQueue, true, (consumerTagTag, delivery) -> {
                String receivedMessage = new String(delivery.getBody(), StandardCharsets.UTF_8);

                if (receivedMessage.startsWith("HEARTBEAT:")) {
                    String leaderId = receivedMessage.split(":")[1];
                    lastHeartbeatTime = System.currentTimeMillis();
                    activeNodes.add(leaderId);
                    logger.info(" [" + nodeId + "] Detected heartbeat from leader: " + leaderId);
                }

                if (receivedMessage.startsWith("NODE_LIST_UPDATE:")) {
                    String[] nodes = receivedMessage.replace("NODE_LIST_UPDATE:", "").split(",");
                    activeNodes.clear();
                    activeNodes.addAll(Arrays.asList(nodes));
                    logger.info(" [" + nodeId + "] Updated active nodes: " + activeNodes);
                }
            }, consumerTagTag -> logger.warning("[" + nodeId + "] Initial heartbeat monitoring canceled."));

            scheduler.schedule(() -> {
                try {
                    channel.basicCancel(consumerTag);
                    logger.info(" [" + nodeId + "] Stopped initial heartbeat monitoring.");

                    if (leaderHeartbeatReceivedRecently()) {
                        logger.info(" [" + nodeId + "] Leader detected. Syncing active nodes.");

                        if (currentLeader == null) {
                            List<String> sortedNodes = new ArrayList<>(activeNodes);
                            Collections.sort(sortedNodes);
                            currentLeader = sortedNodes.get(0);
                            logger.info(" [" + nodeId + "] Discovered leader: " + currentLeader);
                        }

                        if (currentLeader.equals(nodeId)) {
                            logger.info(" [" + nodeId + "] I am the leader. Starting heartbeat...");
                            startHeartbeat();
                            startRecommendationProcessor();
                        } else {
                            logger.info(" [" + nodeId + "] Following leader: " + currentLeader);
                            monitorLeaderHeartbeat();
                        }
                    } else {
                        logger.warning(" [" + nodeId + "] No leader detected! Proceeding with registration...");
                        registerNode();
                    }
                } catch (IOException e) {
                    logger.severe(" [" + nodeId + "] Error stopping initial monitoring: " + e.getMessage());
                }
            }, INITIAL_WAIT_TIME, TimeUnit.SECONDS);

        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Failed to listen for heartbeats: " + e.getMessage());
        }
    }

    /**
     * Registers the current node in the leader election exchange.
     * <p>
     * This method announces the node’s presence in the network and listens for registration
     * messages from other nodes. If enough nodes are available, a leader election is triggered.
     * </p>
     */
    public void registerNode() {
        try {
            channel.exchangeDeclare(exchangeName, "fanout");

            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, exchangeName, "");

            logger.info("[" + nodeId + "] Declared its queue: " + queueName);

            recommendationProcessor.startProcessing(false);

            channel.basicConsume(queueName, true, (consumerTag, delivery) -> {
                String receivedNode = new String(delivery.getBody(), "UTF-8");
                if (!activeNodes.contains(receivedNode)) {
                    activeNodes.add(receivedNode);
                    logger.info("[" + nodeId + "] Received registration from: " + receivedNode);
                    channel.basicPublish(exchangeName, "", null, nodeId.getBytes());
                    logger.info("[" + nodeId + "] Re-announced itself so all nodes sync.");
                }
                if (activeNodes.size() >= MIN_NODES_REQUIRED && currentLeader == null) {
                    logger.info("All nodes have seen each other. Starting leader election...");
                    startLeaderElection();
                } else {
                    logger.info("Waiting for more nodes. Need " + (MIN_NODES_REQUIRED - activeNodes.size()) + " more...");
                }
            }, consumerTag -> logger.warning("Consumer for " + nodeId + " was canceled."));

            channel.basicPublish(exchangeName, "", null, nodeId.getBytes());
            logger.info("Sent initial registration message: " + nodeId);
        } catch (IOException e) {
            logger.severe("Node registration error: " + e.getMessage());
        }
    }

    /**
     * Initiates the leader election process.
     * <p>
     * This method sorts the active nodes by their IDs and elects the node with the lowest ID as the leader.
     * If the current node is elected, it begins sending heartbeat messages. Otherwise, it monitors the leader.
     * </p>
     */
    public void startLeaderElection() {
        List<String> sortedNodes = new ArrayList<>(activeNodes);
        Collections.sort(sortedNodes);
        currentLeader = sortedNodes.get(0);

        if (currentLeader.equals(nodeId)) {
            isLeader = true;
            logger.info("I am the leader: " + nodeId);
            startHeartbeat();
            startRecommendationProcessor();
        } else {
            isLeader = false;
            logger.info("Leader elected: " + currentLeader);
            monitorLeaderHeartbeat();
            recommendationProcessor.startProcessing(isLeader);
        }
    }

    /**
     * Starts the recommendation processor.
     * <p>
     * If the node is elected as the leader, it begins processing recommendation requests.
     * If the node is not the leader, it waits for instructions from the leader.
     * </p>
     */
    public void startRecommendationProcessor() {
        if (isLeader) {
            logger.info("Starting recommendation processor as leader.");
            recommendationProcessor.startProcessing(true);
        } else {
            logger.info("Not a leader, waiting for recommendations.");
            recommendationProcessor.startProcessing(false);
        }
    }

    /**
     * Starts sending periodic heartbeat messages if the node is the leader.
     * <p>
     * The leader sends heartbeats and updates the list of active nodes at a fixed interval.
     * These messages help follower nodes detect when a leader is down.
     * </p>
     */
    public void startHeartbeat() {
        try {

            channel.exchangeDeclare(heartbeatExchange, "fanout");

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    String heartbeatMessage = "HEARTBEAT:" + nodeId;
                    String activeNodesMessage = "NODE_LIST_UPDATE:" + String.join(",", activeNodes);

                    channel.basicPublish(heartbeatExchange, "", null, heartbeatMessage.getBytes(StandardCharsets.UTF_8));
                    channel.basicPublish(heartbeatExchange, "", null, activeNodesMessage.getBytes(StandardCharsets.UTF_8));

                    logger.info(" [" + nodeId + "] Sent heartbeat and active nodes update.");
                } catch (IOException e) {
                    logger.severe(" Failed to send heartbeat: " + e.getMessage());
                }
            }, 0, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

        } catch (IOException e) {
            logger.severe(" Failed to declare heartbeat exchange: " + e.getMessage());
        }
    }

    /**
     * Monitors the leader’s heartbeat.
     * <p>
     * This method listens for heartbeat messages from the leader.
     * If the leader stops sending heartbeats, the node assumes the leader is down
     * and initiates a new leader election.
     * </p>
     */
    public void monitorLeaderHeartbeat() {
        try {

            channel.exchangeDeclare(heartbeatExchange, "fanout");

            String heartbeatQueue = channel.queueDeclare().getQueue();
            channel.queueBind(heartbeatQueue, heartbeatExchange, "");

            logger.info(" [" + nodeId + "] Listening for heartbeats & active nodes updates...");

            channel.basicConsume(heartbeatQueue, true, (consumerTag, delivery) -> {
                String receivedMessage = new String(delivery.getBody(), StandardCharsets.UTF_8);

                if (receivedMessage.startsWith("HEARTBEAT:")) {
                    String senderNode = receivedMessage.split(":")[1];
                    lastHeartbeatTime = System.currentTimeMillis();
                    if (!activeNodes.contains(senderNode)) {
                        activeNodes.add(senderNode);
                    }
                    logger.info(" [" + nodeId + "] Heartbeat received from: " + senderNode);
                }

                if (receivedMessage.startsWith("NODE_LIST_UPDATE:")) {
                    String[] nodes = receivedMessage.replace("NODE_LIST_UPDATE:", "").split(",");
                    activeNodes.clear();
                    activeNodes.addAll(Arrays.asList(nodes));
                    logger.info(" [" + nodeId + "] Updated active nodes: " + activeNodes);
                }
            }, consumerTag -> logger.warning("[" + nodeId + "] Heartbeat monitoring canceled."));

        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Failed to listen for heartbeats: " + e.getMessage());
        }

        scheduler.scheduleAtFixedRate(() -> {
            if (!leaderHeartbeatReceivedRecently()) {
                logger.warning(" [" + nodeId + "] Leader is unresponsive! Removing from active nodes...");
                activeNodes.clear();
                logger.info(" [" + nodeId + "] Reset active nodes.");
                registerNode();
            }
        }, 0, LEADER_CHECK_INTERVAL, TimeUnit.SECONDS);
    }

    /**
     * Checks if the node has received a heartbeat from the leader within the expected interval.
     *
     * @return {@code true} if a heartbeat was received within the last {@code HEARTBEAT_INTERVAL} seconds;
     * {@code false} otherwise.
     */
    public boolean leaderHeartbeatReceivedRecently() {
        return (System.currentTimeMillis() - lastHeartbeatTime) < (HEARTBEAT_INTERVAL * 1000);
    }

}
