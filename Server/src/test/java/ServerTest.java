
import org.bson.conversions.Bson;
import org.junit.jupiter.api.*;
import org.mockito.*;
import com.mongodb.client.*;
import com.mulligan.server.*;
import com.mulligan.server.services.*;
import com.rabbitmq.client.*;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Test suite for the server-side components of the application.
 *
 * This test class contains unit tests for the core server services and the RabbitMQ consumer.
 * The tests validate the behavior of the following components:
 *
 *
 * {@link TransactionService} - Handles parking transaction lifecycle (start and stop events).
 * {@link ParkingService} - Manages parking zone and space validations.
 * {@link CitationService} - Processes citations for parking violations.
 * {@link RabbitMQConsumer} - Processes messages from RabbitMQ for various parking-related operations.
 * {@link ServerApp} - Handles the initialization and management of the server components.
 *
 * Key Updates Based on Recent Changes:
 * Removed reliance on environment variables by injecting dependencies directly.
 * Updated tests to use mocked {@link MongoDatabase} and {@link ConnectionFactory} instead of reading from a `.env` file.
 * Ensured proper initialization of database and RabbitMQ connection in tests.
 * Improved test reliability by eliminating external dependencies (MongoDB & RabbitMQ).
 *
 * Testing Frameworks & Tools:
 * JUnit 5 - For lifecycle management (@Test, @BeforeEach, @AfterEach).
 * Mockito - For mocking external dependencies such as MongoDB collections and RabbitMQ channels.
 *
 * Test Lifecycle:
 * The {@code setUp} method initializes mocked objects and prepares the testing environment.
 * The {@code tearDown} method ensures that mocks are reset after each test execution.
 *
 *
 * @see ServerApp
 * @see TransactionService
 * @see ParkingService
 * @see CitationService
 * @see RabbitMQConsumer
 * @author Oran Alster
 * @version 2.0.0
 */
public class ServerTest {

    @Mock
    private MongoDatabase mockDatabase;
    @Mock
    private MongoCollection<Document> mockCollection;
    @Mock
    private Channel mockChannel;


    private TransactionService transactionService;
    private ParkingService parkingService;
    private CitationService citationService;
    private RabbitMQConsumer consumer;

    /**
     * Initializes the test environment before each test case execution.
     *
     * This method performs the following setup operations:
     * Initializes Mockito annotations to enable mocking.
     * Mocks MongoDB interactions by simulating database collections, queries, and results.
     * Creates mock instances of {@link FindIterable} and {@link MongoCursor} to return predefined test data.
     * Initializes service layers:
     * {@link TransactionService} for handling transactions.
     * {@link ParkingService} for managing parking-related data.
     * {@link CitationService} for managing parking citations.
     * Mocks a RabbitMQ {@link Channel} to simulate message passing.
     * Creates an instance of {@link RabbitMQConsumer} with mocked dependencies.
     *
     * This setup ensures that all dependencies are properly mocked,
     * preventing external database and RabbitMQ connections during testing.
     *
     * @throws Exception if an unexpected error occurs during setup.
     */
    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        FindIterable<Document> mockFindIterable = mock(FindIterable.class);
        MongoCursor<Document> mockCursor = mock(MongoCursor.class);
        when(mockCollection.find(any(Bson.class))).thenReturn(mockFindIterable);
        when(mockFindIterable.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(true, false);
        when(mockCursor.next()).thenReturn(new Document("id", 1));
        when(mockDatabase.getCollection(anyString())).thenReturn(mockCollection);
        when(mockCollection.countDocuments(any(Bson.class))).thenReturn(1L);

        transactionService = new TransactionService(mockDatabase);
        parkingService = new ParkingService(mockDatabase);
        citationService = new CitationService(mockDatabase);

        Channel mockChannel = mock(Channel.class);

        consumer = new RabbitMQConsumer(
                transactionService,
                parkingService,
                citationService,
                mockDatabase,
                mockChannel
        );
    }

