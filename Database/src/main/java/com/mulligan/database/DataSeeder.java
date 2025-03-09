package com.mulligan.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class to seed data into the MongoDB database.
 *
 * @author Matan shabi
 * @version 1.0
 */
public class DataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /**
     * Main method to seed data into the MongoDB database.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        MongoDBConnector dbConnector = MongoDBConnector.getInstance();
        MongoDatabase database = dbConnector.getDatabase();
        ClientSession session = dbConnector.getMongoClient().startSession();

        try {
            session.startTransaction();

            // Seed Data
            seedDataFromFile(session, database.getCollection("vehicles"), "Database/sample_data/vehicles.json");
            seedDataFromFile(session, database.getCollection("parking_zones"), "Database/sample_data/parking_zones.json");
            seedDataFromFile(session, database.getCollection("parking_spaces"), "Database/sample_data/parking_spaces.json");

            // Commit the transaction
            session.commitTransaction();
            log.info("Data seeded successfully in a transactional manner.");
        } catch (Exception e) {
            session.abortTransaction();
            log.error("Error during transaction: {}", e.getMessage());
        } finally {
            session.close();
            dbConnector.close();
        }
    }

    /**
     * Seed data from a file into a collection.
     *
     * @param session    the client session
     * @param collection the collection to seed data into
     * @param filePath   the path to the file containing the data
     */
    public static void seedDataFromFile(ClientSession session, MongoCollection<Document> collection, String filePath) {
        try {
            // Parse JSON file into a list of documents using Jackson
            ObjectMapper objectMapper = new ObjectMapper();
            List<Document> documents = objectMapper.readValue(new File(filePath), new TypeReference<List<Document>>() {});

            // Insert documents into the collection
            collection.insertMany(session, documents);
        } catch (IOException e) {
            log.error("Failed to read file: {}. Error: {}", filePath, e.getMessage());
        } catch (Exception e) {
            log.error("Failed to seed data from file: {}. Error: {}", filePath, e.getMessage());
        }
    }
}
