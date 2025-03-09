package com.mulligan.common;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mulligan.common.Units.RabbitMQSender;
import com.mulligan.common.models.Citation;
import com.mulligan.common.models.Transaction;

/**
 * Unit tests for the RabbitMQSender class.
 * This test class validates the functionality of the RabbitMQSender, ensuring its reliability and correctness
 * in handling RabbitMQ-based communication. It covers the following aspects:
 * Sending and receiving messages for transactions and citations, including proper use of correlation IDs and queues.
 * Parsing valid and malformed responses from RabbitMQ into Transaction and Citation objects.
 * Handling edge cases such as empty responses, malformed data, and partially valid data.
 * Timeout scenarios when no response is received from RabbitMQ within the expected period.
 * Mocking and isolation are used extensively to simulate RabbitMQ behavior, ensuring tests are independent of external systems.
 * @version 1.8.0
 * @author Jamal Majadle
 */
class RabbitMQSenderTest {

    private RabbitMQSender rabbitMQSender;
    private Channel mockChannel;

    /**
     * Sets up the test environment for each test case by initializing the necessary mocks and injecting them into the
     * `RabbitMQSender` instance.
     * This method is annotated with `@BeforeEach` to ensure that it runs before each test case, creating a fresh and isolated
     * setup for consistent test results.
     * Setup Steps:
     * Mocks the `Channel` instance that interacts with RabbitMQ.
     * Injects the mocked `Channel` into a new instance of `RabbitMQSender` to isolate the component under test.
     * Mimics the behavior of the `queueDeclare` method on the mocked `Channel` by returning a mocked
     *       `AMQP.Queue.DeclareOk` object with a predefined queue name (`mock-reply-queue`).
     * This setup ensures that:
     * RabbitMQSender methods can operate without a real connection to RabbitMQ.
     * Mocked responses are used to simulate RabbitMQ interactions.
     * The test cases focus solely on the logic within `RabbitMQSender` without external dependencies.
     * @throws IOException      If an I/O error occurs during setup (unlikely in a mocked environment).
     */
    @BeforeEach
    void setUp() throws IOException {
        mockChannel = mock(Channel.class);

        rabbitMQSender = new RabbitMQSender(mockChannel);

        AMQP.Queue.DeclareOk declareOkMock = mock(AMQP.Queue.DeclareOk.class);
        when(declareOkMock.getQueue()).thenReturn("mock-reply-queue");
        when(mockChannel.queueDeclare()).thenReturn(declareOkMock);
    }

    /**
     * Tests the `sendTransaction` method to ensure it sends a transaction message to the
     * appropriate RabbitMQ queue and correctly receives the response.
     * This test uses mocked RabbitMQ interactions to simulate the message publishing
     * and consumption behavior of the `sendTransaction` method. The test verifies:
     * That the method publishes the message to the correct queue with the expected correlation ID.
     * That the method receives the correct response from the RabbitMQ mock.
     * That the response matches the expected result.
     *
     * @throws IOException          If an I/O error occurs during RabbitMQ communication.
     * @throws InterruptedException If the thread is interrupted while waiting for a response.
     * @throws TimeoutException     If no response is received within the defined timeout period.
     */
    @Test
    void testSendTransaction() throws IOException, InterruptedException, TimeoutException {
        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        final String[] generatedCorrelationId = new String[1];

        doAnswer(invocation -> {
            AMQP.BasicProperties props = invocation.getArgument(2);
            generatedCorrelationId[0] = props.getCorrelationId();
            return null;
        }).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        doAnswer(invocation -> {
            String queueName = invocation.getArgument(0);
            DeliverCallback deliverCallback = invocation.getArgument(2);

            AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(generatedCorrelationId[0])
                    .build();

            Delivery delivery = new Delivery(null, mockProperties, "TRANSACTION_RESPONSE".getBytes(StandardCharsets.UTF_8));
            deliverCallback.handle(queueName, delivery);

            return "mock-consumer-tag";
        }).when(mockChannel).basicConsume(anyString(), eq(true), any(DeliverCallback.class), any(CancelCallback.class));

        String result = rabbitMQSender.sendTransaction("SampleMessage");

        assertNotNull(result);
        assertEquals("TRANSACTION_RESPONSE", result);
        verify(mockChannel, times(1)).basicPublish(anyString(), eq("transactions_queue"), any(), any());
    }

