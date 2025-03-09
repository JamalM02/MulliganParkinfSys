package com.mulligan.customer;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application entry point for the Mulligan Customer Parking System.
 * This application uses JavaFX to provide a graphical user interface (GUI) for customers.
 *
 * Key Features:
 * 
 *   Allows customers to interact with the parking system.
 *   Supports starting and stopping parking events through the UI.
 *   Loads the interface layout from an FXML file.
 * 
 *
 * @author Jamal Majadle
 * @version 1.6.0
 */
public class CustomerApp extends Application {

    /**
     * Initializes and displays the main stage of the application.
     * This method loads the FXML layout, sets the stage title, scene, and dimensions,
     * and handles application exit on window close.
     *
     * @param primaryStage The primary stage for the JavaFX application.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/CustomerView.fxml"));
            primaryStage.setTitle("Customer Parking UI");
            primaryStage.setScene(new Scene(loader.load(), 1000, 500));

            primaryStage.setMinWidth(400);
            primaryStage.setMinHeight(300);

            primaryStage.setOnCloseRequest(event -> System.exit(0));

            primaryStage.show();
        } catch (IOException e) {
            Logger.getLogger(CustomerApp.class.getName()).log(Level.SEVERE, "Failed to load FXML", e);
            System.err.println("Error loading FXML file: " + e.getMessage());
        }
    }

    /**
     * The main entry point for the Java application.
     * This method launches the JavaFX application by invoking the {@code launch} method.
     *
     * @param args Command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
