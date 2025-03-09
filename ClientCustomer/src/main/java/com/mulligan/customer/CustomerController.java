package com.mulligan.customer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mulligan.common.Units.RabbitMQSender;
import com.mulligan.common.models.ParkingEvent;

import com.mulligan.common.models.ParkingSpaceRecommendation;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller for handling customer interactions in the Mulligan parking system.
 * Responsible for user input, validation, and communication with the RabbitMQ server.
 * Key Features:
 * Validates parking spaces and zones.
 * Sends start and stop parking requests to the server via RabbitMQ.
 * Loads parking zones and available parking spaces dynamically.
 *
 * @author Jamal Majadle
 * @version 1.19.0
 */
public class CustomerController {

    @FXML
    Button recommendButton;
    @FXML
    TableView<ParkingSpaceRecommendation> recommendationTable;
    @FXML
    TableColumn<ParkingSpaceRecommendation, String> recSpaceColumn;
    @FXML
    TableColumn<ParkingSpaceRecommendation, Integer> recCitationColumn;
    @FXML
    Button fetchEventsButton;
    @FXML
    TableView<ParkingEvent> parkingEventsTable;
    @FXML
    TableColumn<ParkingEvent, String> vehicleNumberColumn;
    @FXML
    TableColumn<ParkingEvent, String> ownerColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingStartTimeColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingEndTimeColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingDurationColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingCostColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingZoneColumn;
    @FXML
    TableColumn<ParkingEvent, String> parkingSpaceColumn;



    @FXML
    ComboBox<String> zoneComboBox;
    @FXML
    ComboBox<String> parkingSpaceComboBox;
    @FXML
    TextField licensePlateField;
    @FXML
    private Button startButton;
    @FXML
    private Button stopButton;
    @FXML
    Label messageLabel;

    RabbitMQSender rabbitMQSender;
    private static final Logger logger = Logger.getLogger(CustomerController.class.getName());

