package com.mulligan.server;

import com.mongodb.client.MongoDatabase;
import com.mulligan.database.MongoDBConnector;
import com.mulligan.server.services.CitationService;
import com.mulligan.server.services.ParkingService;
import com.mulligan.server.services.TransactionService;
import com.rabbitmq.client.ConnectionFactory;
import java.util.logging.Logger;


/**
 * Main application class for the server.
 *
 * The {@code ServerApp} is responsible for managing the **server lifecycle**, including:
 *
 * **Database Management** - Establishes a connection to MongoDB and initializes services.
 * **Message Processing** - Listens for and processes messages from RabbitMQ.
 * **Transaction Handling** - Manages parking transactions, citations, and parking space availability.
 * **Graceful Shutdown** - Ensures proper resource cleanup when the server stops.
 *
 * **Key Responsibilities:**
 * - Initializes **MongoDB connections** and ensures the database is accessible.
 * - Starts and manages the **RabbitMQConsumer** for processing incoming messages.
 * - Provides service layers for:
 *   - {@link TransactionService} (Handles parking transactions)
 *   - {@link ParkingService} (Manages parking-related operations)
 *   - {@link CitationService} (Processes parking citations)
 * - Manages **server start and stop** operations via {@link #startServer()} and {@link #stopServer()}.
 * - Registers a **shutdown hook** to ensure proper cleanup before application termination.
 *
 * **Lifecycle:**
 * - The server is initialized when the **main method** executes.
 * - It continuously listens for RabbitMQ messages.
 * - When terminated, all **database connections and messaging consumers** are closed properly.
 *
 * **Usage Example:**
 * ```sh
 * java -jar ServerApp.jar
 * ```
 *
 * @author Oran Alster
 * @author Jamal Majadle
 *
 * @version 1.9.0
 * @see TransactionService
 * @see ParkingService
 * @see CitationService
 * @see RabbitMQConsumer
 */

public class ServerApp {
    private RabbitMQConsumer consumer;
    private TransactionService transactionService;
    private ParkingService parkingService;
    private CitationService citationService;
    private final Logger logger = Logger.getLogger(ServerApp.class.getName());
    private final MongoDBConnector mongoConnector;
    private final MongoDatabase database;
    private final ConnectionFactory connectionFactory;

    /**
     * Default constructor for the {@link ServerApp}.
     *
     * This constructor initializes the server application with default configurations:
     * - Retrieves a singleton instance of {@link MongoDBConnector} to establish a database connection.
     * - Fetches a reference to the MongoDB database from the connector.
     * - Initializes a new instance of {@link ConnectionFactory} for managing RabbitMQ connections.
     *
     * This constructor is typically used in a **production environment** where the application
     * relies on environment variables or configuration files to determine database and message queue settings.
     *
     * **Key Responsibilities:**
     * - Establishes a connection to MongoDB via the {@link MongoDBConnector}.
     * - Initializes a {@link ConnectionFactory} for RabbitMQ communication.
     * - Ensures that the application has all necessary dependencies to function properly.
     *
     * @see MongoDBConnector#getInstance()
     * @see ConnectionFactory
     */
    public ServerApp() {
        this.mongoConnector = MongoDBConnector.getInstance();
        this.database = mongoConnector.getDatabase();
        this.connectionFactory = new ConnectionFactory();
    }

