package com.mulligan.peo;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the Parking Enforcement Officer (PEO) application.
 * This application provides a graphical user interface (GUI) for PEOs to:
 * 
 *   Check parking compliance for vehicles.
 *   Issue citations for violations.
 * 
 * Key Features:
 * 
 *   Loads the PEO interface from an FXML layout file.
 *   Handles initialization and setup of the JavaFX application.
 * 
 * @version 1.6.0
 * @author Jamal Majadle
 */
public class ClientPeoMain extends Application {

    /**
     * Initializes and starts the PEO application.
     * Loads the FXML layout, sets the stage title, and displays the scene.
     *
     * @param primaryStage The primary stage for the application.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PEOView.fxml"));
            primaryStage.setTitle("Parking Enforcement Officer UI");
            primaryStage.setScene(new Scene(loader.load(), 400, 340));
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to load PEO interface.");
        }
    }

    /**
     * The main entry point for the PEO application.
     * Launches the JavaFX application.
     *
     * @param args Command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
