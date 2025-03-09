package com.mulligan.server;

import com.mongodb.client.MongoDatabase;
import com.mulligan.RecServer.RecServerApp;
import com.mulligan.server.services.CitationService;
import com.mulligan.server.services.ParkingService;
import com.mulligan.server.services.TransactionService;
import com.rabbitmq.client.*;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * RabbitMQConsumer handles messaging integration with RabbitMQ for parking and citation operations.
 *
 * This class listens to RabbitMQ queues, processes messages, and delegates operations to
 * `TransactionService`, `ParkingService`, and `CitationService`. It includes features for:
 *
 * Listening to RabbitMQ queues for transactions and citations
 * Handling various commands related to parking and citations
 * Establishing and maintaining RabbitMQ connections with automatic recovery
 *
 *
 * Dependencies:
 *
 * RabbitMQ Client Library
 * TransactionService, ParkingService, and CitationService
 * Dotenv for environment configuration
 *
 *
 * @author Oran Alster
 * @author Jamal Majadle
 * @version 1.20.0
 */
public class RabbitMQConsumer {
    private final static String QUEUE_NAME_TRANSACTIONS = "transactions_queue";
    private final static String QUEUE_NAME_CITATIONS = "citations_queue";


    private static final int RETRY_INTERVAL_MS = 5000;

    private final TransactionService transactionService;
    private final ParkingService parkingService;
    private final CitationService citationService;
    private Connection connection;
    private Channel channel;
    private final Logger logger = Logger.getLogger(RabbitMQConsumer.class.getName());

    private final String rabbitmqHost;
    private final String rabbitmqPorts[];
    private final String rabbitmqUser;
    private final String rabbitmqPass;
    private final String nodeId;
    private final MongoDatabase database;
    private final ConnectionFactory connectionFactory;
    Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();


    /**
     * Initializes a RabbitMQConsumer instance.
     *
     * Sets up a connection to RabbitMQ using environment variables for configuration. It also
     * declares the required queues for transactions and citations.
     *
     * @param transactionService Service for transaction operations.
     * @param parkingService     Service for parking-related queries.
     * @param citationService    Service for citation management.
     * @param database
     * @throws Exception If an error occurs while establishing a connection to RabbitMQ.
     */
    public RabbitMQConsumer(TransactionService transactionService, ParkingService parkingService, CitationService citationService, MongoDatabase database) throws Exception {
        this.transactionService = transactionService;
        this.parkingService = parkingService;
        this.citationService = citationService;
        this.database = database;
        this.connectionFactory = null;
        rabbitmqHost = dotenv.get("RABBITMQ_HOST", "localhost");
        rabbitmqPorts = dotenv.get("RABBITMQ_PORTS", "5672").split(",");
        rabbitmqUser = dotenv.get("RABBITMQ_USER", "guest");
        rabbitmqPass = dotenv.get("RABBITMQ_PASS", "guest");
        nodeId = dotenv.get("NODE_ID");


        connectToRabbitMQ();
    }

    /**
     * Constructor for creating a RabbitMQConsumer instance with a pre-configured or mocked {@link Channel}.
     * This constructor is primarily intended for unit testing, enabling dependency injection of a mocked
     * {@link Channel} without requiring an actual RabbitMQ server connection.
     * It disables connection configuration as it assumes the {@link Channel} is externally managed.
     *
     * @param transactionService The {@link TransactionService} instance for handling transactions.
     * @param parkingService The {@link ParkingService} instance for managing parking-related logic.
     * @param citationService The {@link CitationService} instance for handling citation processing.
     * @param database The {@link MongoDatabase} instance for interacting with the database.
     * @param mockChannel The mocked {@link Channel} instance for testing purposes.
     */
    public RabbitMQConsumer(TransactionService transactionService, ParkingService parkingService,
                            CitationService citationService, MongoDatabase database, Channel mockChannel) {
        this.transactionService = transactionService;
        this.parkingService = parkingService;
        this.citationService = citationService;
        this.database = database;
        this.channel = mockChannel;
        this.connection = null;
        this.connectionFactory = null;
        this.rabbitmqHost = null;
        this.rabbitmqPorts = null;
        this.rabbitmqUser = null;
        this.rabbitmqPass = null;
        this.nodeId = "TestNode";
    }



