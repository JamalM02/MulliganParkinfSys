package com.mulligan.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * MongoDB Connector class to connect to the MongoDB Atlas cluster.
 * @author  Matan shabi
 * @version 1.0
 */
public class MongoDBConnector {
    static Dotenv dotenv = Dotenv.load();

    private static final String CONNECTION_URI = dotenv.get("MONGO_URI");
    private static final String DATABASE_NAME = dotenv.get("DATABASE_NAME");
    private static MongoClient mongoClient;
    private static MongoDatabase database;

    private static MongoDBConnector instance;

    /**
     * Private constructor to create a new MongoDBConnector instance.
     */
    private MongoDBConnector() {
        mongoClient = MongoClients.create(CONNECTION_URI);

        database = mongoClient.getDatabase(DATABASE_NAME);
    }

    /**
     * Get the singleton instance of the MongoDBConnector.
     *
     * @return the MongoDBConnector instance
     */
    public static synchronized MongoDBConnector getInstance() {
        if (instance == null) {
            instance = new MongoDBConnector();
        }
        return instance;
    }

    /**
     * Get the MongoDB database.
     *
     * @return the MongoDB database
     */
    public MongoDatabase getDatabase() {
        return database;
    }

    /**
     * Get the MongoDB client.
     *
     * @return the MongoDB client
     */
    public MongoClient getMongoClient() {
        return mongoClient;
    }

    /**
     * Close the MongoDB client.
     */
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
