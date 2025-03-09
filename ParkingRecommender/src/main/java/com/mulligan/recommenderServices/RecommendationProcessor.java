package com.mulligan.recommenderServices;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

/**
 * Handles recommendation processing for parking spaces.
 * Implements a leader-follower architecture to distribute and aggregate recommendation tasks.
 * The leader node collects recommendations from follower nodes and determines the final recommendation using consensus.
 *
 * @author Jamal Majadle
 * @version 1.19.0
 */

public class RecommendationProcessor {
    private static final Logger logger = Logger.getLogger(RecommendationProcessor.class.getName());

    private static final String RECOMMENDATION_QUEUE_NAME = "recommendation_queue";
    private static final String LEADER_RECOMMENDATION_QUEUE = "leader_recommendation_queue";
    private static final String LEADER_FINAL_RECOMMENDATION_QUEUE = "leader_final_recommendation";
    private static final String FOLLOWER_TASK_QUEUE = "recommendation_task_exchange";

    private final String nodeId;
    private final Channel channel;
    private final MongoDatabase database;
    private boolean isLeader;
    private int expectedNodeCount = 0;

    public final Map<String, List<String>> receivedRecommendations = new ConcurrentHashMap<>();

    /**
     * Constructs a new RecommendationProcessor instance.
     *
     * @param nodeId          Unique identifier for the node in the distributed system.
     * @param channel         RabbitMQ channel used for inter-node communication.
     * @param database        MongoDB instance for retrieving and storing parking data.
     * @param minNodesRequired Minimum number of nodes required to participate in consensus.
     */
    public RecommendationProcessor(String nodeId, Channel channel, MongoDatabase database, int minNodesRequired) {
        this.nodeId = nodeId;
        this.channel = channel;
        this.database = database;
        this.expectedNodeCount = minNodesRequired;
    }

    /**
     * Starts processing recommendations based on the node's role.
     * The leader listens for client requests, while followers listen for tasks from the leader.
     *
     * @param isLeader True if the node is a leader, false if it is a follower.
     */
    public void startProcessing(boolean isLeader) {
        this.isLeader = isLeader;
        logger.info("📡 [" + nodeId + "] Node is listening for recommendations... | Is Leader: " + isLeader);

        if (isLeader) {
            listenForClientRequests();
            listenForNodeRecommendations();
        } else {
            listenForLeaderTasks();
        }

        listenForLeaderConsensus();
    }

    /**
     * Listens for incoming client requests on the leader node.
     * The leader then distributes the request to followers for recommendation processing.
     */
    private void listenForClientRequests() {
        try {
            channel.queueDeclare(RECOMMENDATION_QUEUE_NAME, false, false, false, null);
            logger.info(" [" + nodeId + "] Leader is listening for client requests on: " + RECOMMENDATION_QUEUE_NAME);

            channel.basicConsume(RECOMMENDATION_QUEUE_NAME, true, (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                String replyQueue = delivery.getProperties().getReplyTo();
                String correlationId = delivery.getProperties().getCorrelationId();
                logger.info(" [" + nodeId + "] Leader received client request: " + message);

                broadcastTaskToFollowers(message);

                new Thread(() -> {
                    List<String> finalRecommendations = waitForConsensus();
                    sendResponseToClient(replyQueue, correlationId, finalRecommendations);
                }).start();

            }, consumerTag -> {
            });
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Error listening for client requests: " + e.getMessage());
        }
    }

