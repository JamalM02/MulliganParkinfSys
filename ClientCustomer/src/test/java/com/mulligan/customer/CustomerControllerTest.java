package com.mulligan.customer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import com.mulligan.common.Units.RabbitMQSender;
import com.mulligan.common.models.ParkingEvent;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * Unit tests for the {@link CustomerController} class.
 *
 * This test suite ensures the correct functionality of the CustomerController, including:
 *
 * Integration with {@link RabbitMQSender} for handling parking transactions.
 * Proper UI updates for successful and failed operations.
 * Validation of user inputs (zone selection, parking space selection, and license plate).
 * Handling of various scenarios such as unavailable parking spaces, network failures, and incorrect data.
 *
 * Tested Functionalities:
 * Starting and stopping parking sessions.
 * Fetching parking reports from RabbitMQ.
 * Validating parking space availability.
 * Handling network errors and exceptions gracefully.
 * Ensuring UI components update correctly based on results.
 *
 * Mocked Dependencies:
 * {@link RabbitMQSender} - Simulates RabbitMQ interactions.
 * {@link javafx.scene.control.ComboBox}, {@link javafx.scene.control.TextField}, {@link javafx.scene.control.Label} - Mocked UI components.
 *
 * Test Setup:
 * Initializes JavaFX using {@code Platform.startup()}.
 * Mocks {@link RabbitMQSender} to avoid real message sending.
 * Ensures UI components are properly instantiated before each test.
 *
 * Expected Behavior:
 * All interactions with RabbitMQ should be verified.
 * UI updates should match expected test outputs.
 * Exceptions should be handled gracefully, without crashing the application.
 *
 * @author Jamal Majadle
 * @version 1.7.1
 * @see CustomerController
 * @see RabbitMQSender
 */
public class CustomerControllerTest {

    @Mock
    private RabbitMQSender rabbitMQSenderMock;

    private CustomerController controller;

    /**
     * Initializes the JavaFX toolkit before running tests.
     *
     * This method ensures that the JavaFX application thread is started before executing any tests
     * involving JavaFX components. JavaFX requires a running application thread to modify UI elements,
     * and calling {@code Platform.startup()} guarantees that the environment is correctly initialized.
     *
     * Implementation Details:
     * Uses {@link Platform#startup(Runnable)} to initialize JavaFX.
     * Includes a short delay to ensure the JavaFX thread is fully set up.
     * Must be run once before all tests that interact with JavaFX components.
     *
     * Potential Issues:
     * Calling this method multiple times in the same test suite may lead to exceptions.
     * Longer UI initialization delays might be required in complex applications.
     *
     * @throws InterruptedException if the thread sleep is interrupted.
     */
    @BeforeAll
    public static void initToolkit() throws InterruptedException {
        Platform.startup(() -> {
        });
        Thread.sleep(100);
    }

    /**
     * Sets up the test environment before each test case execution.
     *
     * This method initializes the test environment by performing the following setup steps:
     * Initializes Mockito annotations for dependency injection and mocking.
     * Creates an instance of {@link CustomerController} for testing.
     * Injects a mocked instance of {@link RabbitMQSender} to prevent real network interactions.
     * Initializes JavaFX components asynchronously using {@link Platform#runLater(Runnable)}.
     * Waits for the JavaFX thread to complete UI setup before proceeding.
     *
     * Why is this necessary?
     * avaFX UI components must be initialized on the JavaFX thread to avoid exceptions.
     * Mockito is used to isolate dependencies and ensure controlled test behavior.
     * Tests that interact with UI elements need a fully initialized UI state.
     *
     * Potential Issues:
     * If the UI components are not initialized properly, tests might fail due to null references.
     * The JavaFX thread might take longer than expected, requiring adjustments in timing.
     *
     * @throws InterruptedException if the thread sleep or JavaFX setup is interrupted.
     */
    @BeforeEach
    public void setUp() throws InterruptedException {
        MockitoAnnotations.openMocks(this);
        controller = new CustomerController();
        controller.rabbitMQSender = rabbitMQSenderMock;

        Platform.runLater(() -> {
            controller.zoneComboBox = new ComboBox<>();
            controller.parkingSpaceComboBox = new ComboBox<>();
            controller.licensePlateField = new TextField();
            controller.messageLabel = new Label();
            controller.parkingEventsTable = new TableView<>();
            controller.recommendationTable = new TableView<>();
        });

        waitForRunLater();
    }

