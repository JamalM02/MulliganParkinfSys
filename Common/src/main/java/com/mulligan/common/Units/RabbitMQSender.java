package com.mulligan.common.Units;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.mulligan.common.models.Citation;
import com.mulligan.common.models.Transaction;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Handles communication with RabbitMQ for sending and receiving messages related to parking transactions and citations.
 * Key Features:
 * Publishes requests and retrieves responses for transactions and citations.
 * Parses responses into Transaction and Citation objects.
 * Provides methods for dynamically loading parking zones and spaces.
 *
 * @author Jamal Majadle
 * @version 1.20.0
 */
public class RabbitMQSender {
    private static final String TRANSACTIONS_QUEUE_NAME = "transactions_queue";
    private static final String CITATIONS_QUEUE_NAME = "citations_queue";
    private static final String RECOMMENDATION_QUEUE_NAME = "recommendation_queue";
    private Connection connection;
    public Channel channel;

    private final String rabbitmqHost;
    private final String[] rabbitmqPorts;
    private final String rabbitmqUser;
    private final String rabbitmqPass;

    private final Logger logger = Logger.getLogger(RabbitMQSender.class.getName());

    /**
     * Initializes a new RabbitMQSender instance.
     * Sets up connections to RabbitMQ clusters and declares necessary queues.
     *
     * @throws IOException      If an I/O error occurs during initialization.
     * @throws TimeoutException If the connection to RabbitMQ times out.
     */
    public RabbitMQSender() throws IOException, TimeoutException, InterruptedException {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        rabbitmqHost = dotenv.get("RABBITMQ_HOST", "localhost");
        rabbitmqPorts = dotenv.get("RABBITMQ_PORTS", "5672").split(",");
        rabbitmqUser = dotenv.get("RABBITMQ_USER", "guest");
        rabbitmqPass = dotenv.get("RABBITMQ_PASS", "guest");

        connectToRabbitMQ();
    }

    /**
     * Constructor for creating a RabbitMQSender instance with a mocked or pre-configured {@link Channel}.
     * This constructor is typically used for unit testing purposes, allowing the injection of a mocked
     * {@link Channel} to simulate RabbitMQ behavior without requiring a live RabbitMQ server.
     * It disables connection configuration, as it assumes the {@link Channel} is already provided.
     *
     * @param mockChannel The {@link Channel} instance to be used by this RabbitMQSender.
     *                    Typically a mocked instance for testing purposes.
     */
    public RabbitMQSender(Channel mockChannel) {
        this.channel = mockChannel;
        this.rabbitmqPorts = null;
        this.rabbitmqHost = null;
        this.rabbitmqUser = null;
        this.rabbitmqPass = null;
    }

