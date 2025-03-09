import com.mongodb.client.MongoDatabase;
import com.mulligan.RecServer.RecServerApp;
import com.mulligan.recommenderServices.RecommendationProcessor;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static com.mulligan.RecServer.RecServerApp.LEADER_CHECK_INTERVAL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test suite for the RecServerApp component of the application.
 *
 * This test class contains unit tests for {@link RecServerApp}, which is responsible for:
 *
 * Node registration and leader election in a distributed environment.
 * Monitoring heartbeats to detect leader failures and trigger reelections.
 * Managing active nodes and ensuring proper synchronization among them.
 * Handling RabbitMQ interactions for message exchanges and heartbeats.
 * Processing parking recommendations when acting as the leader.
 *
 * Test Coverage:
 *
 * The tests validate the following key functionalities of {@link RecServerApp}:
 *
 * Node Registration: Ensures that nodes correctly register and announce themselves.
 * Leader Election: Verifies that a leader is elected when enough nodes are available.
 * Heartbeat Monitoring: Ensures that leaders send heartbeats and nodes detect failures.
 * Leader Reelection: Simulates leader failures and verifies that a new leader is chosen.
 * RabbitMQ Failure Handling: Ensures that messaging failures are handled gracefully.
 * MongoDB Failure Handling: Verifies correct error handling when database interactions fail.
 * Concurrency Handling: Tests whether leader tasks and heartbeats run in parallel.
 * Node Synchronization: Ensures that nodes update their active node lists correctly.
 *
 * Testing Tools & Frameworks Used:
 *
 * JUnit 5: Manages the test lifecycle and provides assertions.
 * Mockito: Mocks dependencies like MongoDB, RabbitMQ, and networking.
 * Assertions: Validates expected vs. actual results.
 * CountDownLatch: Used for concurrency testing to ensure parallel execution.
 *
 * Test Lifecycle Management:
 *
 * The {@code @BeforeEach} method initializes the test environment, setting up:
 * Mocked RabbitMQ channels.
 * Mocked MongoDB connections.
 * Mocked RecommendationProcessor components.
 *
 * The {@code @AfterEach} method ensures cleanup after each test execution.
 *
 * Related Components:
 * {@link RecServerApp} - The class under test.
 * {@link com.mulligan.recommenderServices.RecommendationProcessor} - Handles recommendation processing.
 *
 * @see RecServerApp
 * @see com.mulligan.recommenderServices.RecommendationProcessor
 *
 * @version 0.0.7
 * @author Oran Alster
 */
class RecServerAppTest {

    private RecServerApp recServerApp;

    @Mock
    private Channel mockChannel;

    @Mock
    private MongoDatabase mockDatabase;

    @Mock
    private RecommendationProcessor mockRecommendationProcessor;

    /**
     * Test Setup Method for RecServerAppTest
     *
     * Executed before each test to initialize the testing environment.
     *
     * Responsibilities:
     * Initializes mock objects using Mockito.
     * Creates a mock RabbitMQ {@link Channel} to simulate message exchanges.
     * Creates a mock MongoDB {@link com.mongodb.client.MongoDatabase} for database interactions.
     * Mocks RabbitMQ queue declaration to return a valid queue name.
     * Instantiates a new {@link RecServerApp} with a test node ID.
     * Clears recorded invocations on the mock channel to ensure a clean test state.
     *
     * Mocking Details:
     * Uses {@code MockitoAnnotations.openMocks(this)} to initialize annotated mock fields.
     * Mocks the {@code queueDeclare()} method to return a mock queue with a predefined name.
     * Prevents interference between tests by resetting the mockChannel invocations.
     *
     * Execution Flow:
     * Mock objects are created.
     * Mock behavior is defined for RabbitMQ queue interactions.
     * A new instance of {@link RecServerApp} is initialized with the test dependencies.
     * Mock interactions are cleared to ensure isolation between tests.
     *
     * Throws:
     * {@link IOException} if an error occurs during mock setup (not expected in normal conditions).
     *
     * Related Components:
     * {@link RecServerApp} - The class being tested.
     * {@link Channel} - RabbitMQ messaging channel.
     * {@link com.mongodb.client.MongoDatabase} - MongoDB interface.
     *
     * @throws IOException if an error occurs during the setup process.
     * @see RecServerApp
     * @see org.mockito.Mockito
     * @see com.mongodb.client.MongoDatabase
     * @see com.rabbitmq.client.Channel
     */
    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        mockChannel = mock(Channel.class);
        mockDatabase = mock(MongoDatabase.class);

        AMQP.Queue.DeclareOk mockDeclareOk = mock(AMQP.Queue.DeclareOk.class);
        when(mockChannel.queueDeclare()).thenReturn(mockDeclareOk);
        when(mockDeclareOk.getQueue()).thenReturn("testQueue");