    /**
     * Constructor for {@link ServerApp} used for testing and dependency injection.
     *
     * This constructor allows manual injection of a **mocked** or **custom-configured** MongoDB database
     * and RabbitMQ connection factory. It is primarily used in **unit tests** and controlled environments
     * where a real database or message broker is not required.
     *
     * **Key Responsibilities:**
     * - Bypasses the use of {@link MongoDBConnector} to allow dependency injection.
     * - Directly assigns a provided {@link MongoDatabase} instance.
     * - Uses a pre-configured {@link ConnectionFactory} for RabbitMQ communication.
     *
     * **Typical Use Cases:**
     * - Unit testing with **mocked** MongoDB and RabbitMQ instances.
     * - Running the application in an **isolated environment** with pre-defined configurations.
     *
     * **Important Notes:**
     * - The `mongoConnector` field is set to `null` because the database is injected manually.
     * - This approach **improves testability** and **removes environment dependencies** in test cases.
     *
     * @param database The MongoDB database instance to be used.
     * @param connectionFactory The RabbitMQ connection factory for managing message broker communication.
     *
     * @see MongoDatabase
     * @see ConnectionFactory
     */
    public ServerApp(MongoDatabase database, ConnectionFactory connectionFactory) {
        this.mongoConnector = null;
        this.database = database;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Initializes and starts the server by setting up the required components.
     *
     * This method performs the following key operations:
     * - Retrieves a MongoDB database instance from {@link MongoDBConnector}.
     * - Initializes core service layers:
     *   - {@link TransactionService} for managing parking transactions.
     *   - {@link ParkingService} for validating parking zones and spaces.
     *   - {@link CitationService} for handling parking citations.
     * - Creates an instance of {@link RabbitMQConsumer} for processing messages from RabbitMQ.
     * - Starts the RabbitMQ consumer to listen for incoming messages.
     *
     * **Expected Behavior:**
     * - Logs successful initialization of MongoDB and RabbitMQ components.
     * - Begins listening for messages and processing parking-related requests.
     *
     * **Exception Handling:**
     * - If an exception occurs during initialization, an error is logged,
     *   and the stack trace is printed for debugging purposes.
     *
     * **Important Notes:**
     * - This method is typically invoked when the server application starts.
     * - Ensure that MongoDB and RabbitMQ services are **running and accessible** before calling this method.
     *
     * @see MongoDBConnector
     * @see TransactionService
     * @see ParkingService
     * @see CitationService
     * @see RabbitMQConsumer
     */
    public void startServer() {
        try {
            MongoDatabase database = mongoConnector.getDatabase();
            logger.info("MongoDBConnector instance created.");
            transactionService = new TransactionService(database);
            parkingService = new ParkingService(database);
            citationService = new CitationService(database);

            consumer = new RabbitMQConsumer(transactionService, parkingService, citationService,database);
            consumer.startListening();
            logger.info("RabbitMQConsumer initialized and listening.");

        } catch (Exception e) {
            logger.severe("Failed to initialize server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gracefully shuts down the server by closing all active connections.
     *
     * This method performs the following key operations:
     * - Closes the {@link RabbitMQConsumer} to stop listening for messages.
     * - Closes the MongoDB connection through {@link MongoDBConnector}.
     * - Logs a confirmation message once the shutdown process is complete.
     *
     * **Expected Behavior:**
     * - The server should stop **without errors** and release all allocated resources.
     * - The RabbitMQ consumer should **terminate gracefully**, ensuring that no messages are processed after shutdown.
     * - The MongoDB connection should be **properly closed** to prevent memory leaks.
     *
     * **Exception Handling:**
     * - If an error occurs while stopping the server, it is logged, and the stack trace is printed for debugging.
     *
     * **Important Notes:**
     * - This method should be **called before application termination** to avoid resource leaks.
     * - Ensure that no **ongoing transactions** or **critical operations** are running before shutting down.
     *
     * @see MongoDBConnector
     * @see RabbitMQConsumer
     */
    public void stopServer() {
        try {
            if (consumer != null) {
                consumer.close();
            }
            mongoConnector.close();
            logger.info("Server stopped gracefully.");
        } catch (Exception e) {
            logger.severe("Error during server shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Main entry point for the server application.
     *
     * This method initializes the server by performing the following steps:
     * - Creates an instance of {@link ServerApp}.
     * - Starts the server, which initializes the MongoDB connection and RabbitMQ consumer.
     * - Registers a shutdown hook to ensure graceful termination when the application exits.
     *
     * **Key Functionalities:**
     * - **Server Initialization:** Calls {@link ServerApp#startServer()} to set up database connections and message listeners.
     * - **Graceful Shutdown:** Uses {@code Runtime.getRuntime().addShutdownHook()} to automatically call {@link ServerApp#stopServer()} when the application stops.
     * - **Command-line Arguments:** Currently not used, but can be extended for future configurations.
     *
     * **Expected Behavior:**
     * - The server **starts correctly**, establishes all required connections, and begins processing messages.
     * - When terminated (via CTRL+C, SIGTERM, or application exit), the shutdown hook **ensures all resources are released** properly.
     *
     * **Usage:**
     * ```sh
     * java -jar ServerApp.jar
     * ```
     *
     * **Notes:**
     * - The shutdown hook ensures that the MongoDB connection and RabbitMQ consumer are closed **to prevent memory leaks**.
     * - Future enhancements may include support for **configuration parameters** via command-line arguments.
     *
     * @param args Command-line arguments (currently unused).
     * @see ServerApp#startServer()
     * @see ServerApp#stopServer()
     */
    public static void main(String[] args) {
        ServerApp serverApp = new ServerApp();
        serverApp.startServer();
        Runtime.getRuntime().addShutdownHook(new Thread(serverApp::stopServer));
    }
}