    /**
     * Establishes a connection to RabbitMQ servers.
     * The method iterates through a list of RabbitMQ hosts, attempting to connect to each one.
     * It uses automatic recovery and retries connections every 5 seconds if all hosts fail.
     * Once connected, it creates a channel and declares the required queues.
     *
     * @throws InterruptedException if the thread is interrupted while waiting between retries.
     */
    private void connectToRabbitMQ() throws InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(rabbitmqUser);
        factory.setPassword(rabbitmqPass);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);

        while (true) {
            for (String port : rabbitmqPorts) {
                try {
                    factory.setHost(rabbitmqHost);
                    factory.setPort(Integer.parseInt(port.trim()));

                    connection = factory.newConnection();
                    channel = connection.createChannel();
                    channel.queueDeclare(TRANSACTIONS_QUEUE_NAME, false, false, false, null);
                    channel.queueDeclare(CITATIONS_QUEUE_NAME, false, false, false, null);
                    channel.queueDeclare(RECOMMENDATION_QUEUE_NAME, false, false, false, null);

                    logger.info("Connected to RabbitMQ at: " + port);
                    return;
                } catch (Exception e) {
                    logger.warning("Failed to connect to RabbitMQ port: " + port + " - " + e.getMessage());
                }
            }
            logger.warning("Retrying connection to RabbitMQ in 5 seconds...");
            Thread.sleep(5000);
        }
    }

    /**
     * Checks the RabbitMQ connection and reconnects if necessary.
     * This method verifies if the connection to RabbitMQ is active. If the connection is lost or
     * closed, it attempts to re-establish the connection by invoking the {@code connectToRabbitMQ} method.
     * Logs appropriate warnings or errors during the reconnection process.
     */
    public void reconnectIfNecessary() {
        try {
            if (connection == null || !connection.isOpen()) {
                logger.warning("Connection lost. Reconnecting...");
                connectToRabbitMQ();
            }
        } catch (Exception e) {
            logger.severe("Failed to reconnect: " + e.getMessage());
        }
    }

    /**
     * Sends a transaction-related request and retrieves the response.
     *
     * @param message The message to send.
     * @return The response from RabbitMQ.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws TimeoutException     If no response is received within the timeout period.
     */
    public String sendTransaction(String message) throws IOException, InterruptedException, TimeoutException {
        reconnectIfNecessary();
        return sendAndReceive(message, TRANSACTIONS_QUEUE_NAME);
    }

    /**
     * Sends a citation-related request and retrieves the response.
     *
     * @param message The message to send.
     * @return The response from RabbitMQ.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws TimeoutException     If no response is received within the timeout period.
     */
    public String sendCitation(String message) throws IOException, InterruptedException, TimeoutException {
        reconnectIfNecessary();
        return sendAndReceive(message, CITATIONS_QUEUE_NAME);
    }


    public String sendRecommendations(String message) throws IOException, InterruptedException, TimeoutException {
        reconnectIfNecessary();
        return sendAndReceive(message, RECOMMENDATION_QUEUE_NAME);
    }


    /**
     * Requests a list of transactions from RabbitMQ.
     *
     * @return A list of Transaction objects parsed from the response.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws TimeoutException     If no response is received within the timeout period.
     */
    public List<Transaction> requestTransactions() throws IOException, InterruptedException, TimeoutException {
        String response = sendAndReceive("GET_TRANSACTIONS", TRANSACTIONS_QUEUE_NAME);
        return parseTransactions(response);
    }

    /**
     * Requests a list of citations from RabbitMQ.
     *
     * @return A list of Citation objects parsed from the response.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws TimeoutException     If no response is received within the timeout period.
     */
    public List<Citation> requestCitations() throws IOException, InterruptedException, TimeoutException {
        String response = sendAndReceive("GET_CITATIONS", CITATIONS_QUEUE_NAME);
        return parseCitations(response);
    }

    /**
     * Sends a message to RabbitMQ and waits for a response.
     *
     * @param message   The message to send.
     * @param queueName The name of the queue to which the message will be sent.
     * @return The response from RabbitMQ.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the thread is interrupted.
     * @throws TimeoutException     If no response is received within the timeout period.
     */
    protected String sendAndReceive(String message, String queueName) throws IOException, InterruptedException, TimeoutException {
        String correlationId = UUID.randomUUID().toString();
        String replyQueueName = channel.queueDeclare().getQueue();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(correlationId)
                .replyTo(replyQueueName)
                .build();

        channel.basicPublish("", queueName, props, message.getBytes(StandardCharsets.UTF_8));
        final BlockingQueue<String> response = new ArrayBlockingQueue<>(1);

        String consumerTag = channel.basicConsume(replyQueueName, true, (consumerTag1, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(correlationId)) {
                response.offer(new String(delivery.getBody(), StandardCharsets.UTF_8));
            }
        }, consumerTag1 -> {
        });

        String result = response.poll(15, TimeUnit.SECONDS);
        channel.basicCancel(consumerTag);

        if (result == null) {
            throw new TimeoutException("No response received within timeout period");
        }

        return result;
    }

    /**
     * Loads a list of parking zones or spaces from RabbitMQ.
     *
     * @param requestType The type of request (e.g., "GET_ALL_PARKING_ZONES").
     * @param params      Additional parameters for the request (e.g., zone name).
     * @return A list of parking zones or spaces.
     */
    public List<String> loadParkingLists(String requestType, String... params) {
        try {
            String message = requestType + (params.length > 0 ? ":" + params[0] : "");
            String response = sendTransaction(message);
            return Arrays.stream(response.split(",")).map(String::trim).collect(Collectors.toList());
        } catch (IOException | InterruptedException | TimeoutException e) {
            e.printStackTrace();
        }
        return List.of();
    }

    /**
     * Parses a response string into a list of Transaction objects.
     *
     * @param response The response string from RabbitMQ.
     * @return A list of parsed Transaction objects.
     */
    public List<Transaction> parseTransactions(String response) {
        if (response == null || response.isEmpty()) {
            System.err.println("Empty transaction response");
            return List.of();
        }

        List<Transaction> transactions = new ArrayList<>();
        String[] messages = response.split("\\|");

        for (String message : messages) {
            String[] parts = message.split(";");
            if (parts.length < 6) {
                System.err.println("Skipping malformed transaction entry: " + message);
                continue;
            }
            try {
                String vin = parts[0].trim();
                String parkingSpace = parts[1].trim();
                String zoneName = parts[2].trim();
                String start = parts[3].trim();
                String end = "null".equalsIgnoreCase(parts[4].trim()) ? null : parts[4].trim();
                double amount = "null".equalsIgnoreCase(parts[5].trim()) || parts[5].trim().isEmpty() ? 0.0 : Double.parseDouble(parts[5].trim());

                transactions.add(new Transaction(vin, parkingSpace, zoneName, start, end, amount));
            } catch (Exception e) {
                System.err.println("Failed to parse transaction entry: " + message + " - Error: " + e.getMessage());
            }
        }

        return transactions;
    }


    /**
     * Parses a response string into a list of Citation objects.
     *
     * @param response The response string from RabbitMQ.
     * @return A list of parsed Citation objects.
     */
    public List<Citation> parseCitations(String response) {
        if (response == null || response.isEmpty()) {
            return List.of();
        }

        List<Citation> citations = new ArrayList<>();
        String[] messages = response.split("\\|");

        for (String message : messages) {
            message = message.trim();
            String[] parts = message.split(";");

            if (parts.length >= 5) {
                try {
                    String licensePlate = parts[0].trim();
                    String parkingSpace = parts[1].trim();
                    String zone = parts[2].trim();
                    String issueTime = parts[3].trim();
                    double amount = Double.parseDouble(parts[4].trim());
                    String reason = parts.length > 5 ? parts[5].trim() : "Unknown Reason";

                    if ("-1".equals(parkingSpace)) {
                        parkingSpace = null;
                        zone = null;
                    }

                    citations.add(new Citation(
                            licensePlate,
                            parkingSpace,
                            zone,
                            issueTime,
                            amount,
                            reason
                    ));
                } catch (Exception e) {
                    System.err.println("Failed to parse citation entry: " + message + " - Error: " + e.getMessage());
                }
            } else {
                System.err.println("Malformed citation entry: " + message);
            }
        }

        return citations;
    }

}

