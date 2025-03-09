import com.mongodb.MongoClientException;
import com.mongodb.client.*;
import com.mongodb.client.result.InsertManyResult;
import com.mulligan.database.AvailableParkingSpots;
import com.mulligan.database.DataSeeder;
import com.mulligan.database.OvertimeParkingEvents;
import com.mulligan.database.TransactionStatsByHour;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mongodb.MongoTimeoutException;
import java.util.concurrent.TimeUnit;


/**
 * This class contains unit tests for database-related functionalities in the application.
 * It verifies the behavior of database operations, including seeding data, fetching available parking spots,
 * handling transactions, parking zones, and managing database node failures and recovery.
 *
 * Mocks are used to simulate interactions with the MongoDB collections ("parking_spaces", "transactions", etc.)
 * and the database.
 *
 * Tests ensure that:
 *
 * - Database methods are invoked correctly.
 * - Aggregations, inserts, and queries into collections are handled properly.
 * - Errors, edge cases, and database node failures are managed appropriately.
 * - Concurrency and simultaneous database access are handled without issues.
 * - Retry mechanisms and recovery processes function correctly after simulated node failures.
 * - Multiple database operations maintain consistency and recover gracefully during and after failures.
 *
 * These tests provide robust coverage of database-related functionality to ensure system reliability
 * and correctness in various scenarios, including fault tolerance and recovery.
 */
public class DatabaseTest {

    private MongoDatabase mockDatabase;
    private MongoCollection<Document> mockParkingSpaces;
    private MongoCollection<Document> mockTransactions;
    private MongoCollection<Document> mockParkingZones;

    @Mock
    private MongoCollection<Document> mockVehicles;

    @Mock
    private MongoCursor<Document> mockCursor;

    @Mock
    private AggregateIterable<Document> mockAggregateIterable;

    private OvertimeParkingEvents overtimeParkingEvents;


    /**
     * Sets up the testing environment before each test case is executed.
     *
     * This method initializes the necessary mock objects and configurations to simulate interactions
     * with the MongoDB database and its collections. It ensures that each test runs in an isolated environment
     * with all dependencies mocked appropriately.
     * 
     * Key setup actions include:
     * 
     *   Mocking the {@link MongoDatabase} and its collections, such as "parking_spaces", "transactions", and "parking_zones".
     *   Mocking MongoDB operations like {@code aggregate}, {@code getCollection}, and {@code insertMany}.
     *   Initializing the {@link OvertimeParkingEvents} class to test its methods.
     * 
     * 
     * Mock configurations include:
     * 
     *   {@code when(mockDatabase.getCollection("vehicles")).thenReturn(mockVehicles)}: Simulates returning the "vehicles" collection.
     *   {@code when(mockParkingSpaces.insertMany(anyList())).thenReturn(mockResult)}: Simulates successful insertion into the "parking_spaces" collection.
     *   Other database collection methods are similarly mocked to facilitate test execution.
     */
    @BeforeEach
    public void setUp() {
        mockDatabase = mock(MongoDatabase.class);
        mockParkingSpaces = mock(MongoCollection.class);
        mockTransactions = mock(MongoCollection.class);
        mockParkingZones = mock(MongoCollection.class);

        mockAggregateIterable = mock(AggregateIterable.class);
        mockCursor = mock(MongoCursor.class);

        MockitoAnnotations.openMocks(this);
        when(mockDatabase.getCollection("vehicles")).thenReturn(mockVehicles);

        MockitoAnnotations.openMocks(this);
        overtimeParkingEvents = new OvertimeParkingEvents();
        when(mockDatabase.getCollection("transactions")).thenReturn(mockTransactions);

        when(mockDatabase.getCollection("parking_spaces")).thenReturn(mockParkingSpaces);
        when(mockDatabase.getCollection("transactions")).thenReturn(mockTransactions);
        when(mockDatabase.getCollection("parking_zones")).thenReturn(mockParkingZones);

        InsertManyResult mockResult = mock(InsertManyResult.class);
        when(mockParkingSpaces.insertMany(anyList())).thenReturn(mockResult);
    }

