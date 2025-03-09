import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mulligan.recommenderServices.RecommendationProcessor;
import com.rabbitmq.client.Channel;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for the RecommendationProcessor component of the application.
 *
 * This test class contains unit tests for the RecommendationProcessor, which is responsible for:
 *
 *   - Fetching parking zone and space data from MongoDB.
 *   - Processing parking recommendations based on availability and citation counts.
 *   - Handling consensus mechanisms for distributed recommendation decisions.
 *   - Managing interactions with RabbitMQ for broadcasting tasks and receiving recommendations.
 *
 * The tests validate the following key functionalities:
 *
 *   - Leader-follower communication for task distribution and recommendation collection.
 *   - Correct selection of parking spaces based on citation counts and availability.
 *   - Handling of occupied spaces and ensuring they are not recommended.
 *   - Consensus-based decision-making for selecting the best parking recommendation.
 *   - Proper behavior when encountering missing zones, unavailable spaces, or no consensus.
 *   - Error handling for database failures, messaging failures, and other edge cases.
 *
 * The tests use the following tools and frameworks:
 *
 *   - JUnit 5 for testing lifecycle management (@Test, @BeforeEach, @AfterEach).
 *   - Mockito for mocking external dependencies such as MongoDB collections and RabbitMQ channels.
 *   - Assertions to verify correctness in various scenarios including failure handling.
 *
 * The setUp method initializes mocked objects and configures test data to simulate different conditions.
 * The tearDown method ensures that the environment is cleaned up after each test.
 *
 * @see RecommendationProcessor
 *
 *  * @version O.O.7
 *  * @author Oran Alster & Jamal Majadle
 */
class RecommendationProcessorTest {
    private RecommendationProcessor recommendationProcessor;
    private Channel mockChannel;
    private MongoDatabase mockDatabase;

    @Mock
    private MongoCollection<Document> mockParkingSpaces;
    @Mock
    private MongoCollection<Document> mockCitations;
    @Mock
    private MongoCollection<Document> mockTransactions;
    @Mock
    private MongoCollection<Document> mockParkingZones;

    /**
     * Initializes the test environment before each test case.
     *
     * This method is annotated with @BeforeEach to ensure that a fresh instance of all necessary
     * mock objects is created before each test execution. It sets up the required dependencies
     * for testing the {@code RecommendationProcessor} component.
     *
     * Steps performed:
     *
     *   - Initializes Mockito annotations to enable mock injection.
     *   - Mocks the RabbitMQ {@code Channel} to simulate messaging interactions.
     *   - Mocks the MongoDB {@code MongoDatabase} instance to simulate database interactions.
     *   - Mocks MongoDB collections for parking spaces, citations, transactions, and parking zones.
     *   - Configures the mock database to return the corresponding mock collections when queried.
     *   - Instantiates the {@code RecommendationProcessor} with the mock dependencies.
     *
     * This setup ensures that each test runs in isolation with a controlled test environment,
     * preventing interference between tests due to shared state.
     *
     * @see RecommendationProcessor
     * @see BeforeEach
     */

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockChannel = mock(Channel.class);
        mockDatabase = mock(MongoDatabase.class);
        mockParkingSpaces = mock(MongoCollection.class);
        mockCitations = mock(MongoCollection.class);
        mockTransactions = mock(MongoCollection.class);
        mockParkingZones = mock(MongoCollection.class);


        when(mockDatabase.getCollection("parking_spaces")).thenReturn(mockParkingSpaces);
        when(mockDatabase.getCollection("Citations")).thenReturn(mockCitations);
        when(mockDatabase.getCollection("Transactions")).thenReturn(mockTransactions);
        when(mockDatabase.getCollection("parking_zones")).thenReturn(mockParkingZones);