    /**
     * Tests whether the {@link ServerApp} starts correctly without throwing exceptions.
     *
     * This test ensures that the server initializes all required components properly,
     * including MongoDB and RabbitMQ dependencies, and starts without issues.
     *
     * Test Steps:
     * Mocks a MongoDB database and collection.
     * Mocks a RabbitMQ {@link ConnectionFactory}, {@link Connection}, and {@link Channel}.
     * Configures RabbitMQ to return mock instances when a new connection is requested.
     * Creates an instance of {@link ServerApp} with the mocked dependencies.
     * Asserts that {@link ServerApp#startServer()} does not throw any exceptions.
     *
     * Expected Behavior:
     * The server should start without errors.
     * MongoDB and RabbitMQ should be properly initialized.
     * No exceptions should be thrown during startup.
     *
     * @see ServerApp
     * @see MongoDatabase
     * @see ConnectionFactory
     * @see Connection
     * @see Channel
     */
    @Test
    public void testStartServer() {
        MongoDatabase mockDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> mockCollection = mock(MongoCollection.class);
        when(mockDatabase.getCollection(anyString())).thenReturn(mockCollection);

        ConnectionFactory mockConnectionFactory = mock(ConnectionFactory.class);
        Connection mockConnection = mock(Connection.class);
        Channel mockChannel = mock(Channel.class);

        try {
            when(mockConnectionFactory.newConnection()).thenReturn(mockConnection);
            when(mockConnection.createChannel()).thenReturn(mockChannel);
        } catch (Exception e) {
            fail("Mocking RabbitMQ connection failed: " + e.getMessage());
        }

        ServerApp serverApp = new ServerApp(mockDatabase, mockConnectionFactory);

        assertDoesNotThrow(serverApp::startServer, "Server failed to start");
    }

    /**
     * Tests whether the {@link ServerApp} stops correctly without throwing exceptions.
     *
     * This test ensures that the server shuts down gracefully by releasing
     * all allocated resources, including MongoDB connections and RabbitMQ channels.
     *
     * Test Steps:
     * Mocks a MongoDB database and collection.
     * Mocks a RabbitMQ {@link ConnectionFactory}, {@link Connection}, and {@link Channel}.
     * Configures RabbitMQ to return mock instances when a new connection is requested.
     * Creates an instance of {@link ServerApp} with the mocked dependencies.
     * Asserts that {@link ServerApp#stopServer()} does not throw any exceptions.
     *
     * Expected Behavior:
     * The server should stop without errors.
     * MongoDB and RabbitMQ connections should be properly closed.
     * No exceptions should be thrown during shutdown.
     *
     * @see ServerApp
     * @see MongoDatabase
     * @see ConnectionFactory
     * @see Connection
     * @see Channel
     */
    @Test
    public void testStopServer() {
        MongoDatabase mockDatabase = mock(MongoDatabase.class);
        MongoCollection<Document> mockCollection = mock(MongoCollection.class);
        when(mockDatabase.getCollection(anyString())).thenReturn(mockCollection);

        ConnectionFactory mockConnectionFactory = mock(ConnectionFactory.class);
        Connection mockConnection = mock(Connection.class);
        Channel mockChannel = mock(Channel.class);

        try {
            when(mockConnectionFactory.newConnection()).thenReturn(mockConnection);
            when(mockConnection.createChannel()).thenReturn(mockChannel);
        } catch (Exception e) {
            fail("Mocking RabbitMQ connection failed: " + e.getMessage());
        }

        ServerApp serverApp = new ServerApp(mockDatabase, mockConnectionFactory);

        assertDoesNotThrow(serverApp::stopServer, "Server failed to stop");
    }


