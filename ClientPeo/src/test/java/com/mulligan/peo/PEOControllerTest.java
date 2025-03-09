package com.mulligan.peo;

import com.mulligan.common.Units.RabbitMQSender;
import javafx.application.Platform;
import javafx.scene.control.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the PEOController class.
 * Validates the behavior of methods for parking zone loading, parking status checks, and citation management.
 */
public class PEOControllerTest {

    @Mock
    private RabbitMQSender rabbitMQSenderMock;

    private PEOController controller;

    /**
     * Initializes the JavaFX platform before running tests.
     */
    @BeforeAll
    public static void initToolkit() {
        Platform.startup(() -> {});
    }

    /**
     * Sets up the test environment by initializing the PEOController instance and its dependencies.
     */
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new PEOController();
        controller.rabbitMQSender = rabbitMQSenderMock;

        controller.zoneComboBox = new ComboBox<>();
        controller.parkingSpaceComboBox = new ComboBox<>();
        controller.licensePlateField = new TextField();
        controller.citationAmountField = new TextField();
        controller.checkStatusMessageLabel = new Label();
        controller.citationStatusMessageLabel = new Label();
        controller.issueCitationButton = new Button();
        controller.cancelCitationButton = new Button();
    }

    /**
     * Tests successful loading of parking zones into the ComboBox.
     */
    @Test
    public void testLoadParkingZones_Success() throws Exception {
        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_ZONES:"))
                .thenReturn(List.of("Zone1", "Zone2"));
        controller.loadParkingZones();
        assertEquals(2, controller.zoneComboBox.getItems().size());
        assertEquals("Zone1", controller.zoneComboBox.getItems().get(0));
    }

    /**
     * Tests the failure scenario when loading parking zones.
     */
    @Test
    public void testLoadParkingZones_Failure() {
        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_ZONES:"))
                .thenThrow(new RuntimeException("Connection error"));
        controller.loadParkingZones();
        assertEquals("Failed to load parking zones due to connection issues.", controller.checkStatusMessageLabel.getText());
    }

    /**
     * Tests checking the parking status when the vehicle is parked correctly.
     */
    @Test
    public void testCheckParkingStatus_ParkedCorrectly() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("ABC123");
        when(rabbitMQSenderMock.sendCitation("CHECK_PARK_STATUS:Zone1,SpaceA,ABC123"))
                .thenReturn("Parking Ok");
        controller.checkParkingStatus();
        assertEquals("Vehicle is parked correctly.", controller.checkStatusMessageLabel.getText());
        assertTrue(controller.citationAmountField.isDisabled());
    }

    /**
     * Tests checking the parking status when the vehicle is not parked correctly.
     */
    @Test
    public void testCheckParkingStatus_NotParkedCorrectly() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("ABC123");
        when(rabbitMQSenderMock.sendCitation("CHECK_PARK_STATUS:Zone1,SpaceA,ABC123"))
                .thenReturn("Parking Not Ok");
        controller.checkParkingStatus();
        assertEquals("Vehicle is NOT parked correctly.", controller.checkStatusMessageLabel.getText());
        assertFalse(controller.citationAmountField.isDisabled());
    }

    /**
     * Tests issuing a citation successfully.
     */
    @Test
    public void testIssueCitation_Success() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("ABC123");
        controller.citationAmountField.setText("50");

        DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime mockTime = LocalDateTime.now();
        String formattedMockTime = mockTime.format(formatterWithoutSeconds);

        when(rabbitMQSenderMock.sendCitation("ISSUE_CITATION:Zone1,SpaceA,ABC123,50," + formattedMockTime))
                .thenReturn("Citation Issued Successfully");
        controller.issueCitation();

        verify(rabbitMQSenderMock, times(1)).sendCitation(anyString());
        assertEquals("Citation Issued Successfully", controller.citationStatusMessageLabel.getText());
    }

    /**
     * Tests issuing a citation with invalid input.
     */
    @Test
    public void testIssueCitation_InvalidInput() {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("");
        controller.citationAmountField.setText("");
        controller.issueCitation();
        assertEquals("All Fields Are REQUIRED.", controller.citationStatusMessageLabel.getText());
    }

    /**
     * Tests canceling the citation process and resetting the form.
     */
    @Test
    public void testCancelCitation() {
        controller.citationAmountField.setDisable(false);
        controller.issueCitationButton.setDisable(false);
        controller.cancelCitationButton.setDisable(false);
        controller.cancelCitation(null);
        assertNull(controller.zoneComboBox.getValue());
        assertTrue(controller.parkingSpaceComboBox.isDisabled());
        assertTrue(controller.licensePlateField.isDisabled());
        assertTrue(controller.citationAmountField.isDisabled());
        assertTrue(controller.issueCitationButton.isDisabled());
        assertTrue(controller.cancelCitationButton.isDisabled());
    }

    /**
     * Tests the failure scenario when loading parking spaces for a selected zone.
     * Ensures the appropriate error message is displayed and no spaces are loaded.
     */
    @Test
    public void testLoadAvailableParkingSpaces_Failure() {
        controller.zoneComboBox.setValue("Zone1");

        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_SPACES", "Zone1"))
                .thenThrow(new RuntimeException("Connection error"));

        controller.loadAvailableParkingSpaces("Zone1");

        assertEquals("Failed to load parking spaces due to connection issues.", controller.checkStatusMessageLabel.getText());
        assertTrue(controller.parkingSpaceComboBox.getItems().isEmpty(), "Parking space combo box should be empty on failure.");
    }

    /**
     * Tests the successful loading of parking spaces for a selected zone.
     * Ensures the spaces are correctly loaded into the parking space ComboBox.
     */
    @Test
    public void testLoadAvailableParkingSpaces_Success() throws Exception {
        controller.zoneComboBox.setValue("Zone1");

        when(rabbitMQSenderMock.loadParkingLists("GET_ALL_PARKING_SPACES", "Zone1"))
                .thenReturn(List.of("SpaceA", "SpaceB"));

        controller.loadAvailableParkingSpaces("Zone1");

        assertEquals(2, controller.parkingSpaceComboBox.getItems().size());
        assertEquals("SpaceA", controller.parkingSpaceComboBox.getItems().get(0));
    }

    /**
     * Tests the failure scenario when issuing a citation due to connection issues.
     * Ensures an appropriate error message is displayed.
     */
    @Test
    public void testIssueCitation_Failure() throws Exception {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("ABC123");
        controller.citationAmountField.setText("50");

        DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime mockTime = LocalDateTime.now();
        String formattedMockTime = mockTime.format(formatterWithoutSeconds);

        when(rabbitMQSenderMock.sendCitation("ISSUE_CITATION:Zone1,SpaceA,ABC123,50," + formattedMockTime))
                .thenThrow(new RuntimeException("Connection error"));

        controller.issueCitation();

        assertEquals("Failed to issue citation due to connection issues.", controller.citationStatusMessageLabel.getText());
    }

    /**
     * Tests the validation for missing fields when checking parking status.
     * Ensures an appropriate error message is displayed.
     */
    @Test
    public void testCheckParkingStatus_MissingFields() {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue(null);
        controller.licensePlateField.setText("");

        controller.checkParkingStatus();

        assertEquals("All Fields Are REQUIRED.", controller.checkStatusMessageLabel.getText());
    }

    /**
     * Tests the reset of all fields and inputs to their initial state.
     * Ensures that all fields and buttons are correctly disabled and cleared.
     */
    @Test
    public void testResetFieldsToInitialState() {
        controller.zoneComboBox.setValue("Zone1");
        controller.parkingSpaceComboBox.setValue("SpaceA");
        controller.licensePlateField.setText("ABC123");
        controller.citationAmountField.setText("50");

        controller.resetFieldsToInitialState();

        assertNull(controller.zoneComboBox.getValue());
        assertTrue(controller.parkingSpaceComboBox.isDisabled());
        assertTrue(controller.licensePlateField.isDisabled());
        assertTrue(controller.citationAmountField.isDisabled());
        assertTrue(controller.issueCitationButton.isDisabled());
        assertTrue(controller.cancelCitationButton.isDisabled());
        assertEquals("", controller.checkStatusMessageLabel.getText());
        assertEquals("", controller.citationStatusMessageLabel.getText());
    }

}
