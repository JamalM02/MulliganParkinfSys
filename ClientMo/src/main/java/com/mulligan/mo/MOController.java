package com.mulligan.mo;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import com.mulligan.common.Units.RabbitMQSender;
import com.mulligan.common.models.Citation;
import com.mulligan.common.models.Transaction;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

/**
 * Controller class for the Municipality Officer (MO) application.
 * This class manages the user interface and interactions for retrieving and displaying
 * parking transactions and citations.
 *
 * Key Features:
 * 
 *   Retrieves transaction and citation reports from the RabbitMQ server.
 *   Displays data in tables using JavaFX TableView components.
 *   Handles button actions for generating reports.
 * 
 *
 * @author Jamal Majadle
 * @version 1.9.0
 */
public class MOController {

    @FXML
    private Button getTransactionReportButton;

    @FXML
    private Button getCitationReportButton;

    @FXML
    TableView<Transaction> transactionTable;

    @FXML
     TableColumn<Transaction, Integer> transactionIdColumn;
    @FXML
     TableColumn<Transaction, Integer> transactionVehicleColumn;
    @FXML
     TableColumn<Transaction, String> transactionParkingSpaceColumn;
    @FXML
     TableColumn<Transaction, String> transactionZoneColumn;
    @FXML
     TableColumn<Transaction, String> transactionStartColumn;
    @FXML
     TableColumn<Transaction, String> transactionEndColumn;
    @FXML
     TableColumn<Transaction, Double> transactionAmountColumn;

    @FXML
     TableView<Citation> citationTable;

    @FXML
     TableColumn<Citation, Integer> citationIdColumn;
    @FXML
     TableColumn<Citation, Integer> citationVehicleColumn;
    @FXML
     TableColumn<Citation, String> citationParkingSpaceColumn;
    @FXML
     TableColumn<Citation, String> citationZoneColumn;
    @FXML
     TableColumn<Citation, String> citationIssueTimeColumn;
    @FXML
     TableColumn<Citation, Double> citationAmountColumn;
    @FXML
    TableColumn<Citation, String> citationReasonColumn;


    RabbitMQSender rabbitMQSender;

    /**
     * Constructor for MOController.
     * Initializes the RabbitMQ connection for communication with the backend.
     */
    public MOController() {
        try {
            rabbitMQSender = new RabbitMQSender();
        } catch (IOException | TimeoutException | InterruptedException e) {
            showAlert("Error", "Failed to connect to RabbitMQ: " + e.getMessage());
        }
    }

    /**
     * Initializes the controller.
     * Sets up the transaction and citation tables and binds button actions.
     */
    @FXML
    protected void initialize() {
        setupTransactionTable();
        setupCitationTable();

        getTransactionReportButton.setOnAction(event -> requestTransactionReport());
        getCitationReportButton.setOnAction(event -> requestCitationReport());
    }

    /**
     * Requests and displays the transaction report.
     * Retrieves data from RabbitMQ and populates the transaction table.
     */
    protected void requestTransactionReport() {
        try {
            List<Transaction> transactions = rabbitMQSender.requestTransactions();
            transactionTable.getItems().setAll(transactions);
        } catch (Exception e) {
            showAlert("Error", "Failed to retrieve transaction report: " + e.getMessage());
        }
    }

    /**
     * Requests and displays the citation report.
     * Retrieves data from RabbitMQ and populates the citation table.
     */
    protected void requestCitationReport() {
        try {
            List<Citation> citations = rabbitMQSender.requestCitations();
            citationTable.getItems().setAll(citations);
        } catch (Exception e) {
            showAlert("Error", "Failed to retrieve citation report: " + e.getMessage());
        }
    }

    /**
     * Sets up the transaction table with the appropriate column bindings.
     */
    protected void setupTransactionTable() {
        transactionVehicleColumn.setCellValueFactory(new PropertyValueFactory<>("licensePlate"));
        transactionParkingSpaceColumn.setCellValueFactory(new PropertyValueFactory<>("parkingSpace"));
        transactionZoneColumn.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        transactionStartColumn.setCellValueFactory(new PropertyValueFactory<>("start"));
        transactionEndColumn.setCellValueFactory(new PropertyValueFactory<>("end"));
        transactionAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));

    }

    /**
     * Sets up the citation table with the appropriate column bindings.
     */
    protected void setupCitationTable() {
        citationVehicleColumn.setCellValueFactory(new PropertyValueFactory<>("licensePlate"));
        citationParkingSpaceColumn.setCellValueFactory(new PropertyValueFactory<>("parkingSpace"));
        citationZoneColumn.setCellValueFactory(new PropertyValueFactory<>("zoneName"));
        citationIssueTimeColumn.setCellValueFactory(new PropertyValueFactory<>("issueTime"));
        citationAmountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        citationReasonColumn.setCellValueFactory(new PropertyValueFactory<>("reason"));
    }

    /**
     * Displays an alert with the specified title and message.
     *
     * @param title   The title of the alert dialog.
     * @param message The message to be displayed in the alert dialog.
     */
    protected void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