    /**
     * Tests the `requestTransactions` method to ensure it correctly requests and parses
     * transaction data from RabbitMQ.
     * This test uses mocked RabbitMQ interactions to simulate message publishing and
     * consumption behavior of the `requestTransactions` method. The test verifies:
     * That the method sends a message to the correct queue with the expected correlation ID.
     * That the method correctly receives and parses the response into a list of `Transaction` objects.
     * That the resulting list contains the expected number of transactions with the correct details.
     *
     * @throws IOException          If an I/O error occurs during RabbitMQ communication.
     * @throws InterruptedException If the thread is interrupted while waiting for a response.
     * @throws TimeoutException     If no response is received within the defined timeout period.
     */
    @Test
    void testRequestTransactions() throws IOException, InterruptedException, TimeoutException {
        final String[] capturedCorrelationId = new String[1];

        doAnswer(invocation -> {
            AMQP.BasicProperties props = invocation.getArgument(2);
            capturedCorrelationId[0] = props.getCorrelationId();
            return null;
        }).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        doAnswer(invocation -> {
            String queueName = invocation.getArgument(0);
            DeliverCallback deliverCallback = invocation.getArgument(2);

            String mockResponse = "ABC123;Space1;Zone1;2024-11-01T10:00;null;15.0|DEF456;Space2;Zone2;2024-11-02T10:30;null;20.0";

            AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(capturedCorrelationId[0]) // Ensure correlation ID matches
                    .build();

            Delivery delivery = new Delivery(null, mockProperties, mockResponse.getBytes(StandardCharsets.UTF_8));
            deliverCallback.handle(queueName, delivery);

            return "mock-consumer-tag";
        }).when(mockChannel).basicConsume(anyString(), eq(true), any(DeliverCallback.class), any(CancelCallback.class));

        List<Transaction> transactions = rabbitMQSender.requestTransactions();

        assertNotNull(transactions, "Transactions list should not be null.");
        assertEquals(2, transactions.size(), "There should be exactly 2 transactions.");

        Transaction firstTransaction = transactions.get(0);
        assertEquals("ABC123", firstTransaction.getLicensePlate(), "License plate of the first transaction is incorrect.");
        assertEquals("Space1", firstTransaction.getParkingSpace(), "Parking space of the first transaction is incorrect.");
        assertEquals(15.0, firstTransaction.getAmount(), "Amount of the first transaction is incorrect.");

        Transaction secondTransaction = transactions.get(1);
        assertEquals("DEF456", secondTransaction.getLicensePlate(), "License plate of the second transaction is incorrect.");
        assertEquals("Space2", secondTransaction.getParkingSpace(), "Parking space of the second transaction is incorrect.");
        assertEquals(20.0, secondTransaction.getAmount(), "Amount of the second transaction is incorrect.");
    }

    /**
     * Tests the `sendCitation` method to ensure it correctly sends a citation-related
     * message to RabbitMQ and receives the expected response.
     * This test uses mocked RabbitMQ interactions to simulate message publishing and
     * consumption behavior. The test verifies:
     * That the message is published to the correct queue (`citations_queue`).
     * That the correlation ID is properly generated and matched in the response.
     * That the method correctly handles the response from RabbitMQ and returns the expected result.
     *
     * @throws IOException          If an I/O error occurs during RabbitMQ communication.
     * @throws InterruptedException If the thread is interrupted while waiting for a response.
     * @throws TimeoutException     If no response is received within the defined timeout period.
     */
    @Test
    void testSendCitation() throws IOException, InterruptedException, TimeoutException {
        doNothing().when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        final String[] generatedCorrelationId = new String[1];

        doAnswer(invocation -> {
            AMQP.BasicProperties props = invocation.getArgument(2);
            generatedCorrelationId[0] = props.getCorrelationId();
            return null;
        }).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        doAnswer(invocation -> {
            String queueName = invocation.getArgument(0);
            DeliverCallback deliverCallback = invocation.getArgument(2);

            AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(generatedCorrelationId[0])
                    .build();

            Delivery delivery = new Delivery(null, mockProperties, "CITATION_RESPONSE".getBytes(StandardCharsets.UTF_8));
            deliverCallback.handle(queueName, delivery);

            return "mock-consumer-tag";
        }).when(mockChannel).basicConsume(anyString(), eq(true), any(DeliverCallback.class), any(CancelCallback.class));

        String result = rabbitMQSender.sendCitation("SampleCitationMessage");

        assertNotNull(result);
        assertEquals("CITATION_RESPONSE", result);
        verify(mockChannel, times(1)).basicPublish(anyString(), eq("citations_queue"), any(), any());
    }