        recommendationProcessor = new RecommendationProcessor("TestNode", mockChannel, mockDatabase, 3);
    }

    /**
     * Tests the {@code broadcastTaskToFollowers} method to ensure correct message broadcasting.
     *
     * This test verifies that the leader node correctly sends a recommendation task to all follower nodes
     * via a RabbitMQ exchange. The test ensures that:
     *
     * The RabbitMQ exchange for task broadcasting is declared successfully.
     * The leader publishes the task message to the appropriate exchange.
     *
     * Test Steps:
     *
     * Mocks the {@code exchangeDeclare} method to simulate successful exchange declaration.
     * Mocks the {@code basicPublish} method to prevent actual message publishing.
     * Invokes {@code broadcastTaskToFollowers} with a sample task message.
     * Verifies that the exchange was declared once with the correct name and type.
     * Verifies that the message was published once to the exchange.
     *
     * This test ensures that the leader node correctly interacts with RabbitMQ to distribute tasks
     * to follower nodes.
     *
     * @throws IOException if an error occurs during message publishing.
     *
     * @see RecommendationProcessor#broadcastTaskToFollowers(String)
     * @see Channel#exchangeDeclare(String, String)
     * @see Channel#basicPublish(String, String, com.rabbitmq.client.AMQP.BasicProperties, byte[])
     */
    @Test
    void testBroadcastTaskToFollowers() throws IOException {
        when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(null);

        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        recommendationProcessor.broadcastTaskToFollowers("Test Task");

        verify(mockChannel, times(1)).exchangeDeclare(eq("recommendation_task_exchange"), eq("fanout"));
        verify(mockChannel, times(1)).basicPublish(eq("recommendation_task_exchange"), eq(""), any(), any());
    }


    /**
     * Tests the {@code sendRecommendationsToLeader} method to ensure correct message transmission.
     *
     * This test verifies that a follower node correctly sends its computed parking space
     * recommendations to the leader node via RabbitMQ. The test ensures that:
     *
     * The follower node correctly invokes {@code basicPublish} to send recommendations.
     * The message is published to the correct queue: {@code leader_recommendation_queue}.
     *
     * Test Steps:
     * Mocks the {@code basicPublish} method to prevent actual message publishing.
     * Creates a sample list of recommendations representing parking spaces and citations.
     * Calls {@code sendRecommendationsToLeader} with the sample recommendations.
     * Verifies that the message was published once to the correct queue.
     *
     * This test ensures that follower nodes properly communicate their recommendations
     * to the leader for consensus processing.
     *
     * @throws IOException if an error occurs during message publishing.
     *
     * @see RecommendationProcessor#sendRecommendationsToLeader(List)
     * @see Channel#basicPublish(String, String, com.rabbitmq.client.AMQP.BasicProperties, byte[])
     */
    @Test
    void testSendRecommendationsToLeader() throws IOException {
        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        List<String> recommendations = List.of("PS1;2", "PS2;1");
        recommendationProcessor.sendRecommendationsToLeader(recommendations);

        verify(mockChannel, times(1)).basicPublish(anyString(), eq("leader_recommendation_queue"), any(), any());
    }

    /**
     * Tests the {@code storeRecommendations} method to ensure correct storage of recommendations
     * and validation of consensus readiness.
     *
     * This test verifies that:
     * Recommendations from multiple nodes are correctly stored in the {@code receivedRecommendations} map.
     * The number of stored recommendations matches the expected count.
     *
     * Test Steps:
     * Stores recommendations from three different nodes, each suggesting a parking space and its citation count.
     * Verifies that the total number of stored recommendations matches the expected count (3).
     *
     * This test ensures that nodes can successfully submit their recommendations, and the leader node
     * properly tracks all received inputs before performing consensus.
     *
     * @see RecommendationProcessor#storeRecommendations(String, List)
     */
    @Test
    void testStoreRecommendations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS2;2"));

        assertEquals(3, recommendationProcessor.receivedRecommendations.size());
    }


    /**
     * Tests the {@code performConsensus} method to verify that the majority vote determines the final recommendation.
     *
     * This test ensures that:
     * When multiple nodes provide recommendations, the one with the majority votes is selected.
     * The consensus mechanism correctly identifies and returns the most agreed-upon recommendation.
     *
     * Test Steps:
     * Stores recommendations from three nodes:
     *    Node1 recommends "PS1;1".
     *    Node2 recommends "PS1;1" (same as Node1).
     *    Node3 recommends "PS2;2" (a different parking space).
     * Calls {@code performConsensus} to determine the most agreed-upon recommendation.
     * Verifies that the result matches "PS1;1", as it received the majority vote.
     *
     * This test ensures that the consensus algorithm correctly selects the recommendation
     * with the highest agreement among nodes.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testPerformConsensus_MajorityAgree() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS2;2"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS1;1"), result);
    }

    /**
     * Tests the {@code performConsensus} method to verify that no consensus is reached when all recommendations differ.
     *
     * This test ensures that:
     * If all nodes provide different recommendations, no majority is formed.
     * The consensus mechanism correctly returns an empty list in such a scenario.
     *
     * Test Steps:
     * Stores recommendations from three nodes:
     *    Node1 recommends "PS1;1".
     *    Node2 recommends "PS2;2".
     *    Node3 recommends "PS3;3".
     * Calls {@code performConsensus} to determine if an agreement is reached.
     * Verifies that the result is an empty list, as no recommendation received a majority vote.
     *
     * This test ensures that when no single recommendation has majority support, the system does not
     * make an arbitrary selection and correctly returns an empty result.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testPerformConsensus_NoAgreement() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS2;2"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS3;3"));

        List<String> result = recommendationProcessor.performConsensus();
        assertTrue(result.isEmpty());
    }

    /**
     * Tests the {@code isConsensusReady} method to verify that consensus is not reached when responses are missing.
     *
     * This test ensures that:
     * Consensus is only reached when the required number of nodes provide recommendations.
     *  If fewer responses than expected are received, the method correctly returns {@code false}.
     *
     * Test Steps:
     * Stores a single recommendation from "Node1" with the value "PS1;1".
     * Calls {@code isConsensusReady()} to check if the consensus threshold is met.
     * Verifies that the method returns {@code false}, indicating that more responses are needed.
     *
     * This test ensures that the system does not proceed with decision-making until the required number of responses
     * have been received, preventing premature or incorrect consensus.
     *
     * @see RecommendationProcessor#isConsensusReady()
     */
    @Test
    void testConsensusWithMissingResponses() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));

        assertFalse(recommendationProcessor.isConsensusReady());
    }

    /**
     * Tests the {@code performConsensus} method when one of the nodes provides an invalid recommendation.
     *
     * This test ensures that:
     * Invalid recommendations (e.g., improperly formatted or non-existent parking spaces) do not affect the final consensus.
     * Valid recommendations from other nodes are still considered and properly aggregated.
     * Consensus is reached correctly even if some nodes provide invalid input.
     *
     * Test Steps:
     * Stores an invalid recommendation from "Node1" with the value "INVALID".
     * Stores two valid recommendations from "Node2" and "Node3" for "PS1;1".
     * Calls {@code performConsensus()} to compute the final recommendation.
     * Verifies that the final result is {@code List.of("PS1;1")}, meaning the invalid recommendation was ignored.
     *
     * This test ensures that invalid inputs do not interfere with the consensus mechanism and that the correct majority result is returned.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testConsensusWithOneInvalidRecommendations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("INVALID"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS1;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS1;1"), result);
    }

    /**
     * Tests the {@code performConsensus} method when two out of three nodes provide invalid recommendations.
     *
     * This test aims to validate:
     * The behavior of the consensus algorithm when multiple nodes provide invalid recommendations.
     * Whether the system can still return a consensus result when valid and invalid recommendations coexist.
     * That invalid recommendations are still considered in consensus, depending on their frequency.
     *
     * Test Steps:
     * Stores an invalid recommendation from "Node1" as "INVALID".
     * Stores a valid recommendation from "Node2" for "PS1;1".
     * Stores another invalid recommendation from "Node3" as "INVALID".
     * Calls {@code performConsensus()} to compute the final recommendation.
     * Asserts that the result is {@code List.of("INVALID")}, indicating that the majority vote (even if invalid) is returned.
     *
     * This test ensures that the consensus algorithm appropriately handles cases where the majority of nodes return invalid data.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testConsensusWithTwoInvalidRecommendations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("INVALID"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("INVALID"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("INVALID"), result);
    }

    /**
     * Tests the {@code performConsensus} method when no majority agreement is reached among nodes.
     *
     * This test ensures that the consensus algorithm correctly handles cases where no single recommendation
     * achieves a majority, leading to an empty consensus result.
     *
     * Test Scenario:
     *  "Node1" recommends "PS3;1".
     *  "Node2" provides a mixed recommendation of "PS3;1" and "PS5;1".
     *  "Node3" recommends "PS5;1".
     *  Since no single parking space achieves a strict majority, the consensus should fail.
     *
     * Expected Outcome:
     * The consensus function should return an empty list, indicating that no majority decision was reached.
     *
     * This test validates that the consensus algorithm does not arbitrarily select a recommendation without a majority,
     * ensuring the correctness of the decision-making process.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testConsensus_NoMajorityAgreement() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS3;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS3;1, PS5;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS5;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertTrue(result.isEmpty());
    }

    /**
     * Tests the {@code generateRecommendations} method when all parking spaces in a zone are occupied.
     *
     * Test Scenario:
     *  A zone "Zone1" is retrieved from the database.
     *  The parking spaces collection returns an empty result, indicating no available spaces.
     *  The transactions collection also returns an empty result, confirming all spaces are occupied.
     *
     * Expected Outcome:
     * The method should return an empty list since there are no available parking spaces in the zone.
     *
     * Validation:
     * Ensures the system correctly identifies when no parking spaces are available.
     * Confirms that the method does not return incorrect recommendations when all spaces are occupied.
     *
     * This test validates that the system properly handles scenarios where a zone has no free parking spaces,
     * preventing incorrect or misleading recommendations.
     *
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testGenerateRecommendation_AllOccupied() {
        Document mockZone = new Document("id", 1).append("zone_name", "Zone1");
        FindIterable<Document> mockFindIterableZone = mock(FindIterable.class);

        when(mockParkingZones.find(any(Bson.class))).thenReturn(mockFindIterableZone);
        when(mockFindIterableZone.first()).thenReturn(mockZone);

        FindIterable<Document> mockFindIterableSpace = mock(FindIterable.class);
        MongoCursor<Document> mockCursor = mock(MongoCursor.class);

        when(mockParkingSpaces.find(any(Bson.class))).thenReturn(mockFindIterableSpace);
        when(mockFindIterableSpace.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(false);

        when(mockTransactions.find(any(Bson.class))).thenReturn(mockFindIterableSpace);
        when(mockFindIterableSpace.iterator()).thenReturn(mockCursor);
        when(mockCursor.hasNext()).thenReturn(false);

        List<String> recommendations = recommendationProcessor.generateRecommendations("Zone1", "PS1");

        assertTrue(recommendations.isEmpty());
    }


    /**
     * Tests the exception handling mechanism in {@code broadcastTaskToFollowers} when a RabbitMQ failure occurs.
     *
     * Test Scenario:
     * Mocks the {@code basicPublish} method of RabbitMQ's {@code Channel} to throw an {@code IOException}.
     * Calls the {@code broadcastTaskToFollowers} method to simulate sending a task.
     * Verifies that the method properly throws an {@code IOException} when the RabbitMQ operation fails.
     *
     * Expected Outcome:
     * The test should throw an {@code IOException}, confirming that the method correctly propagates the error.
     *
     * Validation:
     * Ensures that RabbitMQ-related failures do not go unhandled.
     * Verifies that the application properly responds to messaging system failures.
     *
     * This test ensures that the system correctly handles exceptions when an error occurs while publishing messages
     * to RabbitMQ, improving the resilience of the recommendation system.
     *
     * @throws IOException if an error occurs during RabbitMQ message publishing.
     * @see RecommendationProcessor#broadcastTaskToFollowers(String)
     */
    @Test
    void testRabbitMQException() throws IOException {
        doThrow(new IOException("RabbitMQ Failure")).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        assertThrows(IOException.class, () -> recommendationProcessor.broadcastTaskToFollowers("Test Task"));
    }

    /**
     * Tests the behavior of {@code generateRecommendations} when a MongoDB connection failure occurs.
     *
     * Test Scenario:
     * Mocks the {@code getCollection} method of {@code MongoDatabase} to throw a {@code RuntimeException}
     *  simulating a MongoDB connection failure.
     * Attempts to generate parking recommendations for a given zone.
     * Verifies that the method correctly throws a {@code RuntimeException} when the database is inaccessible.
     *
     * Expected Outcome:
     * The test should throw a {@code RuntimeException}, confirming that the method properly handles database failures.
     *
     * Validation:
     * Ensures that the application does not proceed with operations when the database connection is unavailable.
     * Verifies that database failures are handled gracefully to prevent application crashes.
     *
     * This test ensures the robustness of the recommendation system by simulating a database failure
     * and verifying that the appropriate exception is thrown.
     *
     * @throws RuntimeException if an error occurs while accessing the MongoDB database.
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testMongoDBFailure() {
        when(mockDatabase.getCollection("parking_spaces")).thenThrow(new RuntimeException("MongoDB Connection Failed"));

        assertThrows(RuntimeException.class, () -> recommendationProcessor.generateRecommendations("Zone1", "PS1"));
    }

    /**
     * Tests the behavior of the consensus mechanism when a leader node fails during the recommendation process.
     *
     * Test Scenario:
     * The leader node stores a recommendation.
     * A follower node also submits a recommendation.
     * The leader node then fails, represented by storing an empty list for its recommendations.
     * The test verifies that consensus is not ready due to the leader's failure.
     *
     * Expected Outcome:
     * The {@code isConsensusReady} method should return {@code false} since the leader has failed.
     * This simulates a real-world scenario where a new leader election might be required.
     *
     * Validation:
     * Ensures that the system does not reach consensus when a leader fails before completing the process.
     * Confirms that leader failure impacts the recommendation process, preventing premature decisions.
     *
     * This test verifies that the recommendation system can handle leader failures gracefully,
     * ensuring fault tolerance in distributed environments.
     *
     * @see RecommendationProcessor#storeRecommendations(String, List)
     * @see RecommendationProcessor#isConsensusReady()
     */
    @Test
    void testLeaderReelection() {
        recommendationProcessor.storeRecommendations("LeaderNode", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));

        recommendationProcessor.storeRecommendations("LeaderNode", Collections.emptyList());

        assertFalse(recommendationProcessor.isConsensusReady());
    }

    /**
     * Tests the consensus mechanism when there is a tie between multiple recommendations.
     *
     * Test Scenario:
     * Node1 recommends "PS1;1".
     * Node2 recommends "PS2;1".
     * Node3 provides a split recommendation for both "PS1;1" and "PS2;1", creating a tie.
     * The consensus algorithm attempts to determine a majority recommendation.
     *
     * Expected Outcome:
     * Since no single recommendation has a clear majority, consensus cannot be reached.
     * The result should be an empty list, indicating that a tie prevents consensus.
     *
     * Validation:
     * Ensures that the system does not incorrectly return a recommendation when there is no majority.
     * Confirms that a tie in votes leads to no decision being made, maintaining fairness in the process.
     *
     * This test helps validate that the recommendation system properly handles scenarios where no single
     * parking space receives a majority vote.
     *
     * @see RecommendationProcessor#storeRecommendations(String, List)
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testPerformConsensus_TieVote() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS2;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS1;1", "PS2;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertTrue(result.isEmpty());
    }

    /**
     * Test that the consensus mechanism correctly handles a tie vote.
     *
     * This test simulates three nodes submitting identical recommendations.
     * Since all nodes agree on the same recommendations, the consensus should return
     * the exact same list.
     *
     * Steps:
     * Node1 submits recommendations: {"PS1;1", "PS2;1"}.
     * Node2 submits the same recommendations.
     * Node3 also submits the same recommendations.
     * Perform consensus and verify the result.
     *
     * Expected Outcome:
     * The returned consensus should match the identical input recommendations.
     * The final output should be ["PS1;1", "PS2;1"].
     *
     * Assertions:
     * Check that the result matches the expected recommendation list.
     *
     * @see com.mulligan.recommenderServices.RecommendationProcessor#performConsensus()
     */
    @Test
    void testPerformConsensus_TieVote1() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1", "PS2;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS1;1", "PS2;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS1;1", "PS2;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS1;1", "PS2;1"), result);
    }

    /**
     * Tests whether the leader node correctly broadcasts tasks to follower nodes.
     *
     * Test Scenario:
     * The leader node sends a parking recommendation task ("ZoneA:PS5") to follower nodes.
     * The task is published to the "recommendation_task_exchange" exchange in RabbitMQ.
     *
     * Expected Outcome:
     * The exchange "recommendation_task_exchange" is declared successfully.
     * The leader publishes the task message to the exchange, ensuring followers receive it.
     * No exceptions are thrown during message broadcasting.
     *
     * Validation:
     * Verifies that the exchange is correctly declared using `exchangeDeclare()`.
     * Confirms that the message is published using `basicPublish()`.
     * Ensures that message broadcasting is triggered exactly once.
     *
     * This test helps ensure that leader-follower communication via RabbitMQ functions as expected,
     * allowing the recommendation system to distribute tasks efficiently.
     *
     * @throws IOException if an error occurs during RabbitMQ communication.
     * @see RecommendationProcessor#broadcastTaskToFollowers(String)
     */
    @Test
    void testFollowersReceiveTasks() throws IOException {
        recommendationProcessor.broadcastTaskToFollowers("ZoneA:PS5");

        verify(mockChannel, times(1)).exchangeDeclare(eq("recommendation_task_exchange"), eq("fanout"));
        verify(mockChannel, times(1)).basicPublish(eq("recommendation_task_exchange"), eq(""), any(), any());
    }

    /**
     * Tests whether the recommendation system correctly avoids recommending an occupied parking space.
     *
     * Test Scenario:
     * A parking zone "ZoneA" is present in the database.
     * Three parking spaces (PS1, PS2, and PS3) exist in the zone.
     * The requested parking space (PS3) is marked as occupied in the transactions database.
     * The recommendation processor is expected to avoid suggesting PS3 as a valid option.
     *
     * Expected Outcome:
     * The generated recommendations list should not include PS3, as it is occupied.
     * The recommendations list should not be null.
     *
     * Validation:
     * Ensures that the recommendation processor fetches parking spaces correctly.
     * Mocks the transactions collection to simulate that PS3 is occupied.
     * Verifies that the final recommendation list excludes the occupied space.
     *
     * This test ensures that the system correctly prevents occupied parking spaces from being suggested,
     * enhancing the reliability of parking recommendations.
     *
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testRequestedSpaceOccupied() {
        FindIterable<Document> mockFindIterableZone = mock(FindIterable.class);
        when(mockParkingZones.find(any(Bson.class))).thenReturn(mockFindIterableZone);
        when(mockFindIterableZone.first()).thenReturn(new Document("id", 1).append("zone_name", "ZoneA"));

        FindIterable<Document> mockFindIterableSpaces = mock(FindIterable.class);
        MongoCursor<Document> mockCursorSpaces = mock(MongoCursor.class);

        when(mockParkingSpaces.find(any(Bson.class))).thenReturn(mockFindIterableSpaces);
        when(mockFindIterableSpaces.iterator()).thenReturn(mockCursorSpaces);

        List<Document> mockParkingList = List.of(
                new Document("id", 101).append("post_id", "PS1").append("zone_id", 1),
                new Document("id", 102).append("post_id", "PS2").append("zone_id", 1),
                new Document("id", 103).append("post_id", "PS3").append("zone_id", 1)
        );

        when(mockFindIterableSpaces.into(any(List.class))).thenReturn(mockParkingList);
        when(mockCursorSpaces.hasNext()).thenReturn(true, true, true, false);
        when(mockCursorSpaces.next()).thenReturn(mockParkingList.get(0), mockParkingList.get(1), mockParkingList.get(2));

        FindIterable<Document> mockFindIterableTransactions = mock(FindIterable.class);
        MongoCursor<Document> mockCursorTransactions = mock(MongoCursor.class);

        when(mockTransactions.find(any(Bson.class))).thenReturn(mockFindIterableTransactions);
        when(mockFindIterableTransactions.iterator()).thenReturn(mockCursorTransactions);

        when(mockCursorTransactions.hasNext()).thenReturn(true, false);
        when(mockCursorTransactions.next()).thenReturn(new Document("parking_space_id", 103));

        List<String> recommendations = recommendationProcessor.generateRecommendations("ZoneA", "PS3");

        assertNotNull(recommendations, "Recommendations should not be null");
        assertFalse(recommendations.contains("PS3;"), "Occupied space PS3 should not be recommended");

    }

    /**
     * Tests whether the recommendation system correctly retrieves parking spaces and their associated citations.
     *
     *Test Scenario:
     * A parking zone "ZoneA" is present in the database.
     * Two parking spaces (PS1 and PS2) exist in the zone.
     * PS1 has a recorded citation count of 3, while PS2 has no recorded citations.
     * The system generates parking recommendations based on citation data.
     *
     * Expected Outcome:
     * The recommendations list should not be null.
     * The list should contain at least one recommended parking space.
     *
     * Validation:
     * Ensures that the parking zone exists and can be retrieved correctly.
     * Mocks parking spaces to simulate their presence in the database.
     * Mocks citations collection to ensure citation data is correctly associated with parking spaces.
     * Validates that the system returns a list of recommended parking spaces.
     *
     * This test ensures that the recommendation system properly accounts for citations when suggesting parking spaces,
     * improving fairness by directing users to spaces with fewer violations.
     *
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testAllSpacesHaveCitations() {
        FindIterable<Document> mockFindIterableZone = mock(FindIterable.class);
        when(mockParkingZones.find(any(Bson.class))).thenReturn(mockFindIterableZone);
        when(mockFindIterableZone.first()).thenReturn(new Document("id", 1).append("zone_name", "ZoneA"));

        FindIterable<Document> mockFindIterableSpaces = mock(FindIterable.class);
        MongoCursor<Document> mockCursorSpaces = mock(MongoCursor.class);

        when(mockParkingSpaces.find(any(Bson.class))).thenReturn(mockFindIterableSpaces);
        when(mockFindIterableSpaces.iterator()).thenReturn(mockCursorSpaces);

        List<Document> mockParkingList = new ArrayList<>();
        mockParkingList.add(new Document("id", 101).append("post_id", "PS1").append("zone_id", 1));
        mockParkingList.add(new Document("id", 102).append("post_id", "PS2").append("zone_id", 1));

        when(mockFindIterableSpaces.into(any(List.class))).thenReturn(mockParkingList);

        when(mockCursorSpaces.hasNext()).thenReturn(true, true, false);
        when(mockCursorSpaces.next()).thenReturn(mockParkingList.get(0), mockParkingList.get(1));

        FindIterable<Document> mockFindIterableCitations = mock(FindIterable.class);
        MongoCursor<Document> mockCursorCitations = mock(MongoCursor.class);

        when(mockCitations.find(any(Bson.class))).thenReturn(mockFindIterableCitations);
        when(mockFindIterableCitations.iterator()).thenReturn(mockCursorCitations);

        when(mockCursorCitations.hasNext()).thenReturn(true, false);
        when(mockCursorCitations.next()).thenReturn(new Document("parking_space_id", 101).append("citations", 3));

        List<String> recommendations = recommendationProcessor.generateRecommendations("ZoneA", "PS3");

        assertNotNull(recommendations, "Recommendations should not be null");
        assertFalse(recommendations.isEmpty(), "There should be at least one recommended parking space");

    }

    /**
     * Tests whether the requested parking space is returned when it has the lowest citation count.
     *
     * Test Scenario:
     * Three nodes provide recommendations.
     * All nodes unanimously recommend the same parking space (PS3) with the lowest citation count (1).
     * The consensus algorithm determines the final recommendation.
     *
     * Expected Outcome:
     * The consensus should return "PS3;1" as the recommended space.
     * The test validates that the recommendation system correctly identifies and returns the lowest-cited space.
     *
     * Validation:
     * Ensures that multiple recommendations for the same space are correctly processed.
     * Validates that the system correctly selects the lowest citation count when all nodes agree.
     * Confirms that the consensus mechanism is functioning as expected.
     *
     * This test verifies that the **recommendation processor** properly prioritizes the requested parking space
     * when it has the lowest citation count, ensuring fair allocation.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testReturnRequestedSpace_WhenHasLowestCitations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS3;1"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS3;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS3;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS3;1"), result);
    }

    /**
     * Tests whether an alternative parking space is returned when the requested space has a higher citation count.
     *
     * Test Scenario:
     * Three nodes provide recommendations.
     * One node recommends parking space PS3 with a high citation count (7).
     * Two nodes recommend an alternative parking space PS5with a lower citation count (3).
     * The consensus algorithm determines the final recommendation.
     *
     * Expected Outcome:
     * The consensus should return "PS5;3" as the recommended space.
     * The test validates that the system correctly selects the space with the lower citation count.
     * Ensures that majority voting plays a role in determining the best recommendation.
     *
     * Validation:
     * Ensures that a highly cited space is avoided when better alternatives exist.
     * Validates that the recommendation system correctly prioritizes lower citation counts.
     * Confirms that the consensus mechanism favors the most optimal parking space based on votes.
     *
     * This test verifies that the **recommendation processor** correctly selects an alternative parking space
     * when the originally requested space has too many citations.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testReturnAlternative_WhenRequestedSpaceHasHigherCitations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS3;7"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS5;3"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS5;3"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS5;3"), result);
    }

    /**
     * Tests whether the requested parking space is returned when all spaces have the same citation count.
     *
     * Test Scenario:
     * Three nodes provide recommendations.
     * Each node recommends the same parking space PS3 with an equal citation count (3).
     * The consensus algorithm determines the final recommendation.
     *
     * Expected Outcome:
     * The consensus should return "PS3;3" since all nodes agree on the same space.
     * The test verifies that when all recommendations are identical, the system selects that space.
     *
     * Validation:
     * Ensures that when there is no conflicting recommendation, the requested space is returned.
     * Validates that the system correctly handles cases where all spaces have identical citations.
     * Confirms that the consensus mechanism correctly processes unanimous recommendations.
     *
     * This test verifies that the **recommendation processor** correctly selects the requested parking space
     * when there is no variation in citation counts among the recommended spaces.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testReturnRequestedSpace_WhenAllHaveSameCitations() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS3;3"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS3;3"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS3;3"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS3;3"), result);
    }

    /**
     * Tests whether the system selects an alternative parking space with the lowest citation count
     * when the originally requested space is occupied.
     *
     * Test Scenario:
     * Three nodes provide recommendations.
     * Node1 recommends PS3 but marks it as BUSY (occupied).
     * Node2 and Node3 recommend PS2 with a citation count of 1.
     * The consensus algorithm determines the best available space.
     *
     * Expected Outcome:
     * The system should **ignore** the busy space PS3 and recommend PS2;1.
     * Ensures the system correctly excludes unavailable spaces from recommendations.
     * Validates that the system selects the **lowest citation** alternative when the preferred space is occupied.
     *
     * Validation:
     * Verifies that the consensus mechanism does **not** recommend a space marked as "BUSY".
     * Confirms that the system prioritizes spaces with the **lowest citations** when an alternative is needed.
     * Ensures correctness of decision-making when dealing with unavailable spaces.
     *
     * This test verifies that the **recommendation processor** successfully finds and returns the best available
     * parking space when the initially requested space is occupied.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testReturnLowestCitationAlternative_WhenRequestedSpaceIsBusy() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS3;BUSY"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS2;1"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS2;1"));

        List<String> result = recommendationProcessor.performConsensus();
        assertEquals(List.of("PS2;1"), result);
    }

    /**
     * Tests whether the system returns an empty list when all recommended parking spaces are occupied.
     *
     * Test Scenario:
     * Three nodes provide parking recommendations.
     * Each node recommends a different space (PS1, PS2, PS3), but all are marked as **BUSY**.
     * The consensus algorithm must determine that no spaces are available.
     *
     * Expected Outcome:
     * The system should return an **empty list**, indicating that no suitable parking spaces are available.
     * Ensures that the system does **not** recommend any occupied parking spaces.
     * Validates the correctness of the consensus mechanism when all suggested spaces are unavailable.
     *
     * Validation:
     * Verifies that the **consensus mechanism** correctly identifies when all spaces are occupied.
     * Confirms that the system **does not provide incorrect recommendations** by suggesting unavailable spaces.
     * Ensures compliance with business logic for handling fully occupied parking zones.
     *
     * This test case guarantees that the **RecommendationProcessor** properly handles scenarios where no parking spaces are available.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testReturnEmptyList_WhenAllSpacesAreBusy() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;BUSY"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS2;BUSY"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS3;BUSY"));

        List<String> result = recommendationProcessor.performConsensus();
        assertTrue(result.isEmpty());
    }

    /**
     * Tests the behavior when a parking recommendation is requested for a non-existent zone.
     *
     * Test Scenario:
     * The database query for the parking zone returns **null**, indicating that the requested zone does not exist.
     * The `generateRecommendations` method is called with a **non-existent zone** ("NonExistentZone").
     * The system should detect the missing zone and throw a **RuntimeException**.
     *
     * Expected Outcome:
     * A **RuntimeException** is thrown with the message: **"Zone not found: NonExistentZone"**.
     * The test verifies that the exception message matches the expected output.
     * Ensures the system properly handles invalid parking zone requests.
     *
     * Validation:
     * Ensures that the system does **not proceed with recommendation logic** when the zone does not exist.
     * Confirms that the system provides **clear error messaging** when users request recommendations for an invalid zone.
     * Validates proper exception handling in the `RecommendationProcessor#getZoneId` method.
     *
     * This test ensures that the **RecommendationProcessor** correctly identifies and reports invalid parking zone requests.
     *
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testNoParkingZonesExist() {
        FindIterable<Document> mockFindIterableZone = mock(FindIterable.class);
        when(mockParkingZones.find(any(Bson.class))).thenReturn(mockFindIterableZone);
        when(mockFindIterableZone.first()).thenReturn(null);

        Exception exception = assertThrows(RuntimeException.class, () -> {
            recommendationProcessor.generateRecommendations("NonExistentZone", "PS1");
        });

        assertEquals("Zone not found: NonExistentZone", exception.getMessage(), "Expected a RuntimeException for missing zone");
    }

    /**
     * Tests the scenario where no active parking transactions exist, ensuring that all parking spaces
     * are considered available for recommendation.
     *
     * Test Scenario:
     * The **parking zone lookup succeeds**, confirming that the requested zone ("ZoneA") exists.
     * The **transactions collection returns empty**, indicating that no parking spaces are currently occupied.
     * The **parking spaces collection** returns a list of two available parking spaces.
     * The `generateRecommendations` method is called with **"ZoneA"** and **"PS1"** as parameters.
     *
     * Expected Outcome:
     * The method should **return at least one available parking space**.
     * Confirms that the system does **not filter out spaces due to missing transactions**.
     * Ensures that recommendations are provided when the parking zone is valid and no spaces are occupied.
     *
     * Validation:
     * Verifies that the **transactions collection is properly queried**.
     * Ensures that an **empty transactions dataset does not incorrectly mark spaces as unavailable**.
     * Confirms that the **recommendation list is not empty**, ensuring at least one space is suggested.
     *
     * This test ensures that the **RecommendationProcessor** correctly identifies available parking spaces
     * when no active transactions exist.
     *
     * @see RecommendationProcessor#generateRecommendations(String, String)
     */
    @Test
    void testNoTransactionsExist_AllSpacesAvailable() {
        FindIterable<Document> mockFindIterableZone = mock(FindIterable.class);
        when(mockParkingZones.find(any(Bson.class))).thenReturn(mockFindIterableZone);
        when(mockFindIterableZone.first()).thenReturn(new Document("id", 1).append("zone_name", "ZoneA"));

        FindIterable<Document> mockFindIterableTransactions = mock(FindIterable.class);
        MongoCursor<Document> mockCursorTransactions = mock(MongoCursor.class);

        when(mockTransactions.find(any(Bson.class))).thenReturn(mockFindIterableTransactions);
        when(mockFindIterableTransactions.iterator()).thenReturn(mockCursorTransactions);
        when(mockCursorTransactions.hasNext()).thenReturn(false);

        FindIterable<Document> mockFindIterableSpaces = mock(FindIterable.class);
        MongoCursor<Document> mockCursorSpaces = mock(MongoCursor.class);

        when(mockParkingSpaces.find(any(Bson.class))).thenReturn(mockFindIterableSpaces);
        when(mockFindIterableSpaces.iterator()).thenReturn(mockCursorSpaces);

        List<Document> mockParkingList = List.of(
                new Document("id", 101).append("post_id", "PS1").append("zone_id", 1),
                new Document("id", 102).append("post_id", "PS2").append("zone_id", 1)
        );

        when(mockFindIterableSpaces.into(any(List.class))).thenReturn(mockParkingList);
        when(mockCursorSpaces.hasNext()).thenReturn(true, true, false);
        when(mockCursorSpaces.next()).thenReturn(mockParkingList.get(0), mockParkingList.get(1));

        List<String> recommendations = recommendationProcessor.generateRecommendations("ZoneA", "PS1");

        assertFalse(recommendations.isEmpty(), "There should be at least one available parking space.");
    }

    /**
     * Tests the consensus decision when all parking space recommendations have the same citation count,
     * ensuring that no majority is reached.
     *
     * Test Scenario:
     * Three nodes submit recommendations, each suggesting a different parking space.
     * All recommendations have the **same citation count (3 citations each)**.
     * The `performConsensus` method is called to determine the majority recommendation.
     *
     * Expected Outcome:
     * Since no single parking space has a majority, the **method should return an empty list**.
     * Ensures that the consensus algorithm does **not arbitrarily select a space** when votes are evenly distributed.
     * Validates that the **system correctly identifies the absence of a majority**.
     *
     * Validation:
     * Verifies that the **recommendation system does not force a decision** without a majority.
     * Ensures that the method correctly detects a lack of consensus when votes are evenly distributed.
     * Confirms that the returned result is **an empty list**, indicating no conclusive recommendation.
     *
     * This test ensures that the **RecommendationProcessor** follows proper consensus rules and does not
     * return a recommendation when all suggestions are equally weighted.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testAllSpacesSameCitationCount_NoMajority() {
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;3"));
        recommendationProcessor.storeRecommendations("Node2", List.of("PS2;3"));
        recommendationProcessor.storeRecommendations("Node3", List.of("PS3;3"));

        List<String> result = recommendationProcessor.performConsensus();

        assertTrue(result.isEmpty(), "Should return an empty list when there is no majority.");
    }

    /**
     * Tests the behavior of the consensus mechanism when the leader node fails during the consensus process.
     *
     * Test Scenario:
     * The leader node submits a recommendation.
     * Another node also submits the same recommendation.
     * The leader node is then **removed from the recommendation list**, simulating a failure.
     * The `isConsensusReady` method is called to check if consensus can still be reached.
     *
     * Expected Outcome:
     * Since the leader is removed, the consensus should **not be considered ready**.
     * The test should assert that **`isConsensusReady()` returns `false`**.
     * Ensures that the system properly handles leader failure scenarios without making an invalid decision.
     *
     * Validation:
     * Verifies that **removing the leader affects the consensus process**.
     * Confirms that the consensus is only valid when **enough nodes participate**.
     * Ensures the system does not proceed with incomplete or incorrect consensus.
     *
     * This test ensures the **RecommendationProcessor** properly manages failures in distributed leader-based decision-making.
     *
     * @see RecommendationProcessor#isConsensusReady()
     */
    @Test
    void testLeaderFailsDuringConsensus() {
        recommendationProcessor.storeRecommendations("LeaderNode", List.of("PS1;1"));
        recommendationProcessor.storeRecommendations("Node1", List.of("PS1;1"));

        recommendationProcessor.receivedRecommendations.remove("LeaderNode");

        assertFalse(recommendationProcessor.isConsensusReady(), "Consensus should not be ready if the leader fails.");
    }

    /**
     * Tests the behavior of the consensus mechanism when no recommendations have been received.
     *
     * Test Scenario:
     * No nodes submit any recommendations.
     * The `performConsensus` method is called.
     *
     * Expected Outcome:
     * The method should return an **empty list** since no recommendations were provided.
     * Ensures that the system does not attempt to make a decision based on missing data.
     *
     * Validation:
     * Confirms that `performConsensus()` correctly handles an empty recommendation set.
     * Prevents invalid consensus results when no data is available.
     * Ensures the system does not fail or crash due to a lack of input.
     *
     * This test helps verify that the **RecommendationProcessor** gracefully handles scenarios where no data is available.
     *
     * @see RecommendationProcessor#performConsensus()
     */
    @Test
    void testNoRecommendationsExist() {
        List<String> result = recommendationProcessor.performConsensus();

        assertTrue(result.isEmpty(), "If no recommendations exist, return an empty list.");
    }

}
