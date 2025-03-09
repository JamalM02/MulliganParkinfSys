package com.mulligan.database;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.ClientSession;
import org.bson.Document;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main class to seed data and run aggregations
 *
 * @author Matan shabi
 * @version 1.0
 */
public class Main {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(Main.class);

    /**
     * Main method to seed data and run aggregations
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {

        Logger mongoLogger = Logger.getLogger("org.mongodb.driver");
        mongoLogger.setLevel(Level.INFO); // Set to desired level

        String logLevel = System.getenv("MONGO_LOG_LEVEL"); // Get level from environment
        if ("DEBUG".equalsIgnoreCase(logLevel)) {
            mongoLogger.setLevel(Level.FINE);
        } else {
            mongoLogger.setLevel(Level.INFO);
        }

        MongoDBConnector dbConnector = MongoDBConnector.getInstance();
        MongoDatabase database = dbConnector.getDatabase();

        try {
            // Drop all collections before starting
            deleteAllCollections(database);

            // Collections
            MongoCollection<Document> vehicles = database.getCollection("vehicles");
            MongoCollection<Document> parkingZones = database.getCollection("parking_zones");
            MongoCollection<Document> parkingSpaces = database.getCollection("parking_spaces");

            // Seed Data for Vehicles, Parking Zones, and Parking Spaces
            ClientSession session = dbConnector.getMongoClient().startSession();


            DataSeeder.seedDataFromFile(session, vehicles, "Database/sample_data/vehicles.json");


            DataSeeder.seedDataFromFile(session, parkingZones, "Database/sample_data/parking_zones.json");


            DataSeeder.seedDataFromFile(session, parkingSpaces, "Database/sample_data/parking_spaces.json");

            // Aggregations

            OvertimeParkingEvents.fetchOvertimeEvents(database);


            AvailableParkingSpots.fetchAvailableSpots(database);


            TransactionStatsByHour.fetchTransactionStats(database);

        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
        } finally {
            dbConnector.close();
        }
    }

    /**
     * Deletes all collections in the database.
     *
     * @param database the MongoDB database
     */
    private static void deleteAllCollections(MongoDatabase database) {
        MongoIterable<String> collections = database.listCollectionNames();
        for (String collectionName : collections) {
            database.getCollection(collectionName).drop();
            log.info("Dropped collection: {}", collectionName);
        }
    }
}