    /**
     * Tests the `requestCitations` method to ensure it correctly parses a valid
     * response string into a list of `Citation` objects.
     * This test uses mocked RabbitMQ interactions to simulate the behavior of
     * the `requestCitations` method, including capturing the correlation ID and
     * providing a mocked response with multiple valid citations.
     *
     * @throws IOException          If an I/O error occurs during RabbitMQ communication.
     * @throws InterruptedException If the thread is interrupted while waiting for a response.
     * @throws TimeoutException     If no response is received within the defined timeout period.
     */
    @Test
    void testRequestCitations() throws IOException, InterruptedException, TimeoutException {
        final String[] capturedCorrelationId = new String[1];

        doAnswer(invocation -> {
            AMQP.BasicProperties props = invocation.getArgument(2);
            capturedCorrelationId[0] = props.getCorrelationId();
            return null;
        }).when(mockChannel).basicPublish(anyString(), anyString(), any(), any());

        doAnswer(invocation -> {
            String queueName = invocation.getArgument(0);
            DeliverCallback deliverCallback = invocation.getArgument(2);

            String mockResponse = "ABC123;Space1;Zone1;2024-11-01T10:00;50.0;Overdue Payment|"
                    + "DEF456;Space2;Zone2;2024-11-02T10:30;75.0;Illegal Parking";

            AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                    .correlationId(capturedCorrelationId[0])
                    .build();

            Delivery delivery = new Delivery(null, mockProperties, mockResponse.getBytes(StandardCharsets.UTF_8));
            deliverCallback.handle(queueName, delivery);

            return "mock-consumer-tag";
        }).when(mockChannel).basicConsume(anyString(), eq(true), any(DeliverCallback.class), any(CancelCallback.class));

        List<Citation> citations = rabbitMQSender.requestCitations();

        assertNotNull(citations, "Citations list should not be null");
        assertEquals(2, citations.size(), "Citations list size should match the response");

        Citation firstCitation = citations.get(0);
        assertEquals("ABC123", firstCitation.getLicensePlate(), "License plate of the first citation should match");
        assertEquals("Space1", firstCitation.getParkingSpace(), "Parking space of the first citation should match");
        assertEquals("Zone1", firstCitation.getZoneName(), "Zone of the first citation should match");
        assertEquals("2024-11-01T10:00", firstCitation.getIssueTime(), "Issue time of the first citation should match");
        assertEquals(50.0, firstCitation.getAmount(), 0.01, "Amount of the first citation should match");
        assertEquals("Overdue Payment", firstCitation.getReason(), "Reason of the first citation should match");

        Citation secondCitation = citations.get(1);
        assertEquals("DEF456", secondCitation.getLicensePlate(), "License plate of the second citation should match");
        assertEquals("Space2", secondCitation.getParkingSpace(), "Parking space of the second citation should match");
        assertEquals("Zone2", secondCitation.getZoneName(), "Zone of the second citation should match");
        assertEquals("2024-11-02T10:30", secondCitation.getIssueTime(), "Issue time of the second citation should match");
        assertEquals(75.0, secondCitation.getAmount(), 0.01, "Amount of the second citation should match");
        assertEquals("Illegal Parking", secondCitation.getReason(), "Reason of the second citation should match");
    }