    /**
     * Waits until enough follower nodes have responded with recommendations.
     * This method blocks execution until a sufficient number of recommendations have been received.
     *
     * @return A list of recommended parking spaces based on the gathered responses.
     */
    private List<String> waitForConsensus() {
        while (!isConsensusReady()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        return performConsensus();
    }

    /**
     * Sends the final recommendation response back to the client.
     *
     * @param replyQueue   The queue where the client is waiting for a response.
     * @param correlationId The correlation ID to track the response uniquely.
     * @param recommendations The final list of recommended parking spaces.
     */
    private void sendResponseToClient(String replyQueue, String correlationId, List<String> recommendations) {
        try {
            String response = String.join(",", recommendations);
            AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                    .correlationId(correlationId)
                    .build();
            channel.basicPublish("", replyQueue, props, response.getBytes(StandardCharsets.UTF_8));
            logger.info(" [" + nodeId + "] Sent final recommendations to client: " + response);
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Error sending recommendation response to client: " + e.getMessage());
        }
    }

    /**
     * Broadcasts a recommendation task to all follower nodes.
     * This method is used by the leader to distribute a client request across the cluster.
     *
     * @param task The recommendation task to be processed by followers.
     * @throws IOException if an error occurs while broadcasting the task.
     */
    public void broadcastTaskToFollowers(String task) throws IOException {
        try {
            channel.exchangeDeclare(FOLLOWER_TASK_QUEUE, "fanout");
            channel.basicPublish(FOLLOWER_TASK_QUEUE, "", null, task.getBytes());
            logger.info(" [" + nodeId + "] Broadcasted recommendation task to followers: " + task);
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Failed to broadcast task: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Listens for recommendation tasks sent by the leader.
     * Each follower node processes the received task and generates its own recommendation.
     */
    private void listenForLeaderTasks() {
        try {
            channel.exchangeDeclare(FOLLOWER_TASK_QUEUE, "fanout");

            String queueName = nodeId + "_task_queue";
            channel.queueDeclare(queueName, false, false, false, null);
            channel.queueBind(queueName, FOLLOWER_TASK_QUEUE, "");

            logger.info(" [" + nodeId + "] Listening for leader instructions on: " + FOLLOWER_TASK_QUEUE + " | Queue: " + queueName);

            channel.basicConsume(queueName, true, (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                logger.info(" [" + nodeId + "] Received recommendation task from leader: " + message);

                processRecommendationRequest(message);
            }, consumerTag -> {
            });

        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Error listening for leader tasks: " + e.getMessage());
        }
    }

    /**
     * Processes a recommendation request received from the leader.
     * Generates a list of recommended parking spaces based on availability and citation history.
     *
     * @param message The recommendation request message sent by the leader.
     */
    private void processRecommendationRequest(String message) {
        String[] parts = message.split(":");
        if (parts.length < 2) {
            logger.warning(" [" + nodeId + "] Invalid request format: " + message);
            return;
        }

        String zoneName = parts[0];
        String requestedSpace = parts[1];

        List<String> recommendations = generateRecommendations(zoneName, requestedSpace);
        sendRecommendationsToLeader(recommendations);
    }

    /**
     * Sends the generated recommendations from a follower node to the leader node.
     *
     * @param recommendations The list of recommended parking spaces.
     */
    public void sendRecommendationsToLeader(List<String> recommendations) {
        try {
            String message = nodeId + ":" + String.join(",", recommendations);
            channel.basicPublish("", LEADER_RECOMMENDATION_QUEUE, null, message.getBytes());
            logger.info(" [" + nodeId + "] Sent recommendations to leader: " + message);
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Failed to send recommendations to leader: " + e.getMessage());
        }
    }

    /**
     * Listens for recommendation messages sent by follower nodes.
     * The leader collects these responses and waits for consensus to be reached.
     */
    private void listenForNodeRecommendations() {
        try {
            channel.queueDeclare(LEADER_RECOMMENDATION_QUEUE, false, false, false, null);
            logger.info(" [" + nodeId + "] Leader is listening for node recommendations.");

            channel.basicConsume(LEADER_RECOMMENDATION_QUEUE, true, (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), "UTF-8");
                logger.info(" [" + nodeId + "] Leader received recommendation: " + message);

                String[] parts = message.split(":");
                if (parts.length < 2) {
                    logger.warning(" [" + nodeId + "] Invalid recommendation format: " + message);
                    return;
                }

                String senderNode = parts[0];
                List<String> recommendations = Arrays.asList(parts[1].split(","));

                storeRecommendations(senderNode, recommendations);

                if (isConsensusReady()) {
                    List<String> finalRecommendation = performConsensus();
                    sendFinalRecommendation(finalRecommendation);
                }
            }, consumerTag -> {
            });
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Leader failed to listen for recommendations: " + e.getMessage());
        }
    }

    /**
     * Sends the final recommended parking space to all nodes.
     * This method is used by the leader after reaching a consensus.
     *
     * @param finalRecommendations The list of final recommendations.
     */
    public void sendFinalRecommendation(List<String> finalRecommendations) {
        try {
            String response = " Final Recommended Parking Spaces: " + String.join(", ", finalRecommendations);
            channel.basicPublish("", LEADER_FINAL_RECOMMENDATION_QUEUE, null, response.getBytes());
            logger.info(" [" + nodeId + "] Sent final recommendation: " + response);
        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Error sending final recommendation: " + e.getMessage());
        }
    }

    /**
     * Listens for the final consensus decision from the leader.
     * Follower nodes receive the leader's final recommendation and update their state accordingly.
     */
    private void listenForLeaderConsensus() {
        try {
            channel.queueDeclare(LEADER_FINAL_RECOMMENDATION_QUEUE, false, false, false, null);
            logger.info(" [" + nodeId + "] Listening for final consensus on: " + LEADER_FINAL_RECOMMENDATION_QUEUE);

            channel.basicConsume(LEADER_FINAL_RECOMMENDATION_QUEUE, true, (consumerTag, delivery) -> {
                String consensusDecision = new String(delivery.getBody(), "UTF-8");
                logger.info(" [" + nodeId + "] Received final consensus from leader: " + consensusDecision);
            }, consumerTag -> {
            });

        } catch (IOException e) {
            logger.severe(" [" + nodeId + "] Error listening for final consensus: " + e.getMessage());
        }
    }

    /**
     * Performs consensus by aggregating responses from follower nodes.
     * The method selects the most frequently recommended parking space based on a majority vote.
     *
     * @return A list of final recommendations based on consensus.
     */
    public List<String> performConsensus() {
        Map<String, Integer> voteCounts = new HashMap<>();
        for (List<String> recommendations : receivedRecommendations.values()) {
            for (String recommendation : recommendations) {
                voteCounts.put(recommendation, voteCounts.getOrDefault(recommendation, 0) + 1);
            }
        }

        if (voteCounts.isEmpty()) {
            logger.warning(" No valid recommendations received. Returning an empty list.");
            return Collections.emptyList();
        }

        int maxVotes = Collections.max(voteCounts.values());

        int totalVotes = voteCounts.values().stream().mapToInt(Integer::intValue).sum();

        if (receivedRecommendations.values().stream().distinct().count() == 1) {
            logger.info(" All nodes agree on recommendations: " + receivedRecommendations.values().iterator().next());
            return new ArrayList<>(receivedRecommendations.values().iterator().next());
        }

        if (maxVotes <= totalVotes / 2) {
            logger.info("No majority reached. Returning an empty list.");
            return Collections.emptyList();
        }

        List<String> finalRecommendations = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : voteCounts.entrySet()) {
            if (entry.getValue() == maxVotes) {
                finalRecommendations.add(entry.getKey());
            }
        }

        logger.info("Consensus result: " + finalRecommendations);
        return finalRecommendations;
    }