    /**
     * Tests that processing an unsupported message type does not throw any exceptions.
     * 
     * This test verifies the behavior of the {@code processMessage} method in the {@code RabbitMQConsumer} class
     * when an unsupported message type is received. It ensures that the method handles the scenario gracefully
     * without throwing any runtime exceptions.
     * 
     *
     * 
     * The test uses {@code assertDoesNotThrow} to confirm that the {@code processMessage} method can process
     * an unsupported message type without errors. A mock {@code BasicProperties} object is used to simulate
     * the message properties.
     *
     */
    @Test
    public void testUnsupportedMessageType() {
        String message = "UNSUPPORTED_MESSAGE_TYPE:SomeData";
        AMQP.BasicProperties mockProps = new AMQP.BasicProperties.Builder()
                .replyTo("replyQueue")
                .correlationId("1234")
                .build();

        assertDoesNotThrow(() -> consumer.processMessage(message, mockProps, null), "Unsupported message type should not throw an exception");
    }

    /**
     * Tests that a valid "CHECK_PARK_STATUS" message is processed without throwing any exceptions.
     * 
     * This test verifies the behavior of the {@code processMessage} method in the {@code RabbitMQConsumer} class
     * when a valid "CHECK_PARK_STATUS" message is received. It ensures that the method successfully processes the
     * message and interacts with the {@code ParkingService} without raising any runtime exceptions.
     * 
     *
     * 
     * The test uses {@code when(...).thenReturn(...)} to mock the {@code isParkingSpaceAvailable} method
     * in the {@code ParkingService}, simulating a scenario where the parking space is available. It uses
     * {@code assertDoesNotThrow} to confirm that the {@code processMessage} method completes without errors.
     * 
     */
    @Test
    public void testValidCheckParkStatusMessage() {
        String message = "CHECK_PARK_STATUS:Zone1,P1,VIN1234";
        AMQP.BasicProperties mockProps = new AMQP.BasicProperties.Builder()
                .replyTo("replyQueue")
                .correlationId("1234")
                .build();

        when(parkingService.isParkingSpaceAvailable("Zone1", "P1")).thenReturn(true);

        assertDoesNotThrow(() -> consumer.processMessage(message, mockProps, null), "Valid message processing failed");
    }

    /**
     * Tests that an invalid vehicle VIN is correctly identified as invalid.
     * 
     * This test verifies the behavior of the {@code isVehicleVinValid} method in the {@code TransactionService} class.
     * It ensures that the method returns {@code false} when an invalid VIN is provided, indicating that the vehicle
     * is not recognized in the database.
     *
     *
     * 
     * The test uses {@code when(...).thenReturn(...)} to mock the {@code countDocuments} method of the database collection,
     * simulating a scenario where no documents match the provided VIN. It then asserts that {@code isVehicleVinValid}
     * correctly returns {@code false}.
     * 
     */
    @Test
    public void testInvalidVehicleVin() {
        when(mockCollection.countDocuments(eq("vin", "INVALIDVIN"))).thenReturn(0L);
        assertFalse(transactionService.isVehicleVinValid("INVALIDVIN"), "Invalid VIN should return false");
    }

    /**
     * Tests that the hasActiveSession method correctly identifies when there is no active session for a vehicle.
     *
     * This test verifies the behavior of the hasActiveSession method in the TransactionService class.
     * It ensures that the method returns false when no active session exists for the given vehicle VIN.
     * 
     *
     * 
     * The test uses mocked database interactions to simulate the absence of an active session. Specifically:
     * - A mocked transactions collection is returned by the database.
     * - The find method on the collection is mocked to return a FindIterable.
     * - The first method on the FindIterable is mocked to return null, simulating no active session.
     * It then asserts that hasActiveSession returns false.
     */

    @Test
    public void testNoActiveSession() {
        FindIterable<Document> mockFindIterable = mock(FindIterable.class);
        MongoCollection<Document> mockTransactionsCollection = mock(MongoCollection.class);

        when(mockDatabase.getCollection("transactions")).thenReturn(mockTransactionsCollection);

        when(mockTransactionsCollection.find(any(Bson.class))).thenReturn(mockFindIterable);
        when(mockFindIterable.first()).thenReturn(null);

        assertFalse(transactionService.hasActiveSession("VIN1234"), "No active session should return false");
    }