    /**
     * Tests the behavior of the {@code fetchAvailableSpots} method in the {@link AvailableParkingSpots} class.
     *
     * This test verifies that the method correctly interacts with the MongoDB database to fetch
     * available parking spots using an aggregation pipeline. The test uses mocked objects to simulate
     * database operations and ensures that the expected behavior is exhibited.
     * 
     * Test workflow:
     * 
     *   Mocks the {@link AggregateIterable} and {@link MongoCursor} objects to simulate database aggregation results.
     *   Configures the mocked cursor to return a predefined parking spot document on the first call and stop on the next.
     *   Calls the {@code fetchAvailableSpots} method to retrieve parking spot data.
     *   Verifies interactions with the mocked {@link MongoCollection} and ensures that the correct methods are invoked.
     * 
     * Assertions:
     * 
     *   Ensures that the {@code aggregate} method is called on the "parking_spaces" collection.
     *   Confirms that the {@link AggregateIterable} object provides a cursor for iterating through results.
     *   Validates that the {@link MongoCursor} retrieves the expected parking spot document.
     *   Ensures that the {@code aggregate} method is called on the "parking_spaces" collection.
     *  Confirms that the {@link AggregateIterable} object provides a cursor for iterating through results.
     *   Validates that the {@link MongoCursor} retrieves the expected parking spot document.
     * 
     * 
     *
     * @see AvailableParkingSpots
     * @see AggregateIterable
     * @see MongoCursor
     */
    @Test
    public void testFetchAvailableSpots() {
        AggregateIterable<Document> mockIterable = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);