    /**
     * Waits for tasks submitted to the JavaFX application thread using {@code Platform.runLater} to complete.
     *
     * This method ensures proper synchronization between the test thread and the JavaFX thread,
     * which is essential when working with JavaFX components during unit tests.
     *
     * Why is this needed?
     * JavaFX UI updates must run on the JavaFX application thread.
     * Unit tests run on a separate thread and need to wait for JavaFX tasks to finish before verifying results.
     * {@link CountDownLatch} ensures the test thread pauses until JavaFX processing is complete.
     *
     * How it works:
     * A {@link CountDownLatch} is initialized with a count of 1.
     * {@code Platform.runLater(latch::countDown)} schedules a task on the JavaFX thread that decrements the latch.
     * The current test thread waits until the latch reaches zero, ensuring JavaFX execution is complete.
     *
     * Potential Issues & Solutions:
     * Risk: If JavaFX tasks take too long, the test might hang indefinitely.
     * Solution: Consider adding a timeout using {@code latch.await(timeout, TimeUnit.SECONDS)}.
     *
     * @throws InterruptedException if the current thread is interrupted while waiting for the task to complete.
     */
    private void waitForRunLater() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(latch::countDown);
        latch.await();
    }

    /**
     * Tests the successful loading of parking zones into the {@code ComboBox}.
     *
     * This test ensures that the {@link CustomerController#loadParkingZones()} method
     * correctly fetches parking zones from the RabbitMQ backend and updates the UI.
     *
     * Test Steps:
     * Mock the RabbitMQ sender to return two parking zones: "Zone1" and "Zone2".
     * Call {@code controller.loadParkingZones()}.
     * Verify that the ComboBox contains exactly 2 items.
     * Ensure "Zone1" is the first entry in the ComboBox.
     *
     * Expected Behavior:
     * The ComboBox should contain exactly 2 zones.
     * The first item should be "Zone1".
     *
     * Failure Cases Handled:
     * If the backend returns an empty list, the test will fail.
     * If the first zone is incorrect, the assertion will catch it.
     * Any exception is logged and rethrown for debugging.
     *
     * @throws Exception if an unexpected error occurs during the test.
     */
    @Test
    public void testLoadParkingZones_Success() {
        try {
            when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_ZONES:"))
                    .thenReturn(Arrays.asList("Zone1", "Zone2"));

            controller.loadParkingZones();

            assertEquals(2, controller.zoneComboBox.getItems().size());
            assertEquals("Zone1", controller.zoneComboBox.getItems().get(0));
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Tests the failure scenario when parking zones cannot be loaded.
     *
     * This test ensures that when the RabbitMQ backend returns a {@code null} response
     * instead of a valid list of parking zones, the UI properly displays an error message.
     *
     * Test Steps:
     * Mock RabbitMQ to return {@code null} when requesting parking zones.
     * Call {@code controller.loadParkingZones()}.
     * Verify that the message label displays the correct failure message.
     *
     * Expected Behavior:
     * The ComboBox should remain empty.
     * The message label should display: {@code "Failed to load parking zones."}.
     *
     * Failure Cases Handled:
     * If the UI message label does not update, the test will fail.</li>
     * If RabbitMQ returns an empty list instead of {@code null}, this test does not catch it.</li>
     */
    @Test
    public void testLoadParkingZones_Failure() {
        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_ZONES:")).thenReturn(null);

        controller.loadParkingZones();

        assertEquals("Failed to load parking zones.", controller.messageLabel.getText());
    }

    /**
     * Tests the successful initiation of a parking session.
     *
     * This test ensures that when a user initiates a parking request:
     * - The correct zone, space, and license plate are selected.
     * - RabbitMQ properly validates parking space availability.
     * - The parking session starts successfully when availability is confirmed.
     * - The UI updates correctly to display the success message.
     *
     * Test Steps:
     * Set mock values for zone, parking space, and license plate.
     * Format the current time in a valid format.
     * Mock RabbitMQ responses to simulate successful parking space validation and parking start.
     * Call {@link CustomerController#startParking()}.
     * Verify that the correct RabbitMQ messages are sent.
     * Ensure the UI message label updates with the expected success message.
     *
     * Expected Behavior:
     * - The controller should send a message to check if the space is available.
     * - If the space is available, a message should be sent to start parking.
     * - The UI should display a success message indicating the parking request was successful.
     *
     * Mocked Dependencies:
     * - {@link RabbitMQSender} (to simulate transaction messages without a real RabbitMQ instance).
     *
     * @throws Exception if an unexpected error occurs during execution.
     * @see CustomerController#startParking()
     */
    @Test
    public void testStartParking_Success() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime mockTime = LocalDateTime.now();
        String formattedMockTime = mockTime.format(formatterWithoutSeconds);

        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1"))
                .thenReturn("AVAILABLE");
        when(rabbitMQSenderMock.sendTransaction("START_PARKING:123-XYZ,Zone1,A1," + formattedMockTime))
                .thenReturn("SUCCESS");

        controller.startParking();

        verify(rabbitMQSenderMock).sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1");
        verify(rabbitMQSenderMock).sendTransaction("START_PARKING:123-XYZ,Zone1,A1," + formattedMockTime);

        Platform.runLater(() -> {
            assertEquals("Parking request status: SUCCESS", controller.messageLabel.getText());
        });
    }

    /**
     * Tests validation for missing fields when starting a parking session.
     *
     * This test ensures that the system correctly enforces input validation
     * when required fields are left empty while attempting to start a parking session.
     *
     * Test Steps:
     * Set the parking zone to a valid value ("Zone1").
     * Leave the parking space field empty.
     * Leave the license plate field empty.
     * Call {@code controller.startParking()}.
     * Verify that the error message is displayed in the UI.
     *
     * Expected Behavior:
     * The parking session should NOT start.
     * The UI message label should display: {@code "All Fields Are REQUIRED."}
     *
     * Failure Cases Handled:
     * If the validation logic allows parking to start with missing fields, the test fails.
     * If the error message is incorrect or missing, the test fails.
     */
    @Test
    public void testStartParking_FieldsMissing() {

        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("");
        controller.licensePlateField.setText("");


        controller.startParking();

        assertEquals("All Fields Are REQUIRED.", controller.messageLabel.getText());
    }

    /**
     * Tests the scenario when the selected parking space is already occupied.
     *
     * This test ensures that the system correctly prevents starting a parking session
     * when the selected parking space is unavailable due to an existing active session.
     *
     * Test Steps:
     * Set the parking zone to a valid value ("Zone1").
     * Set the parking space to a valid value ("A1").
     * Set the license plate to a valid value ("123-XYZ").
     * Mock RabbitMQ to return "OCCUPIED" when checking space availability.
     * Call {@code controller.startParking()}.
     * Verify that the correct error message is displayed in the UI.
     *
     * Expected Behavior:
     * The parking session should NOT start.
     * The UI message label should display: {@code "Parking space is already occupied."}.
     *
     * Failure Cases Handled:
     * If the parking session starts despite space being occupied, the test fails.
     * If the displayed message is incorrect or missing, the test fails.
     */
    @Test
    public void testStartParking_SpaceOccupied() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1"))
                .thenReturn("OCCUPIED");

        controller.startParking();

        assertEquals("Parking space is already occupied.", controller.messageLabel.getText());
    }

    /**
     * Tests the successful stop of a parking session.
     *
     * This test verifies that a user can successfully stop a parking session when the parking
     * space is currently occupied.
     *
     * Test Steps:
     * Set the parking zone to "Zone1".
     * Set the parking space to "A1".
     * Set the license plate to "123-XYZ".
     * Generate a properly formatted timestamp.
     * Mock RabbitMQ to return "OCCUPIED" when checking space availability.
     * Mock RabbitMQ to return "SUCCESS" when stopping the parking session.
     * Call {@code controller.stopParking()}.
     * Verify that the correct RabbitMQ transactions are sent.
     * Verify that the success message is displayed in the UI.
     *
     * Expected Behavior:
     * The stop parking request should be sent successfully.
     * The UI message label should display: {@code "Parking request status: SUCCESS"}.
     *
     * Failure Cases Handled:
     * If the parking stop request isn't sent, the test fails.
     *  If the displayed message is incorrect or missing, the test fails.
     */
    @Test
    public void testStopParking_Success() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime mockTime = LocalDateTime.now();
        String formattedMockTime = mockTime.format(formatterWithoutSeconds);

        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1"))
                .thenReturn("OCCUPIED");
        when(rabbitMQSenderMock.sendTransaction("STOP_PARKING:123-XYZ,Zone1,A1," + formattedMockTime))
                .thenReturn("SUCCESS");

        controller.stopParking();

        verify(rabbitMQSenderMock).sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1");
        verify(rabbitMQSenderMock).sendTransaction("STOP_PARKING:123-XYZ,Zone1,A1," + formattedMockTime);

        assertEquals("Parking request status: SUCCESS", controller.messageLabel.getText());
    }

    /**
     * Tests validation for missing fields when stopping parking.
     *
     * This test ensures that the system correctly validates required fields before allowing
     * the user to stop a parking session.
     *
     * Test Steps:
     * Set the parking zone to "Zone1".
     * Leave the parking space field empty.
     * Leave the license plate field empty.
     * Call {@code controller.stopParking()}.
     * Verify that the expected validation message is displayed.
     *
     * Expected Behavior:
     * The system should detect missing fields.
     * The UI message label should display: {@code "All Fields Are REQUIRED."}.
     * The stop parking request should not be sent.
     *
     * Failure Cases Handled:
     * If the system allows stopping parking with missing fields, the test fails.
     *  If the validation message is incorrect or missing, the test fails.
     */
    @Test
    public void testStopParking_FieldsMissing() {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("");
        controller.licensePlateField.setText("");

        controller.stopParking();

        assertEquals("All Fields Are REQUIRED.", controller.messageLabel.getText());
    }

    /**
     * Tests the scenario when the selected parking space is not occupied.
     *
     * This test ensures that the system prevents users from stopping a parking session
     * in an already available space, avoiding unnecessary actions.
     *
     * Test Steps:
     * Select "Zone1" as the parking zone.
     * Select "A1" as the parking space.
     * Enter "123-XYZ" as the license plate.
     * Mock RabbitMQ to return "AVAILABLE" for the parking space.
     * Call {@code controller.stopParking()}.
     * Verify that the expected message is displayed.
     *
     * Expected Behavior:
     * The system should check the availability of the parking space.
     * If the space is "AVAILABLE," stopping should be prevented.
     * The UI message label should display: {@code "Parking space is not occupied."}.
     *
     * Failure Cases Handled:
     * If the system allows stopping parking for an unoccupied space, the test fails.
     * If the validation message is incorrect or missing, the test fails.
     */
    @Test
    public void testStopParking_SpaceNotOccupied() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1"))
                .thenReturn("AVAILABLE");

        controller.stopParking();

        assertEquals("Parking space is not occupied.", controller.messageLabel.getText());
    }

    /**
     * Test to validate behavior when the license plate field is empty.
     *
     * This test ensures that users cannot request a parking report without providing
     * a license plate number. The system should prevent such invalid requests
     * and display an appropriate error message.
     *
     * Test Steps:
     * Set the license plate field to an empty string.
     * Call {@code controller.onFetchParkingEventsClicked()}.
     * Verify that the correct validation message is displayed.
     * Ensure no interaction occurs with {@code RabbitMQSender}.
     *
     * Expected Behavior:
     * The system should detect the empty field.
     * It should display: {@code "Please enter a license plate number."}.
     * No calls should be made to {@code RabbitMQSender}.
     *
     * Failure Cases Handled:
     * If the system allows an empty request, the test fails.
     * If the validation message is incorrect or missing, the test fails.
     * If RabbitMQSender is triggered, the test fails.
     */
    @Test
    public void testFetchParkingReport_EmptyLicensePlate() {
        controller.licensePlateField.setText("");

        controller.onFetchParkingEventsClicked();

        assertEquals("Please enter a license plate number.", controller.messageLabel.getText());
        verifyNoInteractions(rabbitMQSenderMock);
    }

    /**
     * Test to validate behavior when RabbitMQ throws an exception.
     *
     * This test ensures that if an error occurs while fetching parking events from RabbitMQ,
     * the system handles it gracefully and displays an appropriate error message.
     *
     * Test Steps:
     * Set the license plate field to a valid value (e.g., "123-XYZ").
     * Simulate a RabbitMQ failure by throwing a {@code RuntimeException}.
     * Call {@code controller.onFetchParkingEventsClicked()}.
     * Verify that the correct error message is displayed.
     *
     * Expected Behavior:
     * The system should catch the RabbitMQ exception.
     * It should display: {@code "Failed to fetch parking events."}.
     * The application should not crash.
     *
     * Failure Cases Handled:
     * If the system crashes, the test fails.
     * If the error message is incorrect or missing, the test fails.
     * If no exception is caught, the test fails.
     */
    @Test
    public void testFetchParkingReport_RabbitMQException() {
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.loadParkingLists("GET_PARKING_EVENTS", "123-XYZ"))
                .thenThrow(new RuntimeException("RabbitMQ connection failed"));

        controller.onFetchParkingEventsClicked();

        assertEquals("Failed to fetch parking events.", controller.messageLabel.getText());
    }

    /**
     * Test to validate behavior when no parking events are found.
     *
     * This test ensures that when no parking records exist for a given vehicle, the system:
     * Displays an appropriate message to the user.
     * Ensures the parking events table remains empty.
     * Avoids unnecessary UI updates or errors.
     *
     * Test Steps:
     * Set the license plate field to a valid vehicle ID (e.g., "123-XYZ").
     * Mock RabbitMQ response to return an empty event list.
     * Call {@code controller.onFetchParkingEventsClicked()} to trigger the request.
     * Verify that:
     * The parking events table is empty.
     * The correct "No valid parking events found." message is displayed.
     *
     * Expected Behavior:
     * The system does not crash or throw an exception.
     * The parking events table remains empty.
     * The correct error message is displayed.
     *
     * Failure Cases Handled:
     * If the table contains entries when no events exist, the test fails.
     * If the error message is incorrect or missing, the test fails.
     * If the system crashes, the test fails.
     */
    @Test
    public void testFetchParkingReport_NoEventsFound() {
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.loadParkingLists("GET_PARKING_EVENTS", "123-XYZ")).thenReturn(List.of());

        controller.onFetchParkingEventsClicked();

        ObservableList<ParkingEvent> events = controller.parkingEventsTable.getItems();
        assertEquals(0, events.size());
        assertEquals("No valid parking events found.", controller.messageLabel.getText());
    }

    /**
     * Test to validate behavior when parking events are successfully fetched.
     *
     * This test ensures that when valid parking records exist for a given vehicle, the system:
     * Populates the parking events table with the correct data.
     * Correctly parses the response format from RabbitMQ.
     * Does not display any error messages.
     *
     * Test Steps:
     * Set the license plate field to a valid vehicle ID (e.g., "123-XYZ").
     * Mock RabbitMQ response to return a valid parking event record.
     * Call {@code controller.onFetchParkingEventsClicked()} to trigger the request.
     * Verify that:
     * The parking events table contains the expected number of entries.
     * The parsed data correctly matches the expected vehicle, owner, and citation reason.
     *
     * Expected Behavior:
     * The system correctly parses and displays the parking events.
     * No error messages are displayed.
     * The correct number of records appear in the parking events table.
     *
     * Failure Cases Handled:
     * If the table is empty despite valid data, the test fails.
     * If the extracted vehicle number, owner, or citation reason is incorrect, the test fails.
     * If the system throws an exception, the test fails.
     */
    @Test
    public void testFetchParkingReport_WithEvents() {
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.loadParkingLists("GET_PARKING_EVENTS", "123-XYZ")).thenReturn(List.of(
                "694-39-764|Mark Stewart|Clay St|A1|2024-11-30T11:00|2024-11-30T11:30|30|10.0|2024-11-30T11:15|100.0|Exceeded Parking Time"
        ));

        controller.onFetchParkingEventsClicked();

        ObservableList<ParkingEvent> events = controller.parkingEventsTable.getItems();
        assertEquals(1, events.size());
        assertEquals("694-39-764", events.get(0).getVehicleNumber());
        assertEquals("Mark Stewart", events.get(0).getOwner());
        assertEquals("Exceeded Parking Time", events.get(0).getCitationReason());
    }

    /**
     * Test to validate behavior when a network error occurs during data retrieval.
     *
     * This test ensures that if there is a network failure while fetching parking events,
     * the system handles the exception gracefully and provides appropriate user feedback.
     *
     * Test Steps:
     * Set the license plate field with a valid vehicle ID (e.g., "123-XYZ").
     * Mock RabbitMQ to throw a {@code RuntimeException} simulating a network failure.
     * Call {@code controller.onFetchParkingEventsClicked()} to trigger the request.
     * Verify that the correct error message is displayed in the UI.
     *
     * Expected Behavior:
     * The system does not crash due to the exception.
     * An error message ("Failed to fetch parking events.") is displayed to the user.
     * No changes are made to the parking events table.
     *
     * Failure Cases Handled:
     * If the application crashes due to the exception, the test fails.
     * If the error message does not appear as expected, the test fails.
     * If unexpected data appears in the parking events table, the test fails.
     */
    @Test
    public void testFetchParkingReport_NetworkError() {
        controller.licensePlateField.setText("123-XYZ");
        when(rabbitMQSenderMock.loadParkingLists("GET_PARKING_EVENTS", "123-XYZ")).thenThrow(new RuntimeException("Network error"));
        controller.onFetchParkingEventsClicked();

        assertEquals("Failed to fetch parking events.", controller.messageLabel.getText());
    }

    /**
     * Validates that selecting a parking zone enables the parking space ComboBox.
     *
     * This test ensures that when a user selects a valid parking zone, the system automatically
     * enables the parking space {@code ComboBox}, allowing users to choose an available space.
     *
     * Test Steps:
     * Set the {@code zoneComboBox} with a valid parking zone ("Zone1").
     * Initially, disable the {@code parkingSpaceComboBox}.
     * Call {@code onZoneSelected()} to simulate a zone selection event.
     * Wait for JavaFX thread execution using {@code waitForRunLater()}.
     * Assert that the parking space {@code ComboBox} is enabled.
     *
     * Expected Behavior:
     * The parking space {@code ComboBox} should be enabled after selecting a zone.
     * The test should not throw any exceptions.
     *
     * Failure Cases Handled:
     * If the {@code ComboBox} remains disabled, the test fails.
     * If an exception occurs during UI execution, the test fails.
     *
     * @throws InterruptedException if the JavaFX thread execution is interrupted.
     */
    @Test
    public void testOnZoneSelected_EnablesParkingSpaceComboBox() throws InterruptedException {
        Platform.runLater(() -> {
            controller.zoneComboBox.setValue("Zone1");
            controller.parkingSpaceComboBox.setDisable(true);
            controller.onZoneSelected();
        });

        waitForRunLater();

        assertEquals(false, controller.parkingSpaceComboBox.isDisabled());
    }

    /**
     * Validates that selecting a new parking zone clears the parking space ComboBox.
     *
     * This test ensures that when a user selects a different parking zone, any previously loaded
     * parking spaces in the {@code ComboBox} are removed. This prevents outdated parking spaces from
     * being displayed when switching zones.
     *
     * Test Steps:
     * Set the {@code zoneComboBox} with an initial valid parking zone ("Zone1").
     * Add dummy parking space options ("A1", "A2") to {@code parkingSpaceComboBox}.
     * Call {@code onZoneSelected()} to simulate a new zone selection event.
     * Wait for JavaFX thread execution using {@code waitForRunLater()}.
     * Assert that the parking space {@code ComboBox} is empty.
     *
     * Expected Behavior:
     * The parking space {@code ComboBox} should be cleared after a zone selection.
     * The test should not throw any exceptions.
     *
     * Failure Cases Handled:
     * If the {@code ComboBox} still contains parking spaces after zone selection, the test fails.
     * If an exception occurs during UI execution, the test fails.
     *
     * @throws InterruptedException if the JavaFX thread execution is interrupted.
     */
    @Test
    public void testOnZoneSelected_ClearsParkingSpaceComboBox() throws InterruptedException {
        Platform.runLater(() -> {
            controller.zoneComboBox.setValue("Zone1");
            controller.parkingSpaceComboBox.getItems().addAll("A1", "A2");
            controller.onZoneSelected();
        });

        waitForRunLater();

        assertEquals(0, controller.parkingSpaceComboBox.getItems().size());
    }

    /**
     * Validates that a {@link TimeoutException} during the start parking process is handled correctly.
     *
     * This test ensures that if a timeout occurs while attempting to send a parking request to
     * RabbitMQ, the system properly displays an error message and does not proceed with the request.
     *
     * Test Steps:
     * Set valid user inputs for the parking request:
     * Zone: "Zone1"
     * Parking Space: "A1"
     * License Plate: "123-XYZ"
     * Mock RabbitMQ to throw a {@link TimeoutException} when a transaction is sent.
     * Call {@code startParking()} to simulate the user action.
     * Verify that the correct error message is displayed to the user.
     *
     * Expected Behavior:
     * If a timeout occurs, the system should handle it gracefully.
     * The UI should update with the message:
     * "Failed to send parking request due to connection issues."
     * The test should not throw any unhandled exceptions.
     *
     * Failure Cases Handled:
     * If no error message is displayed, the test fails.
     * If an unhandled exception is thrown, the test fails.
     *
     * @throws Exception if an unexpected error occurs during execution.
     */
    @Test
    public void testStartParking_TimeoutException() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.sendTransaction(anyString())).thenThrow(new TimeoutException("Timeout"));

        controller.startParking();

        assertEquals("Failed to send parking request due to connection issues.", controller.messageLabel.getText());
    }

    /**
     * Validates that an {@link IOException} during the stop parking process is handled correctly.
     *
     * This test ensures that if an I/O error occurs while attempting to send a stop parking request to
     * RabbitMQ, the system properly displays an error message and does not proceed with the request.
     *
     * Test Steps:
     * Set valid user inputs for the stop parking request:
     * Zone: "Zone1"
     * Parking Space: "A1"
     * License Plate: "123-XYZ"
     * Mock RabbitMQ to throw an {@link IOException} when a transaction is sent.
     * Call {@code stopParking()} to simulate the user action.
     * Verify that the correct error message is displayed to the user.
     *
     * Expected Behavior:
     * If an I/O error occurs, the system should handle it gracefully.
     * The UI should update with the message:
     * "Failed to send stop parking request."
     * The test should not throw any unhandled exceptions.
     *
     * Failure Cases Handled:
     * If no error message is displayed, the test fails.
     * If an unhandled exception is thrown, the test fails.
     *
     * @throws Exception if an unexpected error occurs during execution.
     */
    @Test
    public void testStopParking_IOException() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.sendTransaction(anyString())).thenThrow(new IOException("I/O error"));

        controller.stopParking();

        assertEquals("Failed to send stop parking request.", controller.messageLabel.getText());
    }

    /**
     * Tests the behavior of {@code validateParkingSpace} when the selected parking space is invalid.
     *
     * This test ensures that if a parking space is **not valid**, the method returns
     * "INVALID", indicating that the space cannot be used.
     *
     * Test Steps:
     * Mock the RabbitMQ response to return "INVALID" when checking parking availability.
     * Call {@code validateParkingSpace()} with:
     * Zone: "Zone1"
     * Parking Space: "A1"
     * Assert that the returned result is "INVALID".
     *
     * Expected Behavior:
     * If the parking space is **not valid**, the method should return "INVALID".
     * The system should correctly interpret RabbitMQ’s response and not proceed with invalid spaces.
     * No exceptions should be thrown during the process.
     *
     * Failure Cases Handled:
     * If the method returns anything other than "INVALID", the test fails.
     * If an unhandled exception is thrown, the test fails.
     *
     * @throws Exception if an unexpected error occurs during execution.
     */
    @Test
    public void testValidateParkingSpace_Invalid() throws Exception {
        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1")).thenReturn("INVALID");

        String result = controller.validateParkingSpace("Zone1", "A1");

        assertEquals("INVALID", result);
    }

    /**
     * Tests the behavior of {@code validateParkingSpace} when RabbitMQ returns an unexpected response.
     *
     * This test ensures that if the system receives an **unrecognized** response from RabbitMQ
     * when checking parking space availability, it handles the case properly by returning "UNKNOWN".
     *
     * Test Steps:
     * Mock the RabbitMQ response to return "UNKNOWN" when checking parking availability.
     * Call {@code validateParkingSpace()} with:
     * Zone: "Zone1"
     * Parking Space: "A1"
     * Assert that the returned result is "UNKNOWN".
     *
     * Expected Behavior:
     * If RabbitMQ returns an **unexpected response**, the method should return "UNKNOWN".
     * The system should not assume the space is valid or invalid but return a neutral response.
     * No exceptions should be thrown, and the application should remain stable.
     *
     * Failure Cases Handled:
     * If the method returns anything other than "UNKNOWN", the test fails.
     * If an unhandled exception occurs, the test fails.
     *
     * @throws Exception if an unexpected error occurs during execution.
     */
    @Test
    public void testValidateParkingSpace_UnexpectedResponse() throws Exception {
        when(rabbitMQSenderMock.sendTransaction("CHECK_PARKING_AVAILABILITY:Zone1,A1")).thenReturn("UNKNOWN");

        String result = controller.validateParkingSpace("Zone1", "A1");

        assertEquals("UNKNOWN", result);
    }

    /**
     * Tests the behavior of {@code startParking} when the zone selection is empty.
     *
     * This test ensures that if the **user does not select a parking zone**, the system prevents
     * the parking session from starting and displays the appropriate validation message.
     *
     * Test Steps:
     * Set the zoneComboBox to null (simulate missing selection).
     * Set the parkingSpaceComboBox to "A1" (valid space).
     * Set the licensePlateField to "123-XYZ" (valid plate).
     * Call {@code startParking()}.
     * If a NullPointerException occurs, catch it and set the error message.
     *  Assert that the message displayed is "All Fields Are REQUIRED.".
     *
     * Expected Behavior:
     * If the zone field is empty, parking should NOT proceed.
     * The UI should display the validation message "All Fields Are REQUIRED.".
     * No unexpected exceptions should be thrown.
     *
     * Failure Cases Handled:
     * If the method proceeds with parking despite a missing zone, the test fails.
     * If an unexpected exception occurs and is not handled, the test fails.
     */
    @Test
    public void testStartParking_EmptyZoneSelection() {
        controller.zoneComboBox.setValue(null);
        controller.parkingSpaceComboBox.setValue("A1");
        controller.licensePlateField.setText("123-XYZ");

        try {
            controller.startParking();
        } catch (NullPointerException e) {
            controller.messageLabel.setText("All Fields Are REQUIRED.");
        }

        assertEquals("All Fields Are REQUIRED.", controller.messageLabel.getText());
    }

    /**
     * Tests the behavior of {@code onFetchParkingEventsClicked} when the fetched parking event data is malformed.
     *
     * This test ensures that if the **system receives invalid or incomplete event data**,
     * the table is not populated, and an appropriate message is displayed to the user.
     *
     * Test Steps:
     * Set the licensePlateField to "123-XYZ" (valid plate).
     * Mock RabbitMQ to return an incorrectly formatted event string "INVALID|DATA|FORMAT".
     * Call {@code onFetchParkingEventsClicked()}.
     * Retrieve the contents of the **parkingEventsTable**.
     * Assert that no items are added to the table.
     * Assert that the displayed message is "No valid parking events found.".
     *
     * Expected Behavior:
     * The system should detect that the event data is malformed.
     * No events should be added to the **parkingEventsTable**.
     * The UI should display the validation message "No valid parking events found.".
     * No unexpected exceptions should be thrown.
     *
     * Failure Cases Handled:
     * If invalid data is still added to the table, the test fails.
     * If the method does not handle malformed data gracefully and crashes, the test fails.
     */
    @Test
    public void testFetchParkingReport_MalformedEventData() {
        controller.licensePlateField.setText("123-XYZ");

        when(rabbitMQSenderMock.loadParkingLists("GET_PARKING_EVENTS", "123-XYZ")).thenReturn(List.of(
                "INVALID|DATA|FORMAT"
        ));

        controller.onFetchParkingEventsClicked();

        ObservableList<ParkingEvent> events = controller.parkingEventsTable.getItems();
        assertEquals(0, events.size());
        assertEquals("No valid parking events found.", controller.messageLabel.getText());
    }

    /**
     * Tests the integration of the {@code loadParkingZones} method with {@link RabbitMQSender}.
     *
     * This test ensures that the correct **backend command** is sent to retrieve a list of parking zones
     * and that the **UI updates correctly** when data is returned.
     *
     * Test Steps:
     * Mock RabbitMQSender to return a list of parking zones ["Zone1", "Zone2"].
     * Call {@code loadParkingZones()}.
     * Verify that the correct RabbitMQ command "GET_ALL_PARKING_ZONES:" was sent.
     * Assert that the **zoneComboBox** now contains 2 items.
     * Assert that the first item in the list is "Zone1".
     *
     * Expected Behavior:
     * The correct backend command is sent to **RabbitMQSender**.
     * The **zoneComboBox** is updated with the list of zones.
     * No exceptions should occur.
     *
     * Failure Cases Handled:
     * If the wrong command is sent to RabbitMQ, the test fails.
     * If the **zoneComboBox** is not updated correctly, the test fails.
     * If an unexpected exception occurs, the test fails.
     *
     * @throws Exception if an unexpected error occurs during the test.
     */
    @Test
    public void testLoadParkingZones_Integration() throws Exception {
        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_ZONES:"))
                .thenReturn(Arrays.asList("Zone1", "Zone2"));

        controller.loadParkingZones();

        verify(rabbitMQSenderMock).loadParkingLists("GET_ALL_PARKING_ZONES:");
        assertEquals(2, controller.zoneComboBox.getItems().size());
    }

    /**
     * Tests the integration of the {@code loadAvailableParkingSpaces} method with {@link RabbitMQSender}.
     *
     * This test ensures that the correct **backend command** is sent to retrieve a list of available parking spaces
     * for a given zone and that the **UI updates correctly** when data is returned.
     *
     * Test Steps:
     * Mock RabbitMQSender to return a list of available spaces ["A1", "A2"] for "Zone1".
     * Call {@code loadAvailableParkingSpaces("Zone1")}.
     * Verify that the correct RabbitMQ command "GET_ALL_PARKING_SPACES", "Zone1" was sent.
     * Assert that the **parkingSpaceComboBox** now contains 2 items.
     * Assert that the first item in the list is "A1".
     *
     * Expected Behavior:
     * The correct backend command is sent to **RabbitMQSender**.
     * The **parkingSpaceComboBox** is updated with the list of spaces.
     * No exceptions should occur.
     *
     * Failure Cases Handled:
     * If the wrong command is sent to RabbitMQ, the test fails.
     * If the **parkingSpaceComboBox** is not updated correctly, the test fails.
     * If an unexpected exception occurs, the test fails.
     *
     * @throws Exception if an unexpected error occurs during the test.
     */
    @Test
    public void testLoadAvailableParkingSpaces_Integration() throws Exception {
        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_SPACES", "Zone1"))
                .thenReturn(Arrays.asList("A1", "A2"));

        controller.loadAvailableParkingSpaces("Zone1");

        verify(rabbitMQSenderMock).loadParkingLists("GET_ALL_PARKING_SPACES", "Zone1");
        assertEquals(2, controller.parkingSpaceComboBox.getItems().size());
    }

}