    /**
     * Tests that a citation is issued without throwing any exceptions.
     *
     * This test verifies the behavior of the {@code issueCitation} method in the {@code CitationService} class.
     * It ensures that the method can successfully issue a citation without raising any runtime exceptions
     * when valid inputs are provided.
     *
     *
     * The test uses {@code when(...).thenReturn(...)} to mock the behavior of the database collection's {@code find} method,
     * simulating the presence of required data. It then uses {@code assertDoesNotThrow} to confirm that the
     * {@code issueCitation} method completes without errors.
     *
     *
     *
     * @see CitationService#issueCitation(int, int, double, LocalDateTime, String)
     */
    @Test
    public void testIssueCitation() {
        when(mockCollection.find(any(Bson.class))).thenReturn(mock(FindIterable.class));

        assertDoesNotThrow(() ->
                        citationService.issueCitation(1, 2, 100.0, LocalDateTime.now(), "Violation"),
                "Failed to issue citation"
        );
    }

    /**
     * Tests the full lifecycle of a parking transaction, including starting and stopping a parking event.
     * 
     * This test verifies the behavior of the startParkingEvent and stopParkingEvent methods
     * in the TransactionService class. It ensures that both methods execute without throwing any
     * exceptions when valid inputs are provided.
     * 
     *
     * The test simulates a complete parking transaction:
     * 
     *   - Starts a parking event using a valid vehicle VIN and parking post ID.
     *   - Stops the parking event after a specified duration.
     * 
     * It uses assertDoesNotThrow to confirm that both operations complete successfully without errors.
     * 
     *
     * @see TransactionService#startParkingEvent(String, String, LocalDateTime)
     * @see TransactionService#stopParkingEvent(String, String, LocalDateTime)
     */
    @Test
    public void testTransactionLifecycle() {
        String vin = "VIN1234";
        String postId = "P1";

        assertDoesNotThrow(() ->
                        transactionService.startParkingEvent(vin, postId, LocalDateTime.now()),
                "Failed to start parking"
        );

        assertDoesNotThrow(() ->
                        transactionService.stopParkingEvent(vin, postId, LocalDateTime.now().plusHours(2)),
                "Failed to stop parking"
        );
    }

    /**
     * Tests that the RabbitMQ consumer processes a valid message without throwing any exceptions.
     * 
     * This test verifies the behavior of the processMessage method in the RabbitMQConsumer class.
     * It ensures that the consumer can handle a valid "CHECK_PARK_STATUS" message correctly without raising
     * any runtime exceptions.
     * 
     *
     * The test simulates the following scenario:
     * 
     *   A valid RabbitMQ message of type "CHECK_PARK_STATUS" is provided, containing details about a zone, post ID, and VIN.
     *   A mock BasicProperties object is used to simulate message metadata, including a reply queue and correlation ID.
     * 
     * It uses assertDoesNotThrow to confirm that the processMessage method processes the message successfully.
     * 
     *
     */
    @Test
    public void testRabbitMQConsumerProcessing() {
        String message = "CHECK_PARK_STATUS:Zone1,P1,VIN1234";
        AMQP.BasicProperties mockProps = new AMQP.BasicProperties.Builder()
                .replyTo("replyQueue")
                .correlationId("1234")
                .build();

        assertDoesNotThrow(() ->
                        consumer.processMessage(message, mockProps, null),
                "Message processing failed"
        );
    }

    /**
     * Tests that a valid parking zone name is correctly identified as valid.
     * 
     * This test verifies the behavior of the isZoneNameValid method in the ParkingService class.
     * It ensures that the method returns true when a valid zone name exists in the database.
     * 
     *
     * The test simulates the following:
     * 
     *   Mocks the countDocuments method of the database collection to simulate the presence of a parking zone.
     *   Uses the when(...).thenReturn(...) pattern to set up the mock behavior.
     * 
     * The test then asserts that isZoneNameValid returns true for a valid zone name.
     * 
     *
     * @see ParkingService#isZoneNameValid(String)
     */
    @Test
    public void testParkingZoneValidation() {
        when(mockCollection.countDocuments(any(Bson.class))).thenReturn(1L);
        assertTrue(parkingService.isZoneNameValid("Zone1"), "Zone validation failed");
    }

