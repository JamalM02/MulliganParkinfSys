package com.mulligan.mo;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the Municipality Officer (MO) application.
 * This application provides a graphical user interface (GUI) for MOs to monitor parking transactions
 * and manage parking citations.
 *
 * Key Features:
 * 
 *   Displays transaction and citation reports.
 *   Interacts with backend services for retrieving parking-related data.
 *   Uses JavaFX for the GUI layout and event handling.
 * 
 *
 * @author Jamal Majadle
 * @version 1.6.0
 */
public class MOApp extends Application {

    /**
     * Starts the JavaFX application and initializes the primary stage.
     * Loads the FXML layout for the Municipality Officer Dashboard, configures stage properties,
     * and sets up application termination behavior on window close.
     *
     * @param primaryStage The main stage for the application.
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MOView.fxml"));
            primaryStage.setTitle("Municipality Officer Dashboard");
            primaryStage.setScene(new Scene(loader.load(), 800, 600));

            primaryStage.setMinWidth(800);
            primaryStage.setMinHeight(600);

            primaryStage.setOnCloseRequest(event -> System.exit(0));

            primaryStage.show();
        } catch (IOException e) {
            Logger.getLogger(MOApp.class.getName()).log(Level.SEVERE, "Failed to load FXML", e);
            System.err.println("Error loading FXML file: " + e.getMessage());
        }
    }

    /**
     * The main entry point for the application.
     * This method launches the JavaFX application by invoking the {@code launch} method.
     *
     * @param args Command-line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}
