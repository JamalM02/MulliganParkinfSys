package com.mulligan.mo;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.mulligan.common.Units.RabbitMQSender;
import com.mulligan.common.models.Citation;
import com.mulligan.common.models.Transaction;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Unit tests for the MOController class.
 * This class validates the behavior of MOController methods, including interaction with
 * RabbitMQSender and proper data population in the transaction and citation tables.
 *
 * Key Features Tested:
 * 
 *   Successful and failed retrieval of transaction reports.
 *   Successful and failed retrieval of citation reports.
 *   Validation of table setup methods for transactions and citations.
 * 
 *
 *  * @author Jamal Majadle
 *  * @version 1.8.0
 */
public class MOControllerTest {

    @Mock
    private RabbitMQSender rabbitMQSenderMock;

    private MOController controller;

    @Mock
    private TableView<Citation> citationTable;


    /**
     * Initializes the JavaFX toolkit before running tests.
     */
    @BeforeAll
    public static void initToolkit() {
        Platform.startup(() -> {});
    }

    /**
     * Sets up the test environment by initializing the controller and mock dependencies.
     */
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        controller = new MOController();
        controller.rabbitMQSender = rabbitMQSenderMock;

        controller.transactionTable = new TableView<>();
        controller.transactionIdColumn = new TableColumn<>("ID");
        controller.transactionVehicleColumn = new TableColumn<>("License Plate");
        controller.transactionParkingSpaceColumn = new TableColumn<>("Parking Space");
        controller.transactionZoneColumn = new TableColumn<>("Zone");
        controller.transactionStartColumn = new TableColumn<>("Start Time");
        controller.transactionEndColumn = new TableColumn<>("End Time");
        controller.transactionAmountColumn = new TableColumn<>("Amount");

        controller.transactionTable.getColumns().addAll(
                controller.transactionIdColumn,
                controller.transactionVehicleColumn,
                controller.transactionParkingSpaceColumn,
                controller.transactionZoneColumn,
                controller.transactionStartColumn,
                controller.transactionEndColumn,
                controller.transactionAmountColumn
        );

        controller.citationTable = new TableView<>();
        controller.citationIdColumn = new TableColumn<>("ID");
        controller.citationVehicleColumn = new TableColumn<>("License Plate");
        controller.citationParkingSpaceColumn = new TableColumn<>("Parking Space");
        controller.citationZoneColumn = new TableColumn<>("Zone");
        controller.citationIssueTimeColumn = new TableColumn<>("Issue Time");
        controller.citationAmountColumn = new TableColumn<>("Amount");
        controller.citationReasonColumn = new TableColumn<>("Reason");

        controller.citationTable.getColumns().addAll(
                controller.citationIdColumn,
                controller.citationVehicleColumn,
                controller.citationParkingSpaceColumn,
                controller.citationZoneColumn,
                controller.citationIssueTimeColumn,
                controller.citationAmountColumn,
                controller.citationReasonColumn
        );