    /**
     * Tests that an invalid RabbitMQ message is processed without throwing any exceptions.
     * 
     * This test verifies the behavior of the processMessage method in the RabbitMQConsumer class
     * when an invalid or unsupported message type is received. It ensures that the method handles the scenario
     * gracefully without raising any runtime exceptions.
     * 
     *
     * The test simulates the following scenario:
     * 
     *   An invalid RabbitMQ message is provided with the format "INVALID_MESSAGE".
     *   A mock BasicProperties object is used to simulate message metadata, including a reply queue and correlation ID.
     * 
     * It uses assertDoesNotThrow to confirm that the processMessage method completes without errors
     * when processing the invalid message.
     * 
     *
     */
    @Test
    public void testInvalidRabbitMQMessage() {
        String invalidMessage = "INVALID_MESSAGE";
        AMQP.BasicProperties mockProps = new AMQP.BasicProperties.Builder()
                .replyTo("replyQueue")
                .correlationId("1234")
                .build();
        assertDoesNotThrow(() ->
                        consumer.processMessage(invalidMessage, mockProps, null),
                "Invalid message processing failed"
        );
    }

    /**
     * Tests that retrieving all citations returns an empty string when there are no citations in the database.
     * 
     * This test verifies the behavior of the getAllCitations method in the CitationService class.
     * It ensures that the method returns an empty string when the database contains no citation documents.
     * 
     *
     * The test simulates the following scenario:
     * 
     *   Mocks the find method of the database collection to return an empty FindIterable.
     *   Asserts that the getAllCitations method returns an empty string, indicating no data is present.
     * 
     *
     * @see CitationService#getAllCitations()
     */
    @Test
    public void testGetAllCitationsEmpty() {
        when(mockCollection.find()).thenReturn(mock(FindIterable.class));
        assertEquals("", citationService.getAllCitations(), "Empty citation retrieval failed");
    }

    /**
     * Tests that a parking space is correctly identified as unavailable when the parking zone is invalid.
     * 
     * This test verifies the behavior of the isParkingSpaceAvailable method in the ParkingService class.
     * It ensures that the method returns false when the provided zone name does not exist in the database.
     * 
     * 
     * The test simulates the following scenario:
     * 
     *   Mocks the parking_zones collection to simulate the absence of the specified parking zone.
     *   Uses when(...).thenReturn(...) to return a FindIterable where first() returns null, indicating the zone is not found.
     *   Asserts that isParkingSpaceAvailable returns false for the invalid zone.
     * 
     * 
     * @see ParkingService#isParkingSpaceAvailable(String, String)
     */
    @Test
    public void testInvalidParkingZone() {
        MongoCollection<Document> mockZonesCollection = mock(MongoCollection.class);
        when(mockDatabase.getCollection("parking_zones")).thenReturn(mockZonesCollection);

        FindIterable<Document> mockZoneIterable = mock(FindIterable.class);
        when(mockZonesCollection.find(eq("zone_name", "InvalidZone"))).thenReturn(mockZoneIterable);
        when(mockZoneIterable.first()).thenReturn(null); // Zone not found

        ParkingService parkingService = new ParkingService(mockDatabase);

        assertFalse(parkingService.isParkingSpaceAvailable("InvalidZone", "P1"),
                "Parking space should not be available for an invalid zone");
    }