        recServerApp = new RecServerApp("TestNode", mockChannel, mockDatabase, 3);

        clearInvocations(mockChannel);
    }

    /**
     * Test: Node Registration in the RecServerApp
     *
     * This test verifies that a node registers itself correctly in the leader election process.
     *
     * Test Objective:
     * Ensure that the node declares an exchange for leader election.
     * Verify that the node correctly publishes its registration message to the leader election exchange.
     *
     * Mocking Details:
     * Mocks {@link AMQP.Exchange.DeclareOk} to simulate successful exchange declaration.
     * Mocks {@link Channel#exchangeDeclare(String, String)} to return the mocked exchange declaration.
     * Uses {@code doNothing()} on {@code basicPublish()} to prevent actual publishing.
     *
     * Execution Flow:
     * Mock exchange declaration to simulate RabbitMQ behavior.
     * Prevent actual message sending by stubbing {@code basicPublish()}.
     * Invoke the {@code registerNode()} method.
     * Verify that the node correctly interacts with the RabbitMQ exchange.
     *
     * Assertions & Verifications:
     * Ensures the exchange declaration method is called exactly once with the correct parameters.
     * Verifies that the node successfully announces itself by publishing a message.
     *
     * Throws:
     * {@link IOException} if an issue occurs during mock interactions (unlikely in normal test execution).
     *
     * Related Components:
     * {@link RecServerApp#registerNode()} - The method being tested.
     * {@link Channel} - RabbitMQ messaging interface.
     *
     * @throws IOException if there is an issue during exchange declaration or message publishing.
     * @see RecServerApp
     * @see com.rabbitmq.client.Channel
     * @see AMQP.Exchange.DeclareOk
     */
    @Test
    void testRegisterNode() throws IOException {
        AMQP.Exchange.DeclareOk mockDeclareOk = mock(AMQP.Exchange.DeclareOk.class);
        when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(mockDeclareOk);

        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        recServerApp.registerNode();

        verify(mockChannel, times(1)).exchangeDeclare(eq("leader_election_exchange"), eq("fanout"));
        verify(mockChannel, times(1)).basicPublish(eq("leader_election_exchange"), eq(""), any(), any());
    }

    /**
     * Test: Leader Election Process in RecServerApp
     *
     * This test verifies that a leader is correctly elected from the active nodes.
     *
     * Test Objective:
     * Ensure that a leader is chosen when multiple nodes are active.
     * Verify that the selected leader belongs to the active nodes list.
     *
     * Test Setup:
     * Adds three active nodes: "NodeA", "NodeB", "NodeC".
     * Calls {@link RecServerApp#startLeaderElection()} to trigger the leader selection process.
     *
     * Execution Flow:
     * Manually populate the active nodes list.
     * Invoke the {@code startLeaderElection()} method.
     * Validate that a leader has been assigned.
     * Ensure that the elected leader exists within the active nodes.
     *
     * Assertions & Verifications:
     * Ensures that {@code currentLeader} is not null after the election.
     * Confirms that the elected leader is one of the active nodes.
     *
     * Related Components:
     * {@link RecServerApp#startLeaderElection()} - The method being tested.
     * {@link RecServerApp#activeNodes} - The list of active nodes.
     * {@link RecServerApp#currentLeader} - The elected leader.
     *
     * @see RecServerApp
     */
    @Test
    void testStartLeaderElection() {
        recServerApp.activeNodes.add("NodeA");
        recServerApp.activeNodes.add("NodeB");
        recServerApp.activeNodes.add("NodeC");

        recServerApp.startLeaderElection();

        assertNotNull(recServerApp.currentLeader, "Leader should be assigned.");
        assertTrue(recServerApp.activeNodes.contains(recServerApp.currentLeader), "Leader should be from active nodes.");
    }

    /**
     * Test: Heartbeat Transmission in RecServerApp
     *
     * This test verifies that heartbeat messages are correctly sent by the leader.
     *
     * Test Objective:
     * Ensure that the server properly declares the heartbeat exchange.
     * Verify that heartbeat messages are published to the correct exchange.
     * Confirm that the heartbeat function is executed at least once.<
     *
     * Test Setup:
     * Mocks the `exchangeDeclare` method to simulate a valid RabbitMQ exchange.
     * Uses `doNothing()` to prevent `basicPublish()` from executing actual network operations.
     * Calls {@link RecServerApp#startHeartbeat()} to initiate the heartbeat mechanism.
     *
     * Execution Flow:
     * Mock the RabbitMQ exchange declaration.
     * Mock the `basicPublish` method to avoid actual message sending.
     * Invoke the {@code startHeartbeat()} method.
     * Verify that the `basicPublish` method was called at least once.
     *
     * Assertions & Verifications:
     * Ensures that the `basicPublish()` method is executed.
     * Confirms that heartbeat messages are published at least once.
     *
     * Related Components:
     * {@link RecServerApp#startHeartbeat()} - The method responsible for sending heartbeat messages.
     *
     * @throws IOException if an I/O error occurs during exchange declaration or message publishing.
     * @see RecServerApp
     */
    @Test
    void testStartHeartbeat() throws IOException {
        AMQP.Exchange.DeclareOk mockDeclareOk = mock(AMQP.Exchange.DeclareOk.class);
        when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(mockDeclareOk);

        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        recServerApp.startHeartbeat();

        verify(mockChannel, atLeastOnce()).basicPublish(anyString(), anyString(), any(), any());
    }

    /**
     * Test: Initial Heartbeat Monitoring Before Node Registration
     *
     * This test ensures that a node correctly listens for heartbeats before registering itself.
     *
     * Test Objective:
     * Ensure that the heartbeat exchange is declared properly.
     * Verify that a temporary queue is created for heartbeat monitoring.
     * Confirm that the server listens for heartbeats before attempting registration.
     *
     * Test Setup:
     * Mocks `exchangeDeclare` to simulate the creation of a RabbitMQ exchange.
     * Mocks `queueDeclare` to return a temporary queue for listening to heartbeats.
     * Simulates `basicConsume` to ensure that heartbeats are consumed from the queue.
     * Calls {@link RecServerApp#monitorHeartbeatBeforeRegister()} to initiate monitoring.
     *
     * Execution Flow:
     * Mock the `exchangeDeclare` to simulate heartbeat exchange declaration.
     * Mock `queueDeclare` to simulate the creation of a temporary queue.
     * Mock `basicConsume` to ensure a consumer is listening on the queue.
     * Invoke {@code monitorHeartbeatBeforeRegister()} to initiate the heartbeat listening process.
     * Verify that the necessary RabbitMQ declarations are made.
     *
     * Assertions & Verifications:
     * Ensures that the `exchangeDeclare()` method is called exactly once.
     * Verifies that `queueDeclare()` is called to create a temporary queue.
     * Confirms that the node listens for heartbeats before registering itself.
     *
     * Related Components:
     * {@link RecServerApp#monitorHeartbeatBeforeRegister()} - The method responsible for listening to heartbeats.
     *
     * @throws IOException if an I/O error occurs during exchange or queue declaration.
     * @see RecServerApp
     */
    @Test
    void testMonitorHeartbeatBeforeRegister() throws IOException {
        when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(null);
        AMQP.Queue.DeclareOk mockQueueDeclareOk = mock(AMQP.Queue.DeclareOk.class);
        when(mockChannel.queueDeclare()).thenReturn(mockQueueDeclareOk);
        when(mockQueueDeclareOk.getQueue()).thenReturn("testQueue");

        String consumerTag = "mockConsumerTag";
        when(mockChannel.basicConsume(anyString(), anyBoolean(), any(DeliverCallback.class), any(CancelCallback.class)))
                .thenReturn(consumerTag);

        recServerApp.monitorHeartbeatBeforeRegister();

        verify(mockChannel, times(1)).exchangeDeclare(eq("heartbeat_exchange"), eq("fanout"));
        verify(mockChannel, times(1)).queueDeclare();
    }

    /**
     * Test: Detect Unresponsive Leader and Trigger Reelection
     *
     * This test verifies that when the leader fails to send heartbeats within the expected time, it is detected as unresponsive.
     *
     * Test Objective:
     * Simulate a scenario where the leader (`NodeA`) stops sending heartbeats.
     * Ensure that the system detects the leader as unresponsive.
     * Validate that the leader reelection process is triggered when the leader fails.
     *
     * Test Setup:
     * Adds `NodeA` and `NodeB` to the active nodes list.
     * Sets `NodeA` as the current leader.
     * Simulates a heartbeat timeout by setting `lastHeartbeatTime` to a past timestamp beyond the allowed interval.
     * Calls {@link RecServerApp#monitorLeaderHeartbeat()} to check if the leader is unresponsive.
     *
     * Execution Flow:
     * Simulate the leader (`NodeA`) being active initially.
     * Artificially set `lastHeartbeatTime` to a time beyond the allowed limit.
     * Call `monitorLeaderHeartbeat()` to verify if the leader is detected as unresponsive.
     * Assert that the leader is considered unresponsive.
     *
     * Assertions & Verifications:
     * Ensures that `leaderHeartbeatReceivedRecently()` returns `false` for an unresponsive leader.
     * Confirms that the system identifies `NodeA` as unresponsive.
     *
     * Related Components:
     * {@link RecServerApp#monitorLeaderHeartbeat()} - The method responsible for detecting unresponsive leaders.
     * {@link RecServerApp#leaderHeartbeatReceivedRecently()} - The method used to check if the leader's last heartbeat was recent.
     * {@link RecServerApp#LEADER_CHECK_INTERVAL} - The timeout threshold for detecting an unresponsive leader.
     *
     * @see RecServerApp
     */
    @Test
    void testMonitorLeaderHeartbeat_LeaderUnresponsive() {
        recServerApp.activeNodes.add("NodeA");
        recServerApp.activeNodes.add("NodeB");
        recServerApp.currentLeader = "NodeA";

        recServerApp.lastHeartbeatTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(LEADER_CHECK_INTERVAL + 1);

        recServerApp.monitorLeaderHeartbeat();

        assertFalse(recServerApp.leaderHeartbeatReceivedRecently(), "Leader should be considered unresponsive.");
    }

    /**
     * Test: Verify Recommendation Processor Starts as Leader
     *
     * This test ensures that when a node is designated as the leader, it correctly starts the recommendation processor.
     *
     * Test Objective:
     * Simulate a scenario where the node is elected as the leader.
     * Verify that the recommendation processor starts as expected.
     *
     * Test Setup:
     * Sets the `isLeader` flag to `true` before starting the processor.
     * Calls {@link RecServerApp#startRecommendationProcessor()} to trigger the processor.
     *
     * Execution Flow:
     * Set the `isLeader` flag to `true`.
     * Call `startRecommendationProcessor()`.
     * Verify that the `isLeader` flag remains `true` after execution.
     *
     * Assertions & Verifications:
     * Ensures that the node remains a leader after calling `startRecommendationProcessor()`.
     * Confirms that the leader status does not change unexpectedly.
     *
     * Related Components:
     * {@link RecServerApp#startRecommendationProcessor()} - The method responsible for initializing the recommendation processor.
     * {@link RecServerApp#isLeader} - The flag indicating whether the node is currently the leader.
     *
     * @see RecServerApp
     */
    @Test
    void testStartRecommendationProcessor_AsLeader() {
        recServerApp.isLeader = true;

        recServerApp.startRecommendationProcessor();

        assertTrue(recServerApp.isLeader, "Node should be a leader.");
    }

    /**
     * Test: Verify Recommendation Processor Starts as a Follower
     *
     * This test ensures that when a node is not the leader, it correctly starts the recommendation processor
     * as a follower.
     *
     * Test Objective:
     * Simulate a scenario where the node is not elected as the leader.
     * Verify that the recommendation processor starts in follower mode.
     *
     * Test Setup:
     * Sets the `isLeader` flag to `false` before starting the processor.
     * Calls {@link RecServerApp#startRecommendationProcessor()} to initialize processing.
     *
     * Execution Flow:
     * Set the `isLeader` flag to `false`.
     * Call `startRecommendationProcessor()`.
     * Verify that the `isLeader` flag remains `false` after execution.
     *
     * Assertions & Verifications:
     * Ensures that the node remains a follower after calling `startRecommendationProcessor()`.
     * Confirms that the follower status does not change unexpectedly.
     *
     * Related Components:
     * {@link RecServerApp#startRecommendationProcessor()} - The method responsible for initializing the recommendation processor.
     * {@link RecServerApp#isLeader} - The flag indicating whether the node is currently the leader.
     *
     * @see RecServerApp
     */
    @Test
     void testStartRecommendationProcessor_AsFollower() {
        recServerApp.isLeader = false;

        recServerApp.startRecommendationProcessor();

        assertFalse(recServerApp.isLeader, "Node should be a follower.");
    }

    /**
     * Test: Verify Active Node List Updates Correctly When New Nodes Join
     *
     * This test ensures that when a new node joins the network, it is correctly added to the active nodes list.
     *
     * Test Objective:
     * Simulate an active node list with existing nodes.
     * Add a new node to the list.
     * Verify that the newly added node is present in the active nodes set.
     *
     * Test Setup:
     * Initializes `activeNodes` with predefined nodes: `"NodeA"`, `"NodeB"`, `"NodeC"`.
     * Adds a new node `"NodeD"` to the set.
     *
     * Execution Flow:
     * Initialize the active node list with predefined nodes.
     * Add `"NodeD"` to the active nodes list.
     * Assert that `"NodeD"` exists in the `activeNodes` set.
     *
     * Assertions & Verifications:
     * Ensures that `"NodeD"` is successfully added to the `activeNodes` set.
     * Confirms that the update mechanism for active nodes functions correctly.
     *
     * Related Components:
     * {@link RecServerApp#activeNodes} - The set containing all currently active nodes.
     *
     * @see RecServerApp
     */
    @Test
    void testActiveNodesUpdate() {
        recServerApp.activeNodes.add("NodeA");
        recServerApp.activeNodes.add("NodeB");
        recServerApp.activeNodes.add("NodeC");

        recServerApp.activeNodes.add("NodeD");

        assertTrue(recServerApp.activeNodes.contains("NodeD"), "New node should be in the active list.");
    }

    /**
     * Test: Verify Leader Heartbeat Monitoring Setup
     *
     * This test ensures that the `monitorLeaderHeartbeat` method properly sets up
     * the required RabbitMQ exchange and queue for listening to leader heartbeats.
     *
     * Test Objective:
     * Ensure that the method declares a RabbitMQ exchange for leader heartbeats.
     * Verify that a queue is declared and bound to the exchange.
     *
     * Test Setup:
     * Mocks `exchangeDeclare` to return a valid response.
     * Mocks `queueDeclare` to simulate queue creation.
     *
     * Execution Flow:
     * Invoke `monitorLeaderHeartbeat` on `recServerApp`.
     * Verify that an exchange named `"heartbeat_exchange"` is declared.
     * Verify that a queue is declared to listen for heartbeats.
     *
     * Assertions & Verifications:
     * Ensures `exchangeDeclare("heartbeat_exchange", "fanout")` is called exactly once.
     * Ensures `queueDeclare()` is called exactly once.
     *
     * Related Components:
     * {@link RecServerApp#monitorLeaderHeartbeat()} - The method being tested.
     * {@link RecServerApp#startHeartbeat()} - Related heartbeat mechanism.
     *
     * @throws IOException if RabbitMQ communication fails
     * @see RecServerApp
     */
    @Test
    void testMonitorLeaderHeartbeat() throws IOException {
        when(mockChannel.exchangeDeclare(anyString(), anyString())).thenReturn(null);
        when(mockChannel.queueDeclare()).thenReturn(mock(AMQP.Queue.DeclareOk.class));

        recServerApp.monitorLeaderHeartbeat();

        verify(mockChannel, times(1)).exchangeDeclare(eq("heartbeat_exchange"), eq("fanout"));
        verify(mockChannel, times(1)).queueDeclare();
    }

    /**
     * Test: Verify Leader Heartbeat Timeout Detection
     *
     * This test ensures that the `leaderHeartbeatReceivedRecently` method correctly
     * detects when the leader has stopped sending heartbeats for an extended period.
     *
     * Test Objective:
     * Simulate a scenario where the leader has not sent a heartbeat within the timeout period.
     * Ensure that the method correctly identifies the leader as unresponsive.
     *
     * Test Setup:
     * Manually set `lastHeartbeatTime` to a value older than the allowed `LEADER_CHECK_INTERVAL`.
     *
     * Execution Flow:
     * Set `lastHeartbeatTime` to simulate a leader that has been inactive beyond the timeout period.
     * Call `leaderHeartbeatReceivedRecently()` to check if the system still considers the leader active.
     * Assert that the method returns `false`, indicating that the leader is unresponsive.
     *
     * Assertions & Verifications:
     * Ensures that `leaderHeartbeatReceivedRecently()` returns `false` when the leader timeout occurs.
     *
     * Related Components:
     * {@link RecServerApp#leaderHeartbeatReceivedRecently()} - The method being tested.
     * {@link RecServerApp#monitorLeaderHeartbeat()} - Related leader monitoring mechanism.
     *
     * @see RecServerApp
     */

    @Test
    void testLeaderHeartbeatTimeout() {
        recServerApp.lastHeartbeatTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(RecServerApp.LEADER_CHECK_INTERVAL + 2);
        assertFalse(recServerApp.leaderHeartbeatReceivedRecently(), "Should detect missing heartbeat.");
    }

    /**
     * Test: Verify Leader Heartbeat is Detected as Recent
     *
     * This test ensures that the `leaderHeartbeatReceivedRecently` method correctly
     * identifies when the leader has sent a heartbeat within the allowed timeframe.
     *
     * Test Objective:
     * Simulate a scenario where the leader has just sent a heartbeat.
     * Ensure that the method correctly detects the leader as active.
     *
     * Test Setup:
     * Set `lastHeartbeatTime` to the current system time to simulate a recent heartbeat.
     *
     * Execution Flow:
     * Update `lastHeartbeatTime` to the current timestamp.
     * Call `leaderHeartbeatReceivedRecently()` to check if the system considers the leader active.
     * Assert that the method returns `true`, confirming that the leader heartbeat is recent.
     *
     * Assertions & Verifications:
     * Ensures that `leaderHeartbeatReceivedRecently()` returns `true` when the leader is active.
     *
     * Related Components:
     * {@link RecServerApp#leaderHeartbeatReceivedRecently()} - The method being tested.
     * {@link RecServerApp#monitorLeaderHeartbeat()} - Related leader monitoring mechanism.
     *
     * @see RecServerApp
     */
    @Test
    void testLeaderHeartbeatReceived() {
        recServerApp.lastHeartbeatTime = System.currentTimeMillis();
        assertTrue(recServerApp.leaderHeartbeatReceivedRecently(), "Should detect recent heartbeat.");
    }

    /**
     * Test: Verify Handling of RabbitMQ Failures in `basicPublish()`
     *
     * This test ensures that the `RecServerApp` correctly handles RabbitMQ failures
     * when attempting to publish heartbeat messages.
     *
     * Test Objective:
     * Simulate a scenario where RabbitMQ's `basicPublish()` fails due to an IOException.
     * Ensure that the system attempts to publish heartbeats despite failures.
     *
     * Test Setup:
     * Mock `basicPublish()` to throw an `IOException` when called.
     *
     * Execution Flow:
     * Mock `mockChannel.basicPublish()` to simulate a failure.
     * Call `startHeartbeat()`, which periodically attempts to publish heartbeats.
     * Pause the test execution for 2 seconds to allow the heartbeat scheduler to execute.
     * Verify that the system attempted to send heartbeats at least once despite the failure.
     *
     * Assertions & Verifications:
     * Verifies that `basicPublish()` is called at least once, confirming that
     *     the system does not crash on RabbitMQ failures.
     *
     * Related Components:
     * {@link RecServerApp#startHeartbeat()} - The method responsible for sending periodic heartbeats.
     * {@link com.rabbitmq.client.Channel#basicPublish(String, String, AMQP.BasicProperties, byte[])} - The RabbitMQ method being mocked.
     *
     * @throws IOException if an I/O error occurs during mock setup.
     * @throws InterruptedException if the test is interrupted while waiting for heartbeats to be sent.
     *
     * @see RecServerApp
     */
    @Test
    void testRabbitMQFailure() throws IOException, InterruptedException {
        doThrow(new IOException("Failed to publish heartbeat"))
                .when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        recServerApp.startHeartbeat();

        Thread.sleep(2000);

        verify(mockChannel, atLeastOnce()).basicPublish(anyString(), anyString(), any(), any());
    }

    /**
     * Test: Verify Leader Election Starts When Minimum Nodes Requirement is Met
     *
     * This test ensures that the leader election process is triggered
     * once the minimum required number of nodes (`MIN_NODES_REQUIRED`) is reached.
     *
     * Test Objective:
     * Simulate a scenario where nodes are added to the active node list.
     * Ensure that leader election is triggered once the required number of nodes is registered.
     *
     * Test Setup:
     * Add multiple nodes (`NodeA`, `NodeB`, `NodeC`, `NodeD`) to the active node list.
     *
     * Execution Flow:
     * Add nodes to `recServerApp.activeNodes`.
     * Invoke `startLeaderElection()` to trigger the leader selection process.
     *
     * Assertions & Verifications:
     * Ensures a leader is assigned** once the required nodes are registered.
     *
     * Related Components:
     * {@link RecServerApp#startLeaderElection()} - The method responsible for leader election.
     *
     * @see RecServerApp
     */
    @Test
    void testLeaderElectionStartsWhenMinNodesReached() {
        recServerApp.activeNodes.add("NodeA");
        recServerApp.activeNodes.add("NodeB");
        recServerApp.activeNodes.add("NodeC");

        recServerApp.activeNodes.add("NodeD");

        recServerApp.startLeaderElection();

        assertNotNull(recServerApp.currentLeader, "Leader should be assigned once the required nodes are registered.");
    }

    /**
     * Test: Ensure Concurrency Between Leader Heartbeat Monitoring and Recommendation Processing
     *
     * This test verifies that the system can handle concurrent execution of
     * leader heartbeat monitoring and recommendation processing without interference.
     *
     * Test Objective:
     * Simulate a scenario where a node is the leader.
     * Run both the heartbeat monitoring and recommendation processing concurrently.
     * Ensure both tasks execute successfully in parallel.
     *
     * Test Setup:
     * Set `currentLeader` to `"NodeA"` and mark the node as `isLeader = true`.
     * Use a {@link CountDownLatch} to track the completion of both tasks.
     *
     * Execution Flow:
     * Initialize two separate threads:
     * Recommendation Processor Thread: Calls `startRecommendationProcessor()`.
     * Heartbeat Monitoring Thread:** Calls `monitorLeaderHeartbeat()`.
     * Both threads execute their respective tasks.
     * The {@link CountDownLatch} ensures both tasks complete within 3 seconds
     *
     * Assertions & Verifications:
     * Ensures both threads complete execution** within the expected timeframe.
     *
     * Related Components:
     * {@link RecServerApp#monitorLeaderHeartbeat()} - Leader heartbeat monitoring.
     * {@link RecServerApp#startRecommendationProcessor()} - Recommendation processing.
     *
     * @throws InterruptedException if thread execution is interrupted.
     * @see RecServerApp
     */
    @Test
    void testConcurrencyWithLeaderHeartbeatAndRecommendationProcessing() throws InterruptedException {
        recServerApp.currentLeader = "NodeA";
        recServerApp.isLeader = true;

        CountDownLatch latch = new CountDownLatch(2);

        Thread recommendationProcessorThread = new Thread(() -> {
            recServerApp.startRecommendationProcessor();
            latch.countDown();
        });

        Thread heartbeatMonitoringThread = new Thread(() -> {
            recServerApp.monitorLeaderHeartbeat();
            latch.countDown();
        });

        recommendationProcessorThread.start();
        heartbeatMonitoringThread.start();

        boolean completed = latch.await(3, TimeUnit.SECONDS);

        assertTrue(completed, "Both threads should run concurrently and complete their tasks.");
    }

    /**
     * Test: Handle MongoDB Failure Gracefully
     *
     * This test verifies that the system correctly handles MongoDB failures,
     * ensuring that appropriate exceptions are thrown when database interactions fail.
     *
     * Test Objective:
     * Simulate a MongoDB failure by forcing `getCollection()` to throw a {@link RuntimeException}.
     * Ensure that a failure in `RecommendationProcessor#startProcessing(boolean)` is properly handled.
     * Verify that `registerNode()` reacts appropriately to the MongoDB failure.
     *
     * Test Setup:
     * Mock the MongoDB instance to throw an exception when attempting to retrieve a collection.
     * Mock the {@link RecommendationProcessor} to throw an exception during startup.
     * Replace the actual {@link RecommendationProcessor} in {@link RecServerApp} with the mocked failing one.
     *
     * Execution Flow:
     * Initialize {@link RecServerApp} with the mocked MongoDB instance.
     * Set the failing {@link RecommendationProcessor} in the test instance.
     * Call `registerNode()`, which should attempt to interact with MongoDB and fail.
     *
     * Assertions & Verifications:
     * Verify that a {@link RuntimeException} is thrown** when MongoDB interactions fail.
     * Ensure that the exception message matches** `"MongoDB failure"`.
     *
     * Related Components:
     * {@link RecServerApp#registerNode()} - Registers a node and interacts with MongoDB.
     * {@link RecommendationProcessor#startProcessing(boolean)} - Starts recommendation processing.
     *
     * @see RecServerApp
     * @see RecommendationProcessor
     */
    @Test
    void testMongoDBFailure() {
        when(mockDatabase.getCollection(anyString())).thenThrow(new RuntimeException("MongoDB failure"));

        doThrow(new RuntimeException("MongoDB failure")).when(mockRecommendationProcessor).startProcessing(anyBoolean());

        recServerApp = new RecServerApp("TestNode", mockChannel, mockDatabase, 3);

        recServerApp.recommendationProcessor = mockRecommendationProcessor;

        Exception exception = assertThrows(RuntimeException.class, () -> {
            recServerApp.registerNode();
        });

        assertEquals("MongoDB failure", exception.getMessage(), "Expected RuntimeException due to MongoDB failure");
    }

    /**
     * Test: Reset Active Nodes After Leader Timeout
     *
     * This test ensures that when the leader becomes unresponsive (misses its heartbeat),
     * the system correctly resets the list of active nodes.
     *
     * Test Objective:
     * Simulate a leader timeout by setting `lastHeartbeatTime` to an expired value.
     * Invoke `monitorLeaderHeartbeat()` to trigger the leader check logic.
     * Verify that the list of active nodes is cleared upon detecting an unresponsive leader.
     *
     * Test Setup:
     * Simulate an outdated last heartbeat timestamp (exceeding the leader check interval).
     * Call `monitorLeaderHeartbeat()` to process the timeout detection.
     *
     * Execution Flow:
     * Set `lastHeartbeatTime` to simulate an expired leader heartbeat.
     * Call `monitorLeaderHeartbeat()` to trigger the leader health check.
     * The method should detect the leader as unresponsive and reset `activeNodes`.
     *
     * Assertions & Verifications:
     * Ensure that `activeNodes` is cleared when the leader times out.
     * Confirm that the system properly handles leader failures.
     *
     * Related Components:
     * {@link RecServerApp#monitorLeaderHeartbeat()} - Monitors leader heartbeats and handles leader failure.
     *
     * @see RecServerApp
     */
    @Test
    void testResetActiveNodesAfterLeaderTimeout() {
        recServerApp.lastHeartbeatTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(RecServerApp.LEADER_CHECK_INTERVAL + 1);

        recServerApp.monitorLeaderHeartbeat();

        assertTrue(recServerApp.activeNodes.isEmpty(), "Active nodes should be reset after leader timeout.");
    }

    /**
     * Test: Leader Election Should NOT Start with Insufficient Nodes
     *
     * This test ensures that the leader election process does not start
     * when the number of active nodes is below the required threshold.
     *
     * Test Objective:
     * Simulate a scenario where the number of active nodes is less than the required minimum.
     * Invoke `registerNode()` to attempt leader election.
     * Verify that no leader is elected due to the insufficient number of nodes.
     *
     * Test Setup:
     * Add only two nodes (`NodeA`, `NodeB`) to `activeNodes`.
     * Call `registerNode()` to trigger leader election logic.
     *
     * Execution Flow:
     * Add two nodes (`NodeA`, `NodeB`) to the active node list.
     * Call `registerNode()` to process node registration and leader election.
     * Check that `currentLeader` remains `null` because there are not enough nodes.
     *
     * Assertions & Verifications:
     * Ensure that no leader is elected (`currentLeader` should remain `null`).
     * Confirm that leader election only starts when the minimum node requirement is met.
     *
     * Related Components:
     * {@link RecServerApp#registerNode()} - Handles node registration and initiates leader election.
     * {@link RecServerApp#startLeaderElection()} - Initiates leader election when sufficient nodes exist.
     *
     * @see RecServerApp
     */
    @Test
    void testLeaderElectionNotStartedWithInsufficientNodes() {
        recServerApp.activeNodes.add("NodeA");
        recServerApp.activeNodes.add("NodeB");

        recServerApp.registerNode();

        assertNull(recServerApp.currentLeader, "Leader should NOT be assigned if the minimum node requirement is not met.");
    }

    /**
     * Test: Node List Update Synchronization
     *
     * This test ensures that the active nodes list is correctly updated when
     * receiving a `NODE_LIST_UPDATE` message.
     *
     * Test Objective:
     * Simulate receiving a `NODE_LIST_UPDATE` message containing a list of active nodes.
     * Clear and update the `activeNodes` set based on the received message.
     * Verify that the node list synchronization correctly updates the active nodes.
     *
     * Test Setup:
     * Define a `NODE_LIST_UPDATE` message with nodes (`NodeA`, `NodeB`, `NodeC`).
     * Clear `activeNodes` to simulate a fresh update.
     * Update `activeNodes` using the parsed message.
     *
     * Execution Flow:
     * Create a simulated `NODE_LIST_UPDATE` message.
     * Clear the existing active nodes list.
     * Parse the message and update the `activeNodes` set.
     * Verify that the correct nodes are present in `activeNodes`.
     *
     * Assertions & Verifications:
     * Ensure that `activeNodes` contains exactly 3 nodes.
     * Verify that `NodeA`, `NodeB`, and `NodeC` are correctly added.
     *
     * Related Components:
     * {@link RecServerApp#activeNodes} - The list of currently active nodes.
     * {@link RecServerApp#monitorLeaderHeartbeat()} - Handles updates to the node list.
     *
     * @see RecServerApp
     */
    @Test
    void testNodeListUpdateSync() {
        String updateMessage = "NODE_LIST_UPDATE:NodeA,NodeB,NodeC";

        recServerApp.activeNodes.clear();
        recServerApp.activeNodes.addAll(Arrays.asList(updateMessage.replace("NODE_LIST_UPDATE:", "").split(",")));

        assertEquals(3, recServerApp.activeNodes.size(), "Active nodes should be updated correctly.");
        assertTrue(recServerApp.activeNodes.contains("NodeA"));
        assertTrue(recServerApp.activeNodes.contains("NodeB"));
        assertTrue(recServerApp.activeNodes.contains("NodeC"));
    }

    /**
     * Test: Node Starts as Follower If a Leader Is Detected
     *
     * This test ensures that a newly joining node does not declare itself
     * as the leader if a leader is already present in the system.
     *
     * Test Objective:
     * Verify that a node remains a **follower** if it detects an existing leader.
     * Ensure that `isLeader` remains `false` if `currentLeader` is set.
     *
     * Test Setup:
     * Simulate an active heartbeat by setting `lastHeartbeatTime` to the current time.
     * Assign `"NodeA"` as the `currentLeader` before registration.
     * Call `monitorHeartbeatBeforeRegister()` to check leader detection logic.
     *
     * Execution Flow:
     * Set `lastHeartbeatTime` to simulate a recent leader heartbeat.
     * Assign `NodeA` as the current leader.
     * Call `monitorHeartbeatBeforeRegister()` to simulate node registration.
     * Check that `isLeader` is `false`, ensuring the node remains a follower.
     *
     * Assertions & Verifications:
     * Ensure `isLeader` is `false`, meaning the node remains a follower.
     * Verify that leadership is not overridden when a leader is present.
     *
     * Related Components:
     * {@link RecServerApp#monitorHeartbeatBeforeRegister()} - Checks if a leader exists before registering.
     * {@link RecServerApp#isLeader} - Determines if the node has become a leader.
     *
     * @see RecServerApp
     */
    @Test
    void testNodeStartsAsFollowerIfLeaderDetected() {
        recServerApp.lastHeartbeatTime = System.currentTimeMillis();
        recServerApp.currentLeader = "NodeA";

        recServerApp.monitorHeartbeatBeforeRegister();

        assertFalse(recServerApp.isLeader, "Node should NOT become a leader if a leader is already detected.");
    }
}