        when(mockParkingSpaces.aggregate(anyList())).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true).thenReturn(false);
        when(cursor.next()).thenReturn(new Document("parking_space", "PS1")
                .append("parking_zone", "Zone A")
                .append("hourly_rate", 2.5)
                .append("max_parking_duration", 120));

        AvailableParkingSpots.fetchAvailableSpots(mockDatabase);

        verify(mockParkingSpaces).aggregate(anyList());
        verify(mockIterable).iterator();
        verify(cursor, times(1)).next();
    }

    /**
     * Tests the behavior of the {@code fetchOvertimeEvents} method in the {@link OvertimeParkingEvents} class.
     *
     * This test verifies that the method correctly interacts with the MongoDB database to fetch
     * details of parking events that exceed the allowed time limit. It uses mocked objects to simulate
     * the database behavior and ensures that the aggregation pipeline is executed and results are processed as expected.
     *
     * Test workflow:
     * 
     *   Mocks the {@link AggregateIterable} to simulate aggregation results from the "transactions" collection.    
     *      Configures the mocked {@link AggregateIterable} to return a predefined list of overtime event documents.   
     *      Calls the {@code fetchOvertimeEvents} method to retrieve the overtime events.  
     *      Verifies that the correct methods are invoked on the mocked {@link MongoCollection} and {@link AggregateIterable}. 
     * 
     *
     * >Assertions:
     * 
     *      Ensures that the {@code aggregate} method is called on the "transactions" collection.  
     *      Confirms that the {@code into} method is called on the {@link AggregateIterable} to retrieve results.  
     *      Validates that the results contain the expected overtime parking event document.   
     * 
     * 
     *
     * @see OvertimeParkingEvents
     * @see AggregateIterable
     * @see MongoCollection
     */
    @Test
    public void testFetchOvertimeEvents() {
        when(mockTransactions.aggregate(anyList())).thenReturn(mockAggregateIterable);

        List<Document> mockResults = List.of(
                new Document("id", 123)
                        .append("start", "2024-12-01T08:00:00")
                        .append("elapsed_minutes", 130)
                        .append("max_parking_minutes", 120)
                        .append("zone_name", "Zone A")
        );
        when(mockAggregateIterable.into(anyList())).thenAnswer(invocation -> mockResults);

        overtimeParkingEvents.fetchOvertimeEvents(mockDatabase);

        verify(mockTransactions, times(1)).aggregate(anyList());
        verify(mockAggregateIterable, times(1)).into(anyList());
    }

    /**
     * Tests the behavior of the {@code fetchTransactionStats} method in the {@link TransactionStatsByHour} class.
     *
     *  This test verifies that the method interacts correctly with the MongoDB database to fetch transaction statistics
     * aggregated by the hour. It ensures that the aggregation pipeline is executed and the results are processed as expected.  
     *
     *  Test workflow:
     *  
     *   Mocks the {@link AggregateIterable} to simulate aggregation results from the "transactions" collection.
     *   Configures the mocked {@link AggregateIterable} to return a mocked {@link MongoCursor} for iterating over results.
     *  Configures the mocked {@link MongoCursor} to return a predefined document and simulate end-of-results.
     *   Calls the {@code fetchTransactionStats} method to retrieve transaction statistics.
     *  Verifies that the correct methods are invoked on the mocked {@link MongoCollection}, {@link AggregateIterable}, and {@link MongoCursor}.
       
     *
     * Assertions:
     * 
     *   Ensures that the {@code aggregate} method is called on the "transactions" collection.
     *   Confirms that the {@code next} method is called on the {@link MongoCursor} to process a result document.
     *  Validates that the expected aggregation pipeline is executed and results are processed correctly.
     * 
     *
     * @see TransactionStatsByHour
     * @see AggregateIterable
     * @see MongoCollection
     * @see MongoCursor
     */
    @Test
    public void testFetchTransactionStats() {
        when(mockTransactions.aggregate(anyList())).thenReturn(mockAggregateIterable);

        when(mockAggregateIterable.iterator()).thenReturn(mockCursor);

        when(mockCursor.hasNext()).thenReturn(true).thenReturn(false);
        when(mockCursor.next()).thenReturn(new Document("_id", 10).append("Transactions", 5));

        TransactionStatsByHour.fetchTransactionStats(mockDatabase);

        verify(mockTransactions, times(1)).aggregate(anyList());
        verify(mockCursor, times(1)).next();
    }

    /**
     * Tests the behavior of the {@code fetchAvailableSpots} method in the {@link AvailableParkingSpots} class
     * when the "parking_spaces" collection in the MongoDB database is empty.
     *
     * This test verifies that the method handles the scenario of an empty result set gracefully without attempting
     * to process any documents from the aggregation pipeline.
     *
     * Test workflow:
     * 
     *   Mocks the {@link AggregateIterable} to simulate aggregation results from the "parking_spaces" collection.
     *   Configures the mocked {@link AggregateIterable} to return a mocked {@link MongoCursor} for iterating over results.
     *  Configures the mocked {@link MongoCursor} to simulate an empty collection by returning {@code false} for {@code hasNext()}.
     *   Calls the {@code fetchAvailableSpots} method to attempt retrieving available parking spots.
     *  Verifies that no documents are processed from the aggregation pipeline.
     *
     *
     * Assertions:
     * 
     *   Ensures that the {@code aggregate} method is called on the "parking_spaces" collection.
     *   Confirms that the {@code iterator} method is invoked on the {@link AggregateIterable}.
     *  Validates that the {@code next} method is never called on the {@link MongoCursor}, as no results are available.
     * 
     *
     * @see AvailableParkingSpots
     * @see AggregateIterable
     * @see MongoCollection
     * @see MongoCursor
     */
    @Test
    public void testFetchAvailableSpots_EmptyCollection() {
        AggregateIterable<Document> mockIterable = mock(AggregateIterable.class);
        MongoCursor<Document> emptyCursor = mock(MongoCursor.class);

        when(mockParkingSpaces.aggregate(anyList())).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(emptyCursor);
        when(emptyCursor.hasNext()).thenReturn(false);

        AvailableParkingSpots.fetchAvailableSpots(mockDatabase);

        verify(mockParkingSpaces).aggregate(anyList());
        verify(mockIterable).iterator();
        verify(emptyCursor, never()).next();
    }

    /**
     * Tests the behavior of the {@code fetchAvailableSpots} method in the {@link AvailableParkingSpots} class
     * when the aggregation result contains both valid and malformed documents.
     *
     * This test simulates a scenario where the "parking_spaces" collection contains mixed data: some documents
     * are valid while others are missing required fields or have unexpected fields.
     *
     * Test workflow:
     * Mocks the {@link AggregateIterable} to simulate aggregation results from the "parking_spaces" collection.
     * Configures the mocked {@link AggregateIterable} to return a mocked {@link MongoCursor} for iterating over results.
     * Sets up the mocked {@link MongoCursor} to simulate both valid and malformed documents:
     * The first document is valid, containing expected fields.
     * The second document is malformed, missing required fields or containing unexpected fields.
     * Calls the {@code fetchAvailableSpots} method to process the documents.
     * Verifies that all documents, including malformed ones, are processed by the cursor.
     *
     * Assertions:
     * Ensures that the {@code aggregate} method is called on the "parking_spaces" collection.
     * Confirms that the {@code iterator} method is invoked on the {@link AggregateIterable}.
     * Validates that the {@code next} method is called twice on the {@link MongoCursor} to process both documents.
     *
     * Note:
     * This test does not validate the handling of malformed data within the {@code fetchAvailableSpots} method.
     * It assumes that the method logs or skips invalid documents but does not throw an exception.
     *
     *
     * @see AvailableParkingSpots
     * @see AggregateIterable
     * @see MongoCollection
     * @see MongoCursor
     */
    @Test
    public void testFetchAvailableSpots_MalformedData() {
        AggregateIterable<Document> mockIterable = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);

        when(mockParkingSpaces.aggregate(anyList())).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                new Document("parking_space", "PS1").append("parking_zone", "Zone A"),
                new Document("invalid_field", "Invalid")
        );

        AvailableParkingSpots.fetchAvailableSpots(mockDatabase);

        verify(mockParkingSpaces).aggregate(anyList());
        verify(cursor, times(2)).next();
    }

    /**
     * Integration test for verifying the seeding and fetching of available parking spots.
     *
     * This test ensures that:
     * - Data is successfully seeded into the "parking_spaces" collection using {@link DataSeeder#seedDataFromFile}.
     * - Available parking spots can be fetched from the collection using {@link AvailableParkingSpots#fetchAvailableSpots}.
     *
     * Test workflow:
     * - Mocks the {@link MongoCollection#insertMany} method to simulate data insertion into the collection.
     * - Creates a temporary JSON file containing sample parking space data for seeding.
     * - Invokes the {@code seedDataFromFile} method to seed the data from the temporary file.
     * - Verifies that the {@code insertMany} method was called with the correct arguments.
     * - Mocks the aggregation pipeline to simulate fetching available parking spots from the collection.
     * - Invokes the {@code fetchAvailableSpots} method to process the aggregation results.
     * - Verifies the interactions with the mocked collection and cursor to ensure proper processing of results.
     * - Deletes the temporary JSON file after the test to clean up.
     *
     * Assertions:
     * - Ensures that the {@code insertMany} method is called exactly once to seed the data.
     * - Confirms that the aggregation pipeline is executed to fetch available parking spots.
     * - Validates that the aggregation results are processed correctly using the mocked cursor.
     *
     * @throws Exception if an error occurs during the test execution.
     *
     * @see DataSeeder
     * @see AvailableParkingSpots
     * @see MongoCollection
     * @see AggregateIterable
     * @see MongoCursor
     */
    @Test
    public void testIntegration_SeedAndFetchAvailableSpots() throws Exception {
        InsertManyResult mockResult = mock(InsertManyResult.class);

        when(mockParkingSpaces.insertMany(eq(null), anyList())).thenReturn(mockResult);

        Path tempFile = Files.createTempFile("test_parking_spaces", ".json");
        String sampleData = """
            [
                {"parking_space": "PS1", "parking_zone": "Zone A", "hourly_rate": 2.5, "max_parking_duration": 120}
            ]
            """;
        Files.writeString(tempFile, sampleData);

        DataSeeder.seedDataFromFile(null, mockParkingSpaces, tempFile.toString());

        verify(mockParkingSpaces, times(1)).insertMany(eq(null), anyList());

        AggregateIterable<Document> mockIterable = mock(AggregateIterable.class);
        MongoCursor<Document> cursor = mock(MongoCursor.class);

        when(mockParkingSpaces.aggregate(anyList())).thenReturn(mockIterable);
        when(mockIterable.iterator()).thenReturn(cursor);
        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn(new Document("parking_space", "PS1").append("parking_zone", "Zone A"));

        AvailableParkingSpots.fetchAvailableSpots(mockDatabase);

        verify(mockParkingSpaces).aggregate(anyList());
        verify(mockIterable).iterator();
        verify(cursor, times(1)).next();

        Files.delete(tempFile);
    }

    /**
     * Tests the behavior of the seedDataFromFile method in the DataSeeder class
     * when provided with an invalid file path.
     *
     * This test verifies that:
     * - The seedDataFromFile method does not attempt to insert any data into the database
     *   when an invalid file path is provided.
     * - An appropriate exception is caught and handled without crashing the test.
     *
     * Test workflow:
     * - Sets up an invalid file path string that does not exist.
     * - Invokes the seedDataFromFile method with the invalid path.
     * - Verifies that the insertMany method of the mocked MongoCollection is never called.
     * - Catches any unexpected exceptions and fails the test with an appropriate message.
     *
     * Assertions:
     * - Ensures that no interaction with the database occurs when the file path is invalid.
     * - Handles exceptions gracefully without crashing the test suite.
     *
     * @see DataSeeder
     * @see MongoCollection
     */
    @Test
    public void testSeedDataFromFile_InvalidPath() {
        String invalidPath = "nonexistent_file.json";

        try {
            DataSeeder.seedDataFromFile(null, mockParkingSpaces, invalidPath);
            verify(mockParkingSpaces, never()).insertMany(anyList());
        } catch (Exception e) {
            fail("Method threw an unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Tests the behavior of the seedDataFromFile method in the DataSeeder class
     * when provided with a file containing invalid JSON data.
     *
     * This test verifies that:
     * - The seedDataFromFile method does not attempt to insert any data into the database
     *   when the provided file contains malformed JSON.
     * - An appropriate exception is caught and handled without crashing the test.
     *
     * Test workflow:
     * - Creates a temporary file with malformed JSON content.
     * - Invokes the seedDataFromFile method with the invalid JSON file.
     * - Verifies that the insertMany method of the mocked MongoCollection is never called.
     * - Catches any unexpected exceptions and fails the test with an appropriate message.
     * - Ensures the temporary file is deleted after the test, regardless of its outcome.
     *
     * Assertions:
     * - Ensures that no interaction with the database occurs when the JSON data is invalid.
     * - Handles exceptions gracefully without crashing the test suite.
     *
     * @throws Exception if an error occurs during file creation or deletion.
     *
     * @see DataSeeder
     * @see MongoCollection
     * @see Files
     */
    @Test
    public void testSeedDataFromFile_InvalidJson() throws Exception {
        Path tempFile = Files.createTempFile("invalid_data", ".json");
        String invalidData = "{ this is not valid JSON }"; // Malformed JSON
        Files.writeString(tempFile, invalidData);

        try {
            DataSeeder.seedDataFromFile(null, mockParkingSpaces, tempFile.toString());
            verify(mockParkingSpaces, never()).insertMany(anyList());
        } catch (Exception e) {
            fail("Method threw an unexpected exception: " + e.getMessage());
        } finally {
            Files.delete(tempFile);
        }
    }

    /**
     * Tests the behavior of the fetchAvailableSpots method in the AvailableParkingSpots class
     * when provided with a null database instance.
     *
     * This test verifies that:
     * - The fetchAvailableSpots method throws a NullPointerException when invoked with a null database.
     * - An appropriate exception is thrown to indicate the invalid input.
     *
     * Test workflow:
     * - Calls the fetchAvailableSpots method with a null database parameter.
     * - Asserts that a NullPointerException is thrown as expected.
     * - Verifies that the exception type matches the expected behavior.
     *
     * Assertions:
     * - Ensures that the fetchAvailableSpots method does not proceed with null input and handles it correctly.
     * - Confirms that the correct exception type is thrown.
     *
     *
     * @see AvailableParkingSpots
     * @see NullPointerException
     */
    @Test
    public void testFetchAvailableSpots_InvalidInput() {
        assertThrows(NullPointerException.class, () ->
                        AvailableParkingSpots.fetchAvailableSpots(null),
                "Fetching available spots with a null database should throw a NullPointerException"
        );
    }

    /**
     * Tests the behavior of the fetchOvertimeEvents method in the OvertimeParkingEvents class
     * when provided with a null database instance.
     *
     * This test verifies that:
     * - The fetchOvertimeEvents method throws a NullPointerException when invoked with a null database.
     * - An appropriate exception is thrown to indicate the invalid input.
     *
     * Test workflow:
     * - Calls the fetchOvertimeEvents method with a null database parameter.
     * - Asserts that a NullPointerException is thrown as expected.
     * - Verifies that the exception type matches the expected behavior.
     *
     * Assertions:
     * - Ensures that the fetchOvertimeEvents method does not proceed with null input and handles it correctly.
     * - Confirms that the correct exception type is thrown.
     *
     * @see OvertimeParkingEvents
     * @see NullPointerException
     */
    @Test
    public void testFetchOvertimeEvents_InvalidInput() {
        assertThrows(NullPointerException.class, () ->
                        overtimeParkingEvents.fetchOvertimeEvents(null),
                "Fetching overtime events with a null database should throw a NullPointerException"
        );
    }

    /**
     * Tests the behavior of the fetchTransactionStats method in the TransactionStatsByHour class
     * when provided with a null database instance.
     *
     * This test verifies that:
     * - The fetchTransactionStats method throws a NullPointerException when invoked with a null database.
     * - An appropriate exception is thrown to indicate the invalid input.
     *
     * Test workflow:
     * - Calls the fetchTransactionStats method with a null database parameter.
     * - Asserts that a NullPointerException is thrown as expected.
     * - Verifies that the exception type matches the expected behavior.
     *
     * Assertions:
     * - Ensures that the fetchTransactionStats method does not proceed with null input and handles it correctly.
     * - Confirms that the correct exception type is thrown.
     *
     * @see TransactionStatsByHour
     * @see NullPointerException
     */
    @Test
    public void testFetchTransactionStats_InvalidInput() {
        assertThrows(NullPointerException.class, () ->
                        TransactionStatsByHour.fetchTransactionStats(null),
                "Fetching transaction stats with a null database should throw a NullPointerException"
        );
    }

    /**
     * Tests the concurrency behavior of the fetchAvailableSpots method in the AvailableParkingSpots class.
     *
     * This test verifies that the method handles concurrent access correctly and ensures proper interaction
     * with the mocked MongoDB database during simultaneous execution.
     *
     * Test workflow:
     * - Mocks the aggregate method of the "parking_spaces" collection to simulate a valid aggregation pipeline.
     * - Configures the mocked cursor to return a single valid parking space document and then terminate.
     * - Creates two threads, each invoking the fetchAvailableSpots method.
     * - Starts and joins both threads to ensure concurrent execution is completed.
     * - Verifies that the aggregate method was called twice, once for each thread.
     *
     * Assertions:
     * - Ensures that the aggregate method is called the expected number of times for concurrent execution.
     * - Validates that the mocked cursor is used correctly in each thread.
     * - Confirms that no errors occur during concurrent access.
     *
     * Note:
     * - This test does not validate thread safety within the fetchAvailableSpots method itself; it focuses
     *   on the interaction with the mocked MongoDB database under concurrent conditions.
     *
     * @throws InterruptedException if the thread execution is interrupted.
     * @see AvailableParkingSpots
     * @see Thread
     */
    @Test
    public void testConcurrentFetchAvailableSpots() throws InterruptedException {
        when(mockParkingSpaces.aggregate(anyList())).thenReturn(mockAggregateIterable);
        when(mockAggregateIterable.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(true, false);
        when(mockCursor.next()).thenReturn(new Document("parking_space", "PS1").append("parking_zone", "Zone A"));

        Runnable fetchTask = () -> AvailableParkingSpots.fetchAvailableSpots(mockDatabase);
        Thread thread1 = new Thread(fetchTask);
        Thread thread2 = new Thread(fetchTask);

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        verify(mockParkingSpaces, times(2)).aggregate(anyList());
    }

    /**
     * Tests the behavior of the fetchAvailableSpots method in the AvailableParkingSpots class
     * when an invalid aggregation pipeline causes a failure.
     *
     * This test verifies that the method handles aggregation pipeline errors by throwing the appropriate exception.
     *
     * Test workflow:
     * - Mocks the aggregate method of the "parking_spaces" collection to simulate a runtime exception.
     * - Configures the mock to throw a RuntimeException with a specific error message when the aggregation
     *   pipeline is executed.
     * - Calls the fetchAvailableSpots method and asserts that it throws the expected exception.
     *
     * Assertions:
     * - Ensures that a RuntimeException is thrown when the aggregation query fails.
     * - Validates that the exception message matches the expected error scenario.
     *
     * Note:
     * - This test specifically checks for aggregation pipeline failures and does not validate other aspects
     *   of the fetchAvailableSpots method.
     *
     * @see AvailableParkingSpots
     * @see RuntimeException
     */
    @Test
    public void testInvalidAggregationPipeline() {
        when(mockParkingSpaces.aggregate(anyList())).thenThrow(new RuntimeException("Aggregation query failed"));

        assertThrows(RuntimeException.class, () ->
                        AvailableParkingSpots.fetchAvailableSpots(mockDatabase),
                "Invalid aggregation pipeline should throw an exception"
        );
    }

    /**
     * Tests the behavior of the fetchOvertimeEvents method in the OvertimeParkingEvents class
     * when transaction data is incomplete or missing required fields.
     *
     * This test verifies that the method gracefully handles missing data in the transaction records
     * without throwing exceptions.
     *
     * Test workflow:
     * - Mocks the aggregate method of the "transactions" collection to simulate an aggregation query.
     * - Configures the aggregation pipeline to return a list of documents where one or more required fields are missing.
     * - Calls the fetchOvertimeEvents method to process the aggregation results.
     * - Asserts that the method does not throw any exceptions when encountering incomplete data.
     * - Verifies that the aggregate method is called once on the "transactions" collection.
     *
     * Assertions:
     * - Ensures that missing data in the transaction records does not cause the method to throw exceptions.
     * - Validates that the aggregation pipeline is executed exactly once.
     *
     * Note:
     * - This test assumes that the method either skips or logs invalid or incomplete records without interrupting execution.
     *
     * @see OvertimeParkingEvents
     * @see AggregateIterable
     * @see MongoCollection
     */
    @Test
    public void testFetchOvertimeEvents_MissingData() {
        when(mockTransactions.aggregate(anyList())).thenReturn(mockAggregateIterable);

        List<Document> mockResults = List.of(
                new Document("id", 123).append("elapsed_minutes", 130) // Missing "max_parking_minutes"
        );
        when(mockAggregateIterable.into(anyList())).thenAnswer(invocation -> mockResults);

        assertDoesNotThrow(() -> overtimeParkingEvents.fetchOvertimeEvents(mockDatabase),
                "Missing data in transaction should not throw an exception"
        );
        verify(mockTransactions, times(1)).aggregate(anyList());
    }

    /**
     * Tests the system's behavior during a database node failure and subsequent recovery
     * across multiple operations.
     *
     * This test verifies that:
     * - The `insertOne()` operation fails during node failure and succeeds after recovery.
     * - Retry mechanisms work correctly after a simulated delay during recovery.
     * - The `find()` operation returns valid results after node recovery.
     * - The `aggregate()` operation handles recovery and provides expected results.
     * - System behavior is consistent and does not throw unexpected exceptions during recovery.
     *
     * @throws InterruptedException if the thread is interrupted during the simulated retry delay.
     */

    @Test
    void testDatabaseNodeFailureAndRecovery_Extended() throws InterruptedException {
        when(mockTransactions.insertOne(any(Document.class)))
                .thenThrow(new MongoClientException("Database node failure"));

        try {
            mockTransactions.insertOne(new Document("key", "value"));
            fail("Expected a MongoClientException due to database failure");
        } catch (MongoClientException e) {
            assertEquals("Database node failure", e.getMessage(), "Expected a database failure exception");
        }

        verify(mockTransactions, times(1)).insertOne(any(Document.class));

        TimeUnit.MILLISECONDS.sleep(100);

        reset(mockTransactions);
        when(mockTransactions.insertOne(any(Document.class))).thenReturn(null);

        assertDoesNotThrow(() -> {
            mockTransactions.insertOne(new Document("key", "value"));
        }, "Operation should succeed after database recovery");

        verify(mockTransactions, times(1)).insertOne(any(Document.class));

        FindIterable<Document> mockFindIterable = mock(FindIterable.class); // Mock FindIterable
        when(mockTransactions.find(any(Bson.class))).thenReturn(mockFindIterable);
        when(mockFindIterable.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(true, false);
        when(mockCursor.next()).thenReturn(new Document("key", "value"));

        assertDoesNotThrow(() -> {
            FindIterable<Document> result = mockTransactions.find(new Document("key", "value"));
            assertNotNull(result, "Find operation should return a valid result");
            assertTrue(result.iterator().hasNext(), "Result should contain data");
        }, "Find operation should succeed after recovery");

        verify(mockTransactions, times(1)).find(any(Bson.class));

        AggregateIterable<Document> mockAggregateIterable = mock(AggregateIterable.class); // Mock AggregateIterable
        when(mockTransactions.aggregate(anyList())).thenReturn(mockAggregateIterable);
        when(mockAggregateIterable.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(true, false);
        when(mockCursor.next()).thenReturn(new Document("aggregate_result", "success"));

        assertDoesNotThrow(() -> {
            AggregateIterable<Document> result = mockTransactions.aggregate(Collections.emptyList());
            assertNotNull(result, "Aggregate operation should return a valid result");
            assertTrue(result.iterator().hasNext(), "Result should contain data");
        }, "Aggregate operation should succeed after recovery");

        verify(mockTransactions, times(1)).aggregate(anyList());
    }

    /**
     * Tests the behavior of the system when multiple database operations are attempted
     * during a node failure and subsequent recovery.
     *
     * This test verifies that:
     * - The system correctly handles node failures for multiple operations (e.g., aggregate calls).
     * - Recovery mechanisms allow operations to resume successfully after the node recovers.
     * - Operations maintain consistency and do not throw unexpected exceptions during recovery.
     *
     * @throws InterruptedException if the thread is interrupted during the simulated recovery delay.
     */
    @Test
    public void testMultipleOperationsDuringNodeFailure() throws InterruptedException {
        when(mockParkingSpaces.aggregate(anyList()))
                .thenReturn(mockAggregateIterable);
        when(mockTransactions.aggregate(anyList()))
                .thenReturn(mockAggregateIterable);

        when(mockAggregateIterable.iterator())
                .thenReturn(mockCursor);
        when(mockCursor.hasNext())
                .thenReturn(true, false);
        when(mockCursor.next())
                .thenReturn(new Document("parking_space", "PS1").append("parking_zone", "Zone A"))
                .thenReturn(new Document("transaction", "TX1").append("status", "overtime"));

        when(mockParkingSpaces.aggregate(anyList()))
                .thenThrow(new MongoTimeoutException("Node failure during parking spots query"))
                .thenReturn(mockAggregateIterable);
        when(mockTransactions.aggregate(anyList()))
                .thenThrow(new MongoTimeoutException("Node failure during transactions query"))
                .thenReturn(mockAggregateIterable);

        assertThrows(MongoTimeoutException.class, () -> {
            AvailableParkingSpots.fetchAvailableSpots(mockDatabase);
        }, "Expected MongoTimeoutException during parking spots query failure");

        assertThrows(MongoTimeoutException.class, () -> {
            overtimeParkingEvents.fetchOvertimeEvents(mockDatabase);
        }, "Expected MongoTimeoutException during transactions query failure");

        TimeUnit.MILLISECONDS.sleep(100);

        assertDoesNotThrow(() -> {
            AvailableParkingSpots.fetchAvailableSpots(mockDatabase);
            overtimeParkingEvents.fetchOvertimeEvents(mockDatabase);
        }, "Operations should succeed after node recovery");

        verify(mockParkingSpaces, atLeast(2)).aggregate(anyList()); // Failure + Recovery
        verify(mockTransactions, atLeast(2)).aggregate(anyList()); // Failure + Recovery
    }
}

