package com.mulligan.peo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mulligan.common.Units.RabbitMQSender;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

/**
 * Controller class for the Parking Enforcement Officer (PEO) application.
 * This class handles the user interface and interactions for parking enforcement tasks,
 * including checking parking status and issuing citations.
 *
 * Key Features:
 * 
 *   Retrieve and display available parking zones and spaces.
 *   Check the parking status of a vehicle in a specific parking space.
 *   Issue citations for parking violations.
 *   Cancel citation issuance and reset the form.
 * 
 *
 * @version 1.8.0
 * @author Jamal Majadle
 */
public class PEOController {

    @FXML
     ComboBox<String> zoneComboBox;

    @FXML
     ComboBox<String> parkingSpaceComboBox;

    @FXML
     TextField licensePlateField;

    @FXML
     TextField citationAmountField;

    @FXML
     Button checkStatusButton;

    @FXML
     Button issueCitationButton;

    @FXML
     Button cancelCitationButton;

    @FXML
     Label checkStatusMessageLabel;

    @FXML
     Label citationStatusMessageLabel;

    private String licensePlate;
    private String selectedZone;
    private String selectedSpace;

     RabbitMQSender rabbitMQSender;
    private static final Logger logger = Logger.getLogger(PEOController.class.getName());

    /**
     * Initializes the PEOController.
     * Sets up the RabbitMQ connection and binds UI components to their respective actions.
     */
    @FXML
    public void initialize() {
        try {
            rabbitMQSender = new RabbitMQSender();
            citationStatusMessageLabel.setWrapText(true);
            citationStatusMessageLabel.setPrefWidth(300);
            loadParkingZones();
            parkingSpaceComboBox.setDisable(true);
            licensePlateField.setDisable(true);
            citationAmountField.setDisable(true);
            cancelCitationButton.setDisable(true);

            checkStatusButton.setOnAction(event -> checkParkingStatus());
            issueCitationButton.setOnAction(event -> issueCitation());
            cancelCitationButton.setOnAction(this::cancelCitation);
        } catch (IOException | TimeoutException | InterruptedException e) {
            checkStatusMessageLabel.setText("Failed to initialize RabbitMQ connection.");
            logger.log(Level.SEVERE, "Failed to initialize RabbitMQSender", e);
        }
    }