    /**
     * Tests the `parseCitations` method to ensure it correctly handles various edge cases
     * in parsing citation responses.
     * This test validates the following scenarios:
     * Empty response: The result should be an empty list.
     * Malformed response: The result should be an empty list for invalid data.
     * Partially valid response: Only valid citations should be parsed and included in the result.
     * Response with extra whitespace: Whitespace around the data should not affect parsing.
     * The test also performs detailed checks on the fields of parsed citations to ensure
     * their correctness.
     */
    @Test
    void testParseCitationsEdgeCases() {
        String emptyResponse = "";
        List<Citation> emptyCitations = rabbitMQSender.parseCitations(emptyResponse);
        assertNotNull(emptyCitations, "The result should not be null for an empty response.");
        assertEquals(0, emptyCitations.size(), "The result should be an empty list for an empty response.");

        String malformedResponse = "INVALID;DATA";
        List<Citation> malformedCitations = rabbitMQSender.parseCitations(malformedResponse);
        assertNotNull(malformedCitations, "The result should not be null for a malformed response.");
        assertEquals(0, malformedCitations.size(), "The result should be an empty list for a malformed response.");

        String partiallyValidResponse = "ABC123;Space1;Zone1;2024-11-01T10:00;50.0|INVALID;DATA";
        List<Citation> partiallyValidCitations = rabbitMQSender.parseCitations(partiallyValidResponse);
        assertNotNull(partiallyValidCitations, "The result should not be null for a partially valid response.");
        assertEquals(1, partiallyValidCitations.size(), "One valid citation should be parsed from the partially valid response.");

        Citation validCitation = partiallyValidCitations.get(0);
        assertEquals("ABC123", validCitation.getLicensePlate(), "The license plate of the first citation should match.");
        assertEquals("Space1", validCitation.getParkingSpace(), "The parking space of the first citation should match.");
        assertEquals("Zone1", validCitation.getZoneName(), "The zone name of the first citation should match.");
        assertEquals("2024-11-01T10:00", validCitation.getIssueTime(), "The issue time of the first citation should match.");
        assertEquals(50.0, validCitation.getAmount(), 0.01, "The amount of the first citation should match.");

        String responseWithWhitespace = "  ABC123;Space1;Zone1;2024-11-01T10:00;50.0  |  DEF456;Space2;Zone2;2024-11-02T10:30;75.0  ";
        List<Citation> citationsWithWhitespace = rabbitMQSender.parseCitations(responseWithWhitespace);
        assertNotNull(citationsWithWhitespace, "The result should not be null for a valid response with extra whitespace.");
        assertEquals(2, citationsWithWhitespace.size(), "Two citations should be parsed from the response.");

        Citation secondCitation = citationsWithWhitespace.get(1);
        assertEquals("DEF456", secondCitation.getLicensePlate(), "The license plate of the second citation should match.");
        assertEquals("Space2", secondCitation.getParkingSpace(), "The parking space of the second citation should match.");
        assertEquals("Zone2", secondCitation.getZoneName(), "The zone name of the second citation should match.");
        assertEquals("2024-11-02T10:30", secondCitation.getIssueTime(), "The issue time of the second citation should match.");
        assertEquals(75.0, secondCitation.getAmount(), 0.01, "The amount of the second citation should match.");
    }