    /**
     * Establishes a connection to RabbitMQ servers.
     *
     * <p>This method iterates through a list of RabbitMQ hosts, trying to connect to each until successful.
     * It declares necessary queues and sets up automatic recovery.
     *
     * @throws InterruptedException If the thread is interrupted during retry waits.
     */
    private void connectToRabbitMQ() throws InterruptedException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUsername(rabbitmqUser);
        factory.setPassword(rabbitmqPass);
        factory.setAutomaticRecoveryEnabled(true);
        factory.setTopologyRecoveryEnabled(true);

        while (true) {
            for (String port : rabbitmqPorts) {
                try {
                    factory.setHost(rabbitmqHost.trim());
                    factory.setPort(Integer.parseInt(port.trim()));

                    connection = factory.newConnection();
                    channel = connection.createChannel();
                    channel.queueDeclare(QUEUE_NAME_TRANSACTIONS, false, false, false, null);
                    channel.queueDeclare(QUEUE_NAME_CITATIONS, false, false, false, null);

                    new RecServerApp(nodeId,channel,database,Integer.parseInt(dotenv.get("MIN_NODES")));
                    setupConnectionRecoveryListener();
                    logger.info("Connected to RabbitMQ at: " + port);
                    return;
                } catch (Exception e) {
                    logger.warning("Failed to connect to RabbitMQ port: " + port + " - " + e.getMessage());
                }
            }
            logger.warning("Retrying connection to RabbitMQ after " + RETRY_INTERVAL_MS / 1000 + " seconds...");
            Thread.sleep(RETRY_INTERVAL_MS);
        }
    }

    /**
     * Sets up a listener for RabbitMQ connection recovery.
     *
     * This method adds a shutdown listener to the RabbitMQ connection, which triggers when the
     * connection is lost. On connection loss, it attempts to reconnect by calling {@code connectToRabbitMQ()}
     * and resumes message consumption by invoking {@code startListening()}.
     *
     * Logs warnings if the connection is lost and errors if reconnection fails.
     */
    private void setupConnectionRecoveryListener() {
        connection.addShutdownListener(cause -> {
            logger.warning("RabbitMQ connection lost: " + cause.getMessage());
            try {
                connectToRabbitMQ();
                startListening();
            } catch (Exception e) {
                logger.severe("Failed to reconnect to RabbitMQ: " + e.getMessage());
            }
        });
    }

    /**
     * Starts listening to RabbitMQ queues and processes incoming messages.
     *
     * This method registers callback functions to handle messages from the transactions and citations queues.
     * The messages are processed asynchronously.
     *
     * @throws Exception If an error occurs while setting up the listeners or connecting to RabbitMQ.
     */
    public void startListening() throws Exception {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            processMessage(message, delivery.getProperties(), delivery.getEnvelope());
        };

        channel.basicConsume(QUEUE_NAME_TRANSACTIONS, true, deliverCallback, consumerTag -> {});
        channel.basicConsume(QUEUE_NAME_CITATIONS, true, deliverCallback, consumerTag -> {});
    }

    /**
     * Processes incoming RabbitMQ messages and delegates them to appropriate handlers.
     *
     * The message format determines which handler method is invoked.
     * For example:
     * "CHECK_PARK_STATUS:{zoneName},{parkingSpace},{vin}" invokes `handleCheckParkStatus`
     * "ISSUE_CITATION:{zoneName},{parkingSpace},{vin},{citationCost},{issueTime}" invokes `handleIssueCitation`
     *
     * @param message The message body.
     * @param properties The message properties, including reply queue and correlation ID.
     * @param envelope The message envelope containing delivery details.
     */
    public void processMessage(String message, AMQP.BasicProperties properties, Envelope envelope) {
        try {
            String response;
            if (message.startsWith("CHECK_PARK_STATUS:")) {
                response = handleCheckParkStatus(message);
            }else if (message.startsWith("ISSUE_CITATION:")) {
                response = handleIssueCitation(message);
            } else if (message.startsWith("CHECK_PARKING_AVAILABILITY:")) {
                response = handleCheckParkingAvailability(message);
            } else if (message.startsWith("START_PARKING:")) {
                response = handleStartParking(message);
            } else if (message.startsWith("STOP_PARKING:")) {
                response = handleStopParking(message);
            }  else if (message.startsWith("GET_PARKING_EVENTS:")){
                response = handleGetVehicleHistory(message);
            }else if (message.equals("GET_TRANSACTIONS")) {
                response = handleGetTransactions();
            } else if (message.equals("GET_CITATIONS")) {
                response = handleGetCitations();
            } else if(message.equals("GET_ALL_PARKING_ZONES:")){
                response = handleLoadAllParkingZones();
            }else if(message.startsWith("GET_ALL_PARKING_SPACES:")){
                String[] parts = message.split(":");
                response = handleLoadAllParkingSpaces(parts[1]);
            } else {
                response = "Unknown Command";
            }
            sendResponse(response, properties.getReplyTo(), properties.getCorrelationId());
        } catch (Exception e) {
            logger.severe("Error processing message: " + e.getMessage());
        }
    }

    public String handleGetTransactions() {
        return transactionService.getAllTransactions();
    }

    /**
     * Handles requests for fetching the vehicle's parking and citation history by VIN.
     * @param message The incoming message in the format: "GET_PARKING_EVENTS:{vin}".
     * @return A formatted string containing the vehicle's parking and citation history.
     *         Each record is separated by a newline. Returns an error message if the VIN is invalid
     *         or if an error occurs during processing.
     */
    public String handleGetVehicleHistory(String message) {
        String[] parts = message.split(":");
        if (parts.length < 2) {
            return "Error: Invalid message format. Expected 'GET_PARKING_EVENTS::{vin}'.";
        }

        String vin = parts[1];

        List<String> history = parkingService.getVehicleHistoryByVin(vin);

        if (history.isEmpty()) {
            return "No parking or citation history found for vehicle with VIN: " + vin;
        }

        return String.join("\n", history);
    }


    /**
     * Handles requests to retrieve all transactions.
     * @return A formatted string containing all transactions or an error message
     */
    public String handleGetCitations() {
        return citationService.getAllCitations();
    }

    /**
     * Handles requests to load all parking zones.
     * @return A comma-separated string of all parking zones
     * @throws SQLException If an error occurs while accessing the database
     */
    private String handleLoadAllParkingZones() throws SQLException {
        List<String> zones = parkingService.getAllAvailableParkingZones();
        return String.join(",", zones);
    }

    /**
     * Handles requests to load all parking spaces in a specific zone.
     * @param zoneName The name of the parking zone
     * @return A comma-separated string of all parking spaces in the zone
     * @throws SQLException If an error occurs while accessing the database
     */
    private String handleLoadAllParkingSpaces(String zoneName) throws SQLException {
        List<String> spaces = parkingService.getAllParkingSpacesByZone(zoneName);
        return String.join(",", spaces);
    }

    /**
     * Handles requests to check the parking status of a vehicle.
     * @param message The request message in the format: "CHECK_PARK_STATUS:zoneName,parkingSpace,vin".
     * @return "Parking Ok" if the vehicle is parked in the correct space and within allowed time.
     *         "Parking Not Ok" if the vehicle violates parking rules or if there is no active session.
     * @throws SQLException If an error occurs while accessing the database.
     */
    public String handleCheckParkStatus(String message) throws SQLException {
        String[] parts = message.split(":|,");
        String zoneName = parts[1];
        String parkingSpace = parts[2];
        String vin = parts[3];
        int postId = parkingService.getParkingSpaceIdByZoneAndPost(zoneName, parkingSpace);
        int vehicleId = transactionService.getVehicleIdByVin(vin);

        if (transactionService.hasActiveSessionByVehicleAndSpace(vehicleId,postId) && !transactionService.hasExceededMaxParkingMinutes(vin)) {
            return "Parking Ok";
        }
        return "Parking Not Ok";
    }

    /**
     * Handles requests to issue a citation for a vehicle.
     * @param message The request message in the format:
     *                "ISSUE_CITATION:zoneName,parkingSpace,vin,citationCost,issueTime".
     * @return A response message indicating the result of the citation issuance.
     * @throws SQLException If an error occurs while processing the citation request.
     */
    public String handleIssueCitation(String message) throws SQLException {
        String[] parts = message.split(":|,");
        String zoneName = parts[1];
        String parkingSpace = parts[2];
        String vin = parts[3];
        double citationCost = Double.parseDouble(parts[4]);
        int postId = parkingService.getParkingSpaceIdByZoneAndPost(zoneName, parkingSpace);
        int vehicleId = transactionService.getVehicleIdByVin(vin);
        int actualPostIdInt = 0;

        String reason = "Unspecified";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime issueTime;

        try {
            issueTime = LocalDateTime.parse(parts[5], formatter);
        } catch (Exception e) {
            logger.warning("Invalid issueTime format: " + parts[5] + ". Defaulting to current time.");
            issueTime = LocalDateTime.now();
        }

        if (transactionService.hasExceededMaxParkingMinutes(vin)) {
            reason = "Parking Violation: Exceeded max parking time.";
        } else if (!transactionService.hasActiveSessionByVehicleAndSpace(vehicleId, postId)) {
            reason = "Parking Violation: Improper parking.";
            String actualPostId= transactionService.getActiveSessionPostId(vin);
            actualPostIdInt = parkingService.getParkingSpaceIdByZoneAndPost(zoneName,actualPostId);
        }

        citationService.issueCitation(vehicleId, actualPostIdInt, citationCost, issueTime, reason);

        return reason;
    }

    /**
     * Handles a request to check the availability of a specific parking space.
     * @param message The message containing the parking zone and post ID details.
     * @return A string indicating the availability or validity of the parking space.
     * @throws RuntimeException If an SQL exception occurs during database access.
     */
    public String handleCheckParkingAvailability(String message) {
        String[] parts = message.split(":|,");
        String zoneName = parts[1];
        String postId = parts[2];

        if (!parkingService.isZoneNameValid(zoneName)) {
            return "INVALID: Parking zone " + zoneName + " is incorrect.";
        }

        if (!parkingService.isParkingSpaceValid(zoneName, postId)) {
            return "INVALID: Parking space " + postId + " in zone " + zoneName + " is incorrect.";
        }

        boolean isAvailable = parkingService.isParkingSpaceAvailable(zoneName, postId);
        return isAvailable ? "AVAILABLE" : "OCCUPIED";
    }

    /**
     * Handles the request to start a parking session for a vehicle.
     * @param message The request message in the format: "START_PARKING:vin,zoneName,postId,startTime".
     * @return A response string indicating the status of the operation:
     *         - If a previous session exists, it includes details of the stopped session and cost.
     *         - If successful, "Parking Started Successfully".
     *         - If validation fails, "INVALID" with the relevant reason.
     */
    public String handleStartParking(String message) {
        String[] parts = message.split(":|,");
        String vin = parts[1];
        String zoneName = parts[2];
        String postId = parts[3];

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime startTime = LocalDateTime.parse(parts[4] + ":" + parts[5], formatter);

        if (!transactionService.isVehicleVinValid(vin)) {
            return "INVALID: Vehicle number " + vin + " is incorrect.";
        }

        if (transactionService.hasActiveSession(vin)) {
            String activePostId = transactionService.getActiveSessionPostId(vin);
            LocalDateTime endTime = LocalDateTime.now();

            double amount = transactionService.stopParkingEvent(vin, activePostId, endTime);

            transactionService.startParkingEvent(vin, postId, startTime);
            return "Vehicle- " + vin + " Previously parked in:" + activePostId + "\ncost: $ " + amount + ".\n Now Parking in " + postId + ".";
        }

        transactionService.startParkingEvent(vin, postId, startTime);
        return "Parking Started Successfully";
    }


    /**
     * Handles the request to stop a parking session for a vehicle.
     * @param message The request message in the format: "STOP_PARKING:vin,zoneName,postId,endTime".
     * @return A response string indicating the success of the operation and the calculated parking cost.
     */
    public String handleStopParking(String message) {
        String[] parts = message.split(":|,");
        String vin = parts[1];
        String zoneName = parts[2];
        String postId = parts[3];

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime endTime = LocalDateTime.parse(parts[4] + ":" + parts[5], formatter);

        double amount = transactionService.stopParkingEvent(vin, postId, endTime);
        if (amount == -1) {
            return "INVALID:Check time and zone";
        }
        return "Parking Stopped Successfully cost: $ " + amount + ".";
    }


    /**
     * Sends a response message to the RabbitMQ reply queue.
     * @param response The response message to be sent.
     * @param replyQueueName The name of the RabbitMQ queue to send the response to.
     * @param correlationId The correlation ID to match the response with the original request.
     */
    private void sendResponse(String response, String replyQueueName, String correlationId) {
        logger.severe(response);
        try {
            AMQP.BasicProperties replyProps = new AMQP.BasicProperties
                    .Builder()
                    .correlationId(correlationId)
                    .build();
            channel.basicPublish("", replyQueueName, replyProps, response.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.severe("Failed to send response: " + e.getMessage());
        }
    }

    /**
     * Closes the RabbitMQ connection and channel.
     *
     * This method ensures a clean shutdown of the RabbitMQ resources to avoid resource leaks.
     *
     * @throws Exception If an error occurs during the close operation.
     */
    public void close() throws Exception {
        channel.close();
        connection.close();
    }
}