    /**
     * Loads available parking zones into the dropdown menu.
     * Displays an error message if the zones cannot be loaded.
     */
    protected void loadParkingZones() {
        try {
            List<String> zones = rabbitMQSender.loadParkingLists("GET_ALL_PARKING_ZONES:");
            if (zones == null || zones.isEmpty()) {
                checkStatusMessageLabel.setText("Failed to load parking zones.");
            } else {
                zoneComboBox.getItems().addAll(zones);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load parking zones", e);
            checkStatusMessageLabel.setText("Failed to load parking zones due to connection issues.");
        }
    }

    /**
     * Triggered when a parking zone is selected.
     * Loads available parking spaces for the selected zone.
     */
    @FXML
    protected void onZoneSelected() {
        selectedZone = zoneComboBox.getValue();
        if (selectedZone != null) {
            parkingSpaceComboBox.setDisable(false);
            loadAvailableParkingSpaces(selectedZone);
        }
    }

    /**
     * Loads available parking spaces for the selected zone.
     *
     * @param zone The selected parking zone.
     */
    protected void loadAvailableParkingSpaces(String zone) {
        try {
            List<String> availableSpaces = rabbitMQSender.loadParkingLists("GET_ALL_PARKING_SPACES", zone);
            if (availableSpaces == null || availableSpaces.isEmpty()) {
                checkStatusMessageLabel.setText("Failed to load parking spaces.");
            } else {
                parkingSpaceComboBox.getItems().clear();
                parkingSpaceComboBox.getItems().addAll(availableSpaces);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to load parking spaces", e);
            checkStatusMessageLabel.setText("Failed to load parking spaces due to connection issues.");
        }
    }

    /**
     * Triggered when a parking space is selected.
     * Enables the vehicle license plate input field.
     */
    @FXML
    protected void onParkingSpaceSelected() {
        selectedSpace = parkingSpaceComboBox.getValue();
        if (selectedSpace != null) {
            licensePlateField.setDisable(false);
        }
    }

    /**
     * Checks the parking status of the selected parking space and vehicle.
     * Displays the result in the status message label.
     */
    protected void checkParkingStatus() {
        licensePlate = licensePlateField.getText();
        selectedZone = zoneComboBox.getValue();
        selectedSpace = parkingSpaceComboBox.getValue();

        if (selectedZone == null || selectedSpace == null || licensePlate == null || licensePlate.isEmpty()) {
            checkStatusMessageLabel.setText("All Fields Are REQUIRED.");
            return;
        }

        try {
            String response = rabbitMQSender.sendCitation("CHECK_PARK_STATUS:" + selectedZone + "," + selectedSpace + "," + licensePlate);
            if ("Parking Ok".equals(response)) {
                checkStatusMessageLabel.setText("Vehicle is parked correctly.");
                resetCitationInputs(false);
            } else if ("Parking Not Ok".equals(response)) {
                checkStatusMessageLabel.setText("Vehicle is NOT parked correctly.");
                resetCitationInputs(true);
            } else {
                checkStatusMessageLabel.setText("Failed to check parking status.");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to check parking status", e);
            checkStatusMessageLabel.setText("Failed to check parking status due to connection issues.");
        }
    }

    /**
     * Issues a citation for an improperly parked vehicle.
     * Displays the response in the citation status label.
     */
    protected void issueCitation() {
        String citationAmount = citationAmountField.getText();
        licensePlate = licensePlateField.getText();
        selectedZone = zoneComboBox.getValue();
        selectedSpace = parkingSpaceComboBox.getValue();
        DateTimeFormatter formatterWithoutSeconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        LocalDateTime mockTime = LocalDateTime.now();
        String formattedMockTime = mockTime.format(formatterWithoutSeconds);

        if (selectedZone == null || selectedSpace == null || licensePlate == null || licensePlate.isEmpty() || citationAmount == null || citationAmount.isEmpty()) {
            citationStatusMessageLabel.setText("All Fields Are REQUIRED.");
            return;
        }

        try {
            String response = rabbitMQSender.sendCitation("ISSUE_CITATION:" + selectedZone + "," + selectedSpace + "," + licensePlate + "," + citationAmount + "," + formattedMockTime);
            citationStatusMessageLabel.setText(response);
            resetFieldsToInitialState();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to issue citation", e);
            citationStatusMessageLabel.setText("Failed to issue citation due to connection issues.");
        }
    }

    /**
     * Cancels the citation process and resets the form.
     *
     * @param event The ActionEvent triggering the cancellation.
     */
    protected void cancelCitation(ActionEvent event) {
        resetFieldsToInitialState();
        citationStatusMessageLabel.setText("Citation issuance canceled.");
    }

    /**
     * Resets all fields and inputs to their initial state.
     */
    protected void resetFieldsToInitialState() {
        zoneComboBox.setDisable(false);
        zoneComboBox.setValue(null);

        parkingSpaceComboBox.setDisable(true);
        parkingSpaceComboBox.getItems().clear();

        licensePlateField.clear();
        licensePlateField.setDisable(true);

        citationAmountField.clear();
        citationAmountField.setDisable(true);

        issueCitationButton.setDisable(true);
        cancelCitationButton.setDisable(true);

        checkStatusMessageLabel.setText("");
        citationStatusMessageLabel.setDisable(true);
    }

    /**
     * Resets citation-related inputs based on the parking status check.
     *
     * @param enable Indicates whether to enable citation inputs.
     */
    private void resetCitationInputs(boolean enable) {
        zoneComboBox.setDisable(enable);
        parkingSpaceComboBox.setDisable(enable);
        licensePlateField.setDisable(enable);
        citationStatusMessageLabel.setText("");
        citationStatusMessageLabel.setDisable(true);
        citationAmountField.setDisable(!enable);
        issueCitationButton.setDisable(!enable);
        cancelCitationButton.setDisable(!enable);
    }
}