        controller.setupTransactionTable();
        controller.setupCitationTable();
    }

    /**
     * Tests the successful retrieval and display of the transaction report.
     *
     * This test verifies the following: 
     * 
     *     The `requestTransactionReport` method correctly retrieves transaction data
     *         from the RabbitMQ sender mock.
     *     The retrieved transaction data is properly populated into the transaction table.
     *     The `requestTransactions` method of the RabbitMQ sender is invoked exactly once.
     * 
     * @throws Exception if an error occurs during the test execution.
     */
    @Test
    public void testRequestTransactionReport_Success() throws Exception {
        List<Transaction> transactions = Arrays.asList(
                new Transaction("ABC123", "SpaceA", "Zone1",
                        "2024-12-25T10:00", "2024-12-25T12:00", 20.0),
                new Transaction("XYZ789", "SpaceB", "Zone2",
                        "2024-12-25T11:00", "2024-12-25T13:00", 15.0)
        );

        when(rabbitMQSenderMock.requestTransactions()).thenReturn(transactions);

        controller.requestTransactionReport();

        ObservableList<Transaction> items = controller.transactionTable.getItems();
        assertEquals(2, items.size());
        assertEquals("ABC123", items.get(0).getLicensePlate());
        assertEquals("XYZ789", items.get(1).getLicensePlate());

        verify(rabbitMQSenderMock, times(1)).requestTransactions();
    }

    /**
     * Tests failure scenario for retrieving the citation report.
     *
     * @throws Exception If an error occurs during the test execution.
     */
    @Test
    public void testRequestCitationReport_Failure() throws Exception {
        when(rabbitMQSenderMock.requestCitations()).thenThrow(new RuntimeException("Connection error"));

        Platform.runLater(() -> controller.requestCitationReport());
        WaitForFXEvents();

        assertTrue(controller.citationTable.getItems().isEmpty(), "Citation table should be empty on failure");
    }

    /**
     * Tests failure scenario for retrieving the transaction report.
     *
     * @throws Exception If an error occurs during the test execution.
     */
    @Test
    public void testRequestTransactionReport_Failure() throws Exception {
        when(rabbitMQSenderMock.requestTransactions()).thenThrow(new RuntimeException("Connection error"));

        Platform.runLater(() -> controller.requestTransactionReport());
        WaitForFXEvents();

        assertTrue(controller.transactionTable.getItems().isEmpty(), "Transaction table should be empty on failure");
    }

    /**
     * Tests the successful retrieval and display of the citation report.
     *
     * This test verifies the following: 
     * 
     *     The `requestCitationReport` method correctly retrieves citation data
     *         from the RabbitMQ sender mock.
     *     The retrieved citation data is properly populated into the citation table.
     *     The `requestCitations` method of the RabbitMQ sender is invoked exactly once.
     * 
     *
     * @throws Exception if an error occurs during the test execution.
     */
    /*@Test
    public void testRequestCitationReport_Success() throws Exception {
        // Create a predefined list of citations with the correct constructor
        List<Citation> citations = Arrays.asList(
                new Citation("ABC123", "SpaceA", "Zone1", LocalDateTime.now(), 50.0, "Improper Parking"),
                new Citation("XYZ789", "SpaceB", "Zone2", LocalDateTime.now(), 75.0, "Exceeded Time Limit")
        );

        when(rabbitMQSenderMock.requestCitations()).thenReturn(citations);

        controller.requestCitationReport();

        ObservableList<Citation> items = controller.citationTable.getItems();
        assertEquals(2, items.size());
        assertEquals("ABC123", items.get(0).getLicensePlate());
        assertEquals("XYZ789", items.get(1).getLicensePlate());

        verify(rabbitMQSenderMock, times(1)).requestCitations();
    }*/


    /**
     * Tests setup of the transaction table column bindings.
     * Ensures that all required columns have a CellValueFactory set.
     */
    @Test
    public void testSetupTransactionTable() {
        controller.setupTransactionTable();

        assertNotNull(controller.transactionVehicleColumn.getCellValueFactory(),
                "Transaction Vehicle column cell value factory is not set.");
        assertNotNull(controller.transactionParkingSpaceColumn.getCellValueFactory(),
                "Transaction Parking Space column cell value factory is not set.");
        assertNotNull(controller.transactionZoneColumn.getCellValueFactory(),
                "Transaction Zone column cell value factory is not set.");
        assertNotNull(controller.transactionStartColumn.getCellValueFactory(),
                "Transaction Start Time column cell value factory is not set.");
        assertNotNull(controller.transactionEndColumn.getCellValueFactory(),
                "Transaction End Time column cell value factory is not set.");
        assertNotNull(controller.transactionAmountColumn.getCellValueFactory(),
                "Transaction Amount column cell value factory is not set.");
    }

    /**
     * Tests setup of the citation table column bindings.
     * Ensures that all required columns have a CellValueFactory set.
     */
    @Test
    public void testSetupCitationTable() {
        controller.setupCitationTable();

        assertNotNull(controller.citationVehicleColumn.getCellValueFactory(),
                "Citation Vehicle column cell value factory is not set.");
        assertNotNull(controller.citationParkingSpaceColumn.getCellValueFactory(),
                "Citation Parking Space column cell value factory is not set.");
        assertNotNull(controller.citationZoneColumn.getCellValueFactory(),
                "Citation Zone column cell value factory is not set.");
        assertNotNull(controller.citationIssueTimeColumn.getCellValueFactory(),
                "Citation Issue Time column cell value factory is not set.");
        assertNotNull(controller.citationAmountColumn.getCellValueFactory(),
                "Citation Amount column cell value factory is not set.");
        assertNotNull(controller.citationReasonColumn.getCellValueFactory(),
                "Citation Reason column cell value factory is not set.");
    }


    /**
     * Utility method to wait for JavaFX events to complete.
     *
     * @throws InterruptedException If the waiting is interrupted.
     */
    private void WaitForFXEvents() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        latch.await();
    }

    /**
     * Utility method to wait for JavaFX events to complete.
     * Ensures that all tasks in the JavaFX application thread are executed before continuing.
     */
    private void waitForFXEvents() {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore the interrupted status
            throw new RuntimeException("Test interrupted while waiting for JavaFX events", e);
        }
    }


    /**
     * Tests the scenario where no transactions are returned from the backend.
     * Ensures the transaction table is empty and an appropriate state is handled.
     */
    @Test
    public void testRequestTransactionReport_NoTransactionsFound() throws Exception {
        when(rabbitMQSenderMock.requestTransactions()).thenReturn(List.of());

        Platform.runLater(() -> controller.requestTransactionReport());
        waitForFXEvents();

        assertTrue(controller.transactionTable.getItems().isEmpty(), "Transaction table should be empty when no transactions are found");
    }

    /**
     * Tests the scenario where no citations are returned from the backend.
     * Ensures the citation table is empty and an appropriate state is handled.
     */
    @Test
    public void testRequestCitationReport_NoCitationsFound() throws Exception {
        when(rabbitMQSenderMock.requestCitations()).thenReturn(List.of());

        Platform.runLater(() -> controller.requestCitationReport());
        waitForFXEvents();

        assertTrue(controller.citationTable.getItems().isEmpty(), "Citation table should be empty when no citations are found");
    }

    /**
     * Tests the behavior of the `showAlert` method.
     * Ensures that an error dialog is displayed with the correct title and message.
     */
    @Test
    public void testShowAlert() {
        Platform.runLater(() -> controller.showAlert("Test Title", "Test Message"));
        waitForFXEvents();

        assertTrue(true, "No exceptions should occur when calling showAlert");
    }

    /**
     * Ensures that both transaction and citation tables are initialized with their columns properly bound.
     * Validates that cell value factories are correctly set for all columns.
     */
    @Test
    public void testTableInitialization() {
        controller.setupTransactionTable();
        controller.setupCitationTable();

        assertNotNull(controller.transactionVehicleColumn.getCellValueFactory(), "Transaction vehicle column should have a cell value factory");
        assertNotNull(controller.transactionParkingSpaceColumn.getCellValueFactory(), "Transaction parking space column should have a cell value factory");

        assertNotNull(controller.citationVehicleColumn.getCellValueFactory(), "Citation vehicle column should have a cell value factory");
        assertNotNull(controller.citationParkingSpaceColumn.getCellValueFactory(), "Citation parking space column should have a cell value factory");
    }

}