    /**
     * Tests the `sendAndReceive` method to ensure it correctly handles scenarios
     * where no response is received within the specified timeout period.
     * This test validates:
     * A `TimeoutException` is thrown when the response is not received within the expected time.
     * The exception message matches the expected message: "No response received within timeout period".
     * No unexpected exceptions are thrown during the process.
     * The test simulates a timeout scenario by invoking `sendTransaction` with a
     * test message and ensuring the appropriate exception is raised.
     */
    @Test
    void testSendAndReceiveTimeout() {
        try {
            rabbitMQSender.sendTransaction("TimeoutTest");
            fail("Expected TimeoutException was not thrown.");
        } catch (TimeoutException e) {
            assertEquals("No response received within timeout period", e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Tests edge cases for parsing transaction responses in the `parseTransactions` method.
     * This test ensures that the `parseTransactions` method behaves correctly under various scenarios:
     * Empty response: Validates that an empty response returns an empty list without errors.
     * Malformed response: Ensures a malformed response returns an empty list without exceptions.
     * Partially valid response: Confirms that partially valid responses correctly parse valid transactions
     *       and ignore invalid data.
     * Valid response with additional whitespace: Verifies that extra whitespace in the response is handled
     *       gracefully and does not affect parsing accuracy.
     * The test cases include assertions to check:
     * Correct handling of null and malformed inputs.
     * Proper parsing of valid transactions in partially valid responses.
     * Accurate parsing of transactions in responses with additional whitespace.
     * Each case validates the correctness of parsed `Transaction` objects and their attributes, including
     * parking space, zone, and other relevant fields.
     */
    @Test
    void testParseTransactionsEdgeCases() {
        String emptyResponse = "";
        List<Transaction> emptyTransactions = rabbitMQSender.parseTransactions(emptyResponse);
        assertNotNull(emptyTransactions, "Empty response should not return null");
        assertEquals(0, emptyTransactions.size(), "Empty response should return an empty list");

        String malformedResponse = "INVALID;DATA";
        List<Transaction> malformedTransactions = rabbitMQSender.parseTransactions(malformedResponse);
        assertNotNull(malformedTransactions, "Malformed response should not return null");
        assertEquals(0, malformedTransactions.size(), "Malformed response should return an empty list");

        String partiallyValidResponse = "1;Space1;Zone1;2024-11-01T10:00;2024-11-01T12:00;15.0|INVALID;DATA";
        List<Transaction> partiallyValidTransactions = rabbitMQSender.parseTransactions(partiallyValidResponse);
        assertNotNull(partiallyValidTransactions, "Partially valid response should not return null");
        assertEquals(1, partiallyValidTransactions.size(), "Partially valid response should return a list with one valid transaction");
        assertEquals("Space1", partiallyValidTransactions.get(0).getParkingSpace(), "Parsed transaction parking space is incorrect");
        assertEquals("Zone1", partiallyValidTransactions.get(0).getZoneName(), "Parsed transaction zone is incorrect");

        String responseWithWhitespace = "  1;Space1;Zone1;2024-11-01T10:00;2024-11-01T12:00;15.0  |  2;Space2;Zone2;2024-11-02T10:30;2024-11-02T13:30;20.0  ";
        List<Transaction> transactionsWithWhitespace = rabbitMQSender.parseTransactions(responseWithWhitespace);
        assertEquals(2, transactionsWithWhitespace.size(), "Valid response with whitespace should parse all transactions");
        assertEquals("Space1", transactionsWithWhitespace.get(0).getParkingSpace(), "Parsed transaction parking space (first) is incorrect");
        assertEquals("Space2", transactionsWithWhitespace.get(1).getParkingSpace(), "Parsed transaction parking space (second) is incorrect");
        assertEquals("Zone1", transactionsWithWhitespace.get(0).getZoneName(), "Parsed transaction zone (first) is incorrect");
        assertEquals("Zone2", transactionsWithWhitespace.get(1).getZoneName(), "Parsed transaction zone (second) is incorrect");
    }


    /**
     * Tests the `parseTransactions` method's ability to handle a mix of malformed and valid transaction data.
     * This test ensures that:
     * Malformed transaction entries in the response are ignored without throwing exceptions.
     * Valid transaction entries in the response are correctly parsed and included in the result.
     * Test Scenario:
     * Input contains one malformed entry ("BAD;DATA") and one valid transaction entry.
     * Validation Criteria:
     * The returned list of transactions is not null.
     * The list contains only the valid transaction entry.
     * All attributes of the valid transaction (e.g., license plate, parking space, zone name, start time,
     *       end time, and amount) are correctly parsed and match the expected values.
     * The end time of the valid transaction is correctly identified as null when not provided.
     * This test ensures the robustness of the `parseTransactions` method in handling mixed data responses.
     */
    @Test
    void testParseMalformedTransactionData() {
        String malformedResponse = "BAD;DATA|1;Space1;Zone1;2024-11-01T10:00;null;15.0";

        List<Transaction> transactions = rabbitMQSender.parseTransactions(malformedResponse);

        assertNotNull(transactions, "The list of transactions should not be null");
        assertEquals(1, transactions.size(), "Only one valid transaction should be parsed");

        Transaction transaction = transactions.get(0);
        assertEquals("1", transaction.getLicensePlate(), "License plate mismatch");
        assertEquals("Space1", transaction.getParkingSpace(), "Parking space mismatch");
        assertEquals("Zone1", transaction.getZoneName(), "Zone name mismatch");
        assertEquals("2024-11-01T10:00", transaction.getStart(), "Start time mismatch");
        assertNull(transaction.getEnd(), "End time should be null");
        assertEquals(15.0, transaction.getAmount(), "Transaction amount mismatch");
    }

    /**
     * Tests the {@link RabbitMQSender#loadParkingLists(String, String...)} method for handling edge cases.
     * Validates that the method correctly handles scenarios such as:
     * An invalid request type leading to an "INVALID_RESPONSE".
     * Returning an empty list for invalid input.
     * Ensuring the correct invocation of the {@code basicPublish} method.
     * Mocks the RabbitMQ {@code basicConsume} to simulate a response for an invalid request type.
     *
     * @throws Exception If any unexpected exception occurs during the test execution.
     */
    @Test
    void testLoadParkingListsEdgeCases() throws Exception {
        doAnswer(invocation -> {
            String queueName = invocation.getArgument(0);
            DeliverCallback deliverCallback = invocation.getArgument(2);

            AMQP.BasicProperties mockProperties = new AMQP.BasicProperties.Builder()
                    .correlationId("mock-correlation-id")
                    .build();

            String mockResponse = "INVALID_RESPONSE";
            Delivery delivery = new Delivery(null, mockProperties, mockResponse.getBytes(StandardCharsets.UTF_8));
            deliverCallback.handle(queueName, delivery);

            return "mock-consumer-tag";
        }).when(mockChannel).basicConsume(anyString(), eq(true), any(DeliverCallback.class), any(CancelCallback.class));

        List<String> parkingList = rabbitMQSender.loadParkingLists("INVALID_REQUEST_TYPE");

        assertNotNull(parkingList, "Parking list should not be null for invalid request.");
        assertEquals(0, parkingList.size(), "Parking list should be empty for invalid request.");

        verify(mockChannel, times(1)).basicPublish(eq(""), eq("transactions_queue"), any(), any());
    }

    /**
     * Tests the {@link RabbitMQSender#parseCitations(String)} method for handling unexpected data formats.
     * Validates that the method:
     * Handles an unexpected data format without throwing exceptions.
     * Returns an empty list when the response contains invalid or extra fields.
     */
    @Test
    void testParseCitationsUnexpectedData() {
        String unexpectedResponse = "INVALID|DATA|EXTRA_FIELDS";

        List<Citation> citations = rabbitMQSender.parseCitations(unexpectedResponse);

        assertNotNull(citations, "Citations list should not be null for unexpected data.");
        assertEquals(0, citations.size(), "Citations list should be empty for unexpected data.");
    }

    /**
     * Tests the {@link RabbitMQSender#sendTransaction(String)} method for null input.
     * Validates that the method:
     * Throws a {@link NullPointerException} when a null input is provided.
     * Includes a descriptive message indicating the null input issue.
     */
    @Test
    void testSendTransactionWithNullInput() {
        try {
            rabbitMQSender.sendTransaction(null);
            fail("Expected NullPointerException was not thrown.");
        } catch (NullPointerException e) {
            assertTrue(e.getMessage().contains("message"), "NullPointerException should indicate null input.");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * Tests the {@link RabbitMQSender#sendTransaction(String)} method for handling concurrent requests.
     * Validates that:
     * Multiple threads can call {@code sendTransaction} simultaneously without issues.
     * The {@link Channel#basicPublish(String, String, AMQP.BasicProperties, byte[])} method is invoked for each request.
     * This test ensures thread safety and proper handling of concurrent requests.
     *
     * @throws InterruptedException If the thread is interrupted while waiting for other threads to complete.
     * @throws IOException          If an I/O error occurs during request processing.
     */
    @Test
    void testConcurrentRequests() throws InterruptedException, IOException {
        Runnable task = () -> {
            try {
                rabbitMQSender.sendTransaction("ConcurrentMessage");
            } catch (Exception e) {
                fail("Unexpected exception in concurrent request: " + e.getMessage());
            }
        };

        Thread thread1 = new Thread(task);
        Thread thread2 = new Thread(task);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        verify(mockChannel, times(2)).basicPublish(anyString(), anyString(), any(), any());
    }

    /**
     * Tests the {@link RabbitMQSender#sendCitation(String)} method for handling timeout scenarios.
     * Validates that:
     * A {@link TimeoutException} is thrown when no response is received within the specified timeout period.
     * The exception message matches the expected timeout message.
     * This test ensures that the method correctly handles scenarios where a response is not received on time.
     */
    @Test
    void testSendCitationTimeout() {
        try {
            rabbitMQSender.sendCitation("TimeoutTest");
            fail("Expected TimeoutException was not thrown.");
        } catch (TimeoutException e) {
            assertEquals("No response received within timeout period", e.getMessage(), "TimeoutException message should match.");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

}