    /**
     * Stores recommendations received from follower nodes.
     * Once enough responses are collected, consensus is triggered.
     *
     * @param senderNode The sender node's ID.
     * @param recommendations The list of recommendations provided by the sender node.
     */
    public void storeRecommendations(String senderNode, List<String> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            logger.warning(" [" + nodeId + "] Received empty recommendations from node: " + senderNode);
            return;
        }

        receivedRecommendations.put(senderNode, recommendations);
        logger.info(" [" + nodeId + "] Stored recommendations from node: " + senderNode + " → " + recommendations);

        if (isLeader && isConsensusReady()) {
            List<String> finalRecommendation = performConsensus();
            sendFinalRecommendation(finalRecommendation);
        } else if (isLeader) {
            logger.info(" [" + nodeId + "] Leader waiting for more recommendations... Received: " + receivedRecommendations.size());
        }
    }

    /**
     * Generates recommendations for a given parking zone and requested space.
     * The function attempts to recommend the best available parking space based on citations and availability.
     *
     * @param zoneName The name of the parking zone.
     * @param requestedSpace The specific parking space requested by the user.
     * @return A list of recommended parking spaces.
     */
    public List<String> generateRecommendations(String zoneName, String requestedSpace) {
        int zoneId = getZoneId(zoneName);
        if (zoneId == -1) {
            logger.warning(" No matching zone found for: " + zoneName);
            return List.of(" Invalid parking zone provided.");
        }

        Map<Integer, Map<String, Object>> spaceData = fetchParkingDataFromDB(zoneId);

        Integer requestedSpaceId = validateRequestedSpace(spaceData, requestedSpace);

        if (requestedSpaceId == null || !spaceData.containsKey(requestedSpaceId)) {
            logger.info(" Requested space is unavailable. Finding nearest alternative...");
            return findNearestAvailableSpace(spaceData);
        }

        Map<Integer, Integer> citationCounts = fetchCitationsForSpaces(spaceData.keySet());

        if (spaceData.isEmpty()) {
            return List.of(" No parking spaces available in the selected zone.");
        }

        return handleRecommendations(spaceData, citationCounts, requestedSpaceId, requestedSpace);
    }

    /**
     * Finds the nearest available parking space based on the lowest ID.
     * The assumption is that lower IDs represent closer spaces.
     *
     * @param spaceData A map containing available parking spaces with their details.
     * @return A list containing a single nearest available parking space in the format "post_id;0".
     */
    private List<String> findNearestAvailableSpace(Map<Integer, Map<String, Object>> spaceData) {
        return spaceData.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .limit(1)
                .map(entry -> entry.getValue().get("post_id") + ";0")
                .toList();
    }

    /**
     * Validates if the requested parking space exists within the given parking zone.
     *
     * @param spaceData      A map containing available parking spaces with their details.
     * @param requestedSpace The requested parking space ID as a string.
     * @return The corresponding space ID if found, otherwise null.
     */
    private Integer validateRequestedSpace(Map<Integer, Map<String, Object>> spaceData, String requestedSpace) {
        return spaceData.entrySet().stream()
                .filter(entry -> entry.getValue().get("post_id").equals(requestedSpace))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * Handles the recommendation logic by selecting the best parking space based on citation history.
     * It considers the requested space first, and if unavailable, finds the space with the least citations.
     *
     * @param spaceData        A map containing parking spaces and their details.
     * @param citationCounts   A map containing citation counts for each parking space.
     * @param requestedSpaceId The ID of the requested parking space.
     * @param requestedSpace   The requested parking space as a string.
     * @return A list of recommended parking spaces formatted as "post_id;citation_count".
     */
    private List<String> handleRecommendations(Map<Integer, Map<String, Object>> spaceData,
                                               Map<Integer, Integer> citationCounts,
                                               Integer requestedSpaceId,
                                               String requestedSpace) {
        citationCounts.forEach((spaceId, count) -> citationCounts.putIfAbsent(spaceId, 0));

        int minCitations = citationCounts.values().stream().min(Integer::compare).orElse(0);

        logger.info(" Processing Recommendations - Min Citations: " + minCitations);

        if (requestedSpaceId != null && citationCounts.getOrDefault(requestedSpaceId, 0) == minCitations) {
            return List.of(requestedSpace + ";" + minCitations);
        }

        List<Integer> minCitationSpaceIds = citationCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == minCitations && (requestedSpaceId == null || !entry.getKey().equals(requestedSpaceId)))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparingInt(spaceId -> requestedSpaceId != null ? Math.abs(spaceId - requestedSpaceId) : spaceId))
                .limit(2)
                .toList();

        if (!minCitationSpaceIds.isEmpty()) {
            return generateRecommendationsList(spaceData, citationCounts, minCitationSpaceIds);
        }

        return recommendBestAvailable(spaceData, citationCounts, requestedSpaceId);
    }

    /**
     * Generates a list of recommended parking spaces with the lowest citation count.
     * This method prioritizes the closest spaces with the least number of citations.
     *
     * @param spaceData      A map containing parking spaces and their details.
     * @param citationCounts A map containing citation counts for each parking space.
     * @param spaceIds       A list of space IDs that have the minimum citation count.
     * @return A list of recommended parking spaces formatted as "post_id;citation_count".
     */
    private List<String> generateRecommendationsList(Map<Integer, Map<String, Object>> spaceData,
                                                     Map<Integer, Integer> citationCounts,
                                                     List<Integer> spaceIds) {
        List<String> recommendations = new ArrayList<>();
        for (Integer spaceId : spaceIds) {
            String postId = (String) spaceData.get(spaceId).get("post_id");
            int citations = citationCounts.getOrDefault(spaceId, 0);
            recommendations.add(postId + ";" + citations);
        }
        logger.info(" Recommended Spaces: " + recommendations);
        return recommendations;
    }

    /**
     * Recommends the best available parking space when no requested space or minimum citation space is found.
     * The method selects the closest available space based on its ID.
     *
     * @param spaceData        A map containing available parking spaces with their details.
     * @param citationCounts   A map containing citation counts for each parking space.
     * @param requestedSpaceId The ID of the requested parking space.
     * @return A list containing one recommended parking space formatted as "post_id;citation_count".
     */
    private List<String> recommendBestAvailable(Map<Integer, Map<String, Object>> spaceData,
                                                Map<Integer, Integer> citationCounts,
                                                Integer requestedSpaceId) {
        return citationCounts.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> Math.abs(entry.getKey() - requestedSpaceId)))
                .limit(1)
                .map(entry -> spaceData.get(entry.getKey()).get("post_id") + ";" + entry.getValue())
                .toList();
    }

    /**
     * Retrieves citation counts for a given set of parking spaces.
     * Citations indicate how frequently a parking space has been fined or reported.
     *
     * @param spaceIds The set of parking space IDs to retrieve citation counts for.
     * @return A map of parking space IDs to their citation counts.
     */
    private Map<Integer, Integer> fetchCitationsForSpaces(Set<Integer> spaceIds) {
        Map<Integer, Integer> citationCounts = new HashMap<>();

        try {
            for (Integer spaceId : spaceIds) {
                citationCounts.put(spaceId, 0);
            }

            MongoCollection<Document> citationsCollection = database.getCollection("Citations");
            List<Document> citations = citationsCollection.find(Filters.in("parking_space_id", spaceIds)).into(new ArrayList<>());

            for (Document citation : citations) {
                Integer spaceId = citation.getInteger("parking_space_id");
                citationCounts.put(spaceId, citationCounts.getOrDefault(spaceId, 0) + 1);
            }

            logger.info(" Updated Citation counts: " + citationCounts);
        } catch (Exception e) {
            logger.severe(" Failed to fetch citations: " + e.getMessage());
        }

        return citationCounts;
    }


    private int getZoneId(String zoneName) throws RuntimeException {
        MongoCollection<Document> zonesCollection = database.getCollection("parking_zones");
        Document zone = zonesCollection.find(Filters.eq("zone_name", zoneName)).first();

        if (zone != null) {
            return zone.getInteger("id", -1);
        }
        throw new RuntimeException("Zone not found: " + zoneName);
    }

    /**
     * Fetches available parking spaces from the database for a given zone.
     * Occupied spaces are automatically filtered out.
     *
     * @param zoneId The ID of the zone to retrieve parking spaces for.
     * @return A map containing details of available parking spaces.
     */
    public Map<Integer, Map<String, Object>> fetchParkingDataFromDB(int zoneId) {
        Map<Integer, Map<String, Object>> spaceData = new HashMap<>();

        try {
            MongoCollection<Document> collection = database.getCollection("parking_spaces");
            List<Document> spaces = collection.find(Filters.eq("zone_id", zoneId)).into(new ArrayList<>());

            if (spaces.isEmpty()) {
                logger.warning(" No parking spaces found for zone ID: " + zoneId);
                return new HashMap<>();
            }

            Set<Integer> occupiedSpaces = getOccupiedSpaces();

            for (Document doc : spaces) {
                Integer id = doc.getInteger("id");

                if (occupiedSpaces.contains(id)) {
                    logger.info(" Skipping occupied parking space: " + id);
                    continue;
                }

                String postId = doc.getString("post_id");
                int citations = doc.getInteger("citations", 0);

                Map<String, Object> details = new HashMap<>();
                details.put("post_id", postId);
                details.put("citations", citations);

                spaceData.put(id, details);
            }

            logger.info(" Successfully fetched available parking spaces for zone_id " + zoneId + ": " + spaceData);
        } catch (Exception e) {
            logger.severe(" Failed to fetch parking spaces for zone_id " + zoneId + ": " + e.getMessage());
        }

        return spaceData;
    }

    /**
     * Fetches a list of occupied parking spaces.
     * Spaces that are currently in use are excluded from the recommendation list.
     *
     * @return A set containing the IDs of occupied parking spaces.
     */
    private Set<Integer> getOccupiedSpaces() {
        Set<Integer> occupiedSpaces = new HashSet<>();
        try {
            MongoCollection<Document> transactionsCollection = database.getCollection("Transactions");

            List<Document> activeTransactions = transactionsCollection.find(Filters.or(
                    Filters.eq("end", null),
                    Filters.exists("end", false)
            )).into(new ArrayList<>());

            for (Document transaction : activeTransactions) {
                Integer spaceId = transaction.getInteger("parking_space_id");
                occupiedSpaces.add(spaceId);
            }

            logger.info(" Occupied Spaces: " + occupiedSpaces);
        } catch (Exception e) {
            logger.severe(" Failed to fetch occupied spaces: " + e.getMessage());
        }

        return occupiedSpaces;
    }

    /**
     * Determines whether consensus has been reached among follower nodes.
     * The system requires a minimum number of responses before a decision is finalized.
     *
     * @return True if consensus is reached, false otherwise.
     */
    public boolean isConsensusReady() {
        return receivedRecommendations.size() >= expectedNodeCount;
    }
}