    /**
     * Initializes the CustomerController.
     * - Sets up RabbitMQ communication.
     * - Binds UI elements to their respective actions.
     * - Disables fields that depend on prior user selections.
     */
    @FXML
    public void initialize() {
        try {
            initializeTable();
            clearTable();
            rabbitMQSender = new RabbitMQSender();
            loadParkingZones();
            parkingSpaceComboBox.setDisable(true);
            licensePlateField.setDisable(false);
            startButton.setOnAction(event -> startParking());
            stopButton.setOnAction(event -> stopParking());
            licensePlateField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || newValue.trim().isEmpty()) {
                    clearTable();
                }
            });
            recommendButton.setOnAction(event -> fetchRecommendations());

        } catch (IOException | TimeoutException e) {
            messageLabel.setText("Failed to initialize RabbitMQ connection.");
            logger.log(Level.SEVERE, "Failed to initialize RabbitMQSender", e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Starts a parking session for the customer.
     * Sends a request to RabbitMQ with the parking space, vehicle details, and current time.
     */
    protected void startParking() {
        clearTable();
        String parkingSpace = parkingSpaceComboBox.getValue();
        String licensePlate = licensePlateField.getText();
        String parkingZoneField = zoneComboBox.getValue();

        if (parkingSpace.isEmpty() || licensePlate.isEmpty() || parkingZoneField.isEmpty()) {
            messageLabel.setText("All Fields Are REQUIRED.");
            return;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime currentTime = LocalDateTime.now();
            String formattedTime = currentTime.format(formatter);

            switch (validateParkingSpace(parkingZoneField, parkingSpace)) {
                case "AVAILABLE":
                    String startResponse = rabbitMQSender.sendTransaction(
                            "START_PARKING:" + licensePlate + "," + parkingZoneField + "," + parkingSpace + "," + formattedTime);
                    messageLabel.setText("Parking request status: " + startResponse);
                    break;
                case "OCCUPIED":
                    messageLabel.setText("Parking space is already occupied.");
                    break;
                case "INVALID":
                    messageLabel.setText("Invalid Parking Space.");
                    break;
                default:
                    messageLabel.setText("Failed to send parking request.");
                    break;
            }
        } catch (IOException | TimeoutException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to start parking request", e);
            messageLabel.setText("Failed to send parking request due to connection issues.");
        }
    }

    /**
     * Stops a parking session for the customer.
     * Sends a request to RabbitMQ to stop parking for the selected space and vehicle.
     */
    protected void stopParking() {
        clearTable();
        String parkingSpace = parkingSpaceComboBox.getValue();
        String licensePlate = licensePlateField.getText();
        String parkingZoneField = zoneComboBox.getValue();

        if (parkingSpace.isEmpty() || licensePlate.isEmpty()) {
            messageLabel.setText("All Fields Are REQUIRED.");
            return;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
            LocalDateTime currentTime = LocalDateTime.now();
            String formattedTime = currentTime.format(formatter);

            switch (validateParkingSpace(parkingZoneField, parkingSpace)) {
                case "AVAILABLE":
                    messageLabel.setText("Parking space is not occupied.");
                    break;
                case "OCCUPIED":
                    String stopResponse = rabbitMQSender.sendTransaction(
                            "STOP_PARKING:" + licensePlate + "," + parkingZoneField + "," + parkingSpace + "," + formattedTime);
                    messageLabel.setText("Parking request status: " + stopResponse);
                    break;
                case "INVALID":
                    messageLabel.setText("Invalid Parking Space.");
                    break;
                default:
                    messageLabel.setText("Failed to send stop parking request.");
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send stop parking request", e);
            messageLabel.setText("Failed to send stop parking request.");
        }
    }

    /**
     * Validates the selected parking space.
     * Sends a request to RabbitMQ to check if the parking space is available, occupied, or invalid.
     *
     * @param parkingZoneField The selected parking zone.
     * @param parkingSpace     The parking space to validate.
     * @return A string indicating the parking space's status ("AVAILABLE", "OCCUPIED", "INVALID").
     * @throws IOException          If RabbitMQ communication fails.
     * @throws TimeoutException     If RabbitMQ request times out.
     * @throws InterruptedException If the thread is interrupted.
     */
    protected String validateParkingSpace(String parkingZoneField, String parkingSpace)
            throws IOException, TimeoutException, InterruptedException {
        String response = rabbitMQSender.sendTransaction("CHECK_PARKING_AVAILABILITY:" + parkingZoneField + "," + parkingSpace);
        return response != null ? response : "ERROR";
    }

    /**
     * Loads the available parking zones into the dropdown menu.
     */
    protected void loadParkingZones() {
        List<String> zones = rabbitMQSender.loadParkingLists("GET_ALL_PARKING_ZONES:");
        if (zones == null) {
            messageLabel.setText("Failed to load parking zones.");
            return;
        }
        zoneComboBox.getItems().addAll(zones);
    }

    /**
     * Handles selection of a parking zone.
     * Enables the parking space dropdown and loads available spaces.
     */
    @FXML
    protected void onZoneSelected() {
        clearTable();
        String selectedZone = zoneComboBox.getValue();
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
        parkingSpaceComboBox.getItems().clear();
        List<String> availableSpaces = rabbitMQSender.loadParkingLists("GET_ALL_PARKING_SPACES", zone);
        if (availableSpaces == null) {
            messageLabel.setText("Failed to load parking spaces.");
            return;
        }
        parkingSpaceComboBox.getItems().addAll(availableSpaces);
    }

    /**
     * Fetches and displays parking events associated with the entered license plate.
     */
    @FXML
    protected void onFetchParkingEventsClicked() {
        clearTable();
        if (licensePlateField.getText().isEmpty()) {
            messageLabel.setText("Please enter a license plate number.");
            return;
        }
        try {
            List<String> eventData = rabbitMQSender.loadParkingLists("GET_PARKING_EVENTS", licensePlateField.getText());
            ObservableList<ParkingEvent> events = FXCollections.observableArrayList();

            for (String rawBatch : eventData) {
                String[] records = rawBatch.split("\n");
                for (String rawEvent : records) {
                    String[] fields = rawEvent.split("\\|");

                    if (fields.length != 11) {
                        Logger.getLogger(CustomerController.class.getName())
                                .log(Level.WARNING, "Skipping incomplete event data. Expected 11 fields but got " + fields.length + ": " + rawEvent);
                        continue;
                    }

                    String vehicleNumber = fields[0].trim();
                    String owner = fields[1].trim();
                    String zone = fields[2].trim();
                    String space = fields[3].trim();
                    String parkingStartTime = fields[4].trim();
                    String parkingEndTime = fields[5].trim();
                    String parkingDuration = fields[6].trim();
                    String parkingCost = fields[7].trim();
                    String citationIssueTime = fields[8].isEmpty() ? "N/A" : fields[8].trim();
                    String citationAmount = fields[9].isEmpty() ? "0.0" : fields[9].trim();
                    String citationReason = fields[10].isEmpty() ? "N/A" : fields[10].trim();

                    events.add(new ParkingEvent(
                            vehicleNumber,
                            owner,
                            zone,
                            space,
                            parkingStartTime.isEmpty() ? "N/A" : parkingStartTime,
                            parkingEndTime.isEmpty() ? "Ongoing" : parkingEndTime,
                            parkingDuration.isEmpty() ? "0" : parkingDuration,
                            parkingCost.isEmpty() ? "$0.00" : parkingCost,
                            citationIssueTime,
                            citationAmount,
                            citationReason
                    ));
                }
            }

            if (events.isEmpty()) {
                messageLabel.setText("No valid parking events found.");
            } else {
                parkingEventsTable.setItems(events);
            }
        } catch (Exception e) {
            messageLabel.setText("Failed to fetch parking events.");
            Logger.getLogger(CustomerController.class.getName()).log(Level.SEVERE, "Error fetching events", e);
        }
    }

    private void initializeTable() {
        vehicleNumberColumn.setCellValueFactory(new PropertyValueFactory<>("vehicleNumber"));
        ownerColumn.setCellValueFactory(new PropertyValueFactory<>("owner"));
        parkingZoneColumn.setCellValueFactory(new PropertyValueFactory<>("parkingZone"));
        parkingSpaceColumn.setCellValueFactory(new PropertyValueFactory<>("parkingSpace"));
        parkingStartTimeColumn.setCellValueFactory(new PropertyValueFactory<>("parkingStartTime"));
        parkingEndTimeColumn.setCellValueFactory(new PropertyValueFactory<>("parkingEndTime"));
        parkingDurationColumn.setCellValueFactory(new PropertyValueFactory<>("parkingDuration"));
        parkingCostColumn.setCellValueFactory(new PropertyValueFactory<>("parkingCost"));
        recSpaceColumn.setCellValueFactory(new PropertyValueFactory<>("space"));
        recCitationColumn.setCellValueFactory(new PropertyValueFactory<>("citations"));
    }


    private void clearTable() {
        parkingEventsTable.getItems().clear();
        recommendationTable.getItems().clear();
    }

    /**
     * Fetches parking space recommendations based on the selected zone and parking space.
     * This method validates user input, sends a recommendation request via RabbitMQ,
     * and updates the UI table with the received recommendations.
     * If the user fails to provide valid input, an error message is displayed.
     * If no recommendations are received, a corresponding message is shown.
     */
    private void fetchRecommendations() {
        clearTable();
        String zone = zoneComboBox.getValue();
        String space = parkingSpaceComboBox.getValue();
        String vehicle = licensePlateField.getText();

        if (zone == null || space == null || zone.isEmpty() || space.isEmpty()) {
            messageLabel.setText("Please fill all fields to get recommendations.");
            return;
        }

        try {
            String message = zone + ":" + space;
            String response = rabbitMQSender.sendRecommendations(message);

            if (response == null || response.isEmpty()) {
                messageLabel.setText("No recommendations received.");
                return;
            }

            ObservableList<ParkingSpaceRecommendation> recommendations = parseRecommendations(response);
            recommendationTable.setItems(recommendations);
            messageLabel.setText("Recommendations fetched successfully.");

        } catch (Exception e) {
            messageLabel.setText("Failed to fetch recommendations.");
            Logger.getLogger(CustomerController.class.getName()).log(Level.SEVERE, "Error fetching recommendations", e);
        }
    }

    /**
     * Parses the recommendation response received from the recommendation system.
     * The response is expected to be a comma-separated list of recommendations,
     * where each recommendation is in the format:
     * parking_space_id;citation_count
     * This method splits and processes the recommendations, converting them into
     * {@link ParkingSpaceRecommendation} objects, which are then returned as an observable list.
     * If a recommendation entry is malformed or cannot be parsed, it is skipped, and a warning is logged.
     *
     * @param response The raw response string received from the recommendation system.
     * @return An {@link ObservableList} containing parsed {@link ParkingSpaceRecommendation} objects.
     */
    private ObservableList<ParkingSpaceRecommendation> parseRecommendations(String response) {
        ObservableList<ParkingSpaceRecommendation> recommendationList = FXCollections.observableArrayList();
        if (response == null || response.isEmpty()) {
            return recommendationList;
        }
        String[] recommendations = response.split(",");
        for (String recommendation : recommendations) {
            try {
                String[] parts = recommendation.trim().split(";");
                if (parts.length != 2) {
                    logger.warning("Skipping malformed recommendation: " + recommendation);
                    continue;
                }
                String space = parts[0];
                int citations = Integer.parseInt(parts[1]);
                recommendationList.add(new ParkingSpaceRecommendation(space, citations));
            } catch (Exception e) {
                logger.warning("Failed to parse recommendation: " + recommendation);
            }
        }
        return recommendationList;
    }


}