    /**
     * Tests that a parking space is correctly identified as unavailable when the parking space is invalid.
     * 
     * This test verifies the behavior of the isParkingSpaceAvailable method in the ParkingService class.
     * It ensures that the method returns false when the provided parking space does not exist in the database,
     * even if the zone is valid.
     * 
     *
     * The test simulates the following scenario:
     * 
     *   Mocks the parking_zones and parking_spaces collections to simulate a valid zone and an invalid space.
     *   Mocks the find method on the parking_zones collection to return a valid zone document.
     *   Mocks the find method on the parking_spaces collection to return null for the specified post ID, indicating the space is invalid.
     *   Asserts that isParkingSpaceAvailable returns false for the invalid parking space.
     * 
     *
     * @see ParkingService#isParkingSpaceAvailable(String, String)
     */
    @Test
    public void testInvalidParkingSpace() {
        MongoCollection<Document> mockZonesCollection = mock(MongoCollection.class);
        MongoCollection<Document> mockSpacesCollection = mock(MongoCollection.class);
        when(mockDatabase.getCollection("parking_zones")).thenReturn(mockZonesCollection);
        when(mockDatabase.getCollection("parking_spaces")).thenReturn(mockSpacesCollection);

        Document mockZone = new Document("id", 1).append("zone_name", "Zone1");
        FindIterable<Document> mockZoneIterable = mock(FindIterable.class);
        when(mockZonesCollection.find(eq("zone_name", "Zone1"))).thenReturn(mockZoneIterable);
        when(mockZoneIterable.first()).thenReturn(mockZone);

        FindIterable<Document> mockSpaceIterable = mock(FindIterable.class);
        when(mockSpacesCollection.find(and(eq("zone_id", 1), eq("post_id", "InvalidP1")))).thenReturn(mockSpaceIterable);
        when(mockSpaceIterable.first()).thenReturn(null); // Space not found

        ParkingService parkingService = new ParkingService(mockDatabase);

        assertFalse(parkingService.isParkingSpaceAvailable("Zone1", "InvalidP1"),
                "Parking space should not be available for an invalid space");
    }

    /**
     * Tests that a parking space is correctly identified as unavailable when it is occupied.
     *
     * This test verifies the behavior of the isParkingSpaceAvailable method in the ParkingService class.
     * It ensures that the method returns false when the specified parking space is already occupied by an active transaction.
     *
     *
     * The test simulates the following scenario:
     * 
     *   Mocks the parking_zones, parking_spaces, and transactions collections to simulate a valid zone, a valid space, and an active transaction.
     *   Mocks the find method on the parking_zones collection to return a valid zone document.
     *   Mocks the find method on the parking_spaces collection to return a valid parking space document.
     *   Mocks the countDocuments method on the transactions collection to return 1L, simulating an active transaction for the parking space.
     *   Asserts that isParkingSpaceAvailable returns false for the occupied parking space.
     *
     *
     * @see ParkingService#isParkingSpaceAvailable(String, String)
     */
    @Test
    public void testParkingSpaceOccupied() {
        MongoCollection<Document> mockZonesCollection = mock(MongoCollection.class);
        MongoCollection<Document> mockSpacesCollection = mock(MongoCollection.class);
        MongoCollection<Document> mockTransactionsCollection = mock(MongoCollection.class);

        when(mockDatabase.getCollection("parking_zones")).thenReturn(mockZonesCollection);
        when(mockDatabase.getCollection("parking_spaces")).thenReturn(mockSpacesCollection);
        when(mockDatabase.getCollection("transactions")).thenReturn(mockTransactionsCollection);

        Document mockZone = new Document("id", 1).append("zone_name", "Zone1");
        FindIterable<Document> mockZoneIterable = mock(FindIterable.class);
        when(mockZonesCollection.find(eq("zone_name", "Zone1"))).thenReturn(mockZoneIterable);
        when(mockZoneIterable.first()).thenReturn(mockZone);

        Document mockSpace = new Document("id", 2).append("post_id", "P1").append("zone_id", 1);
        FindIterable<Document> mockSpaceIterable = mock(FindIterable.class);
        when(mockSpacesCollection.find(and(eq("zone_id", 1), eq("post_id", "P1")))).thenReturn(mockSpaceIterable);
        when(mockSpaceIterable.first()).thenReturn(mockSpace);

        when(mockTransactionsCollection.countDocuments(and(eq("parking_space_id", 2), exists("end", false)))).thenReturn(1L);

        ParkingService parkingService = new ParkingService(mockDatabase);

        assertFalse(parkingService.isParkingSpaceAvailable("Zone1", "P1"),
                "Parking space should not be available if it is occupied");
    }

}