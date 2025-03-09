package com.mulligan.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.Arrays;

/**
 * Fetches transaction statistics grouped dynamically by hour.
 *
 * @author Matan shabi
 * @version 1.1
 */
public class TransactionStatsByHour {
    /**
     * Fetches transaction statistics grouped by hour from the `start` field.
     *
     * @param database the MongoDB database
     */
    public static void fetchTransactionStats(MongoDatabase database) {
        MongoCollection<Document> transactions = database.getCollection("transactions");

        // Extract the hour from the `start` field
        Bson addHourField = new Document("$addFields",
                new Document("Hour",
                        new Document("$hour", "$start")
                )
        );

        // Group transactions by extracted hour and count them
        Bson groupByHour = new Document("$group",
                new Document("_id", "$Hour")
                        .append("Transactions", new Document("$sum", 1))
        );

        // Sort by hour for better readability
        Bson sortByHour = new Document("$sort", new Document("_id", 1));

        // Run the aggregation pipeline
        try (MongoCursor<Document> cursor = transactions.aggregate(Arrays.asList(
                addHourField,
                groupByHour,
                sortByHour
        )).iterator()) {
            // Print results
            while (cursor.hasNext()) {
                System.out.println(cursor.next().toJson());
            }
        } catch (Exception e) {
            System.err.println("Error fetching transaction statistics: " + e.getMessage());
        }
    }
}
