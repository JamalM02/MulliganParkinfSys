package com.mulligan.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fetches available parking spots from the database
 * @author  Matan shabi
 * @version 1.0
 */

public class AvailableParkingSpots {
    /**
     * Fetches available parking spots from the database
     *
     * @param database the MongoDB database
     */
    public static void fetchAvailableSpots(MongoDatabase database) {
        MongoCollection<Document> parkingSpaces = database.getCollection("parking_spaces");

        Bson lookupParkingZones = new Document("$lookup",
                new Document("from", "parking_zones")
                        .append("localField", "zone_id")
                        .append("foreignField", "id")
                        .append("as", "zone_info")
        );

        Bson unwindZoneInfo = new Document("$unwind", "$zone_info");

        Bson lookupActiveTransactions = new Document("$lookup",
                new Document("from", "transactions")
                        .append("localField", "id")
                        .append("foreignField", "parking_space_id")
                        .append("pipeline", Arrays.asList(
                                new Document("$match", new Document("end", new Document("$exists", false)))
                        ))
                        .append("as", "active_transaction")
        );

        Bson filterAvailable = new Document("$match", new Document("active_transaction", new Document("$size", 0)));

        Bson projectFields = new Document("$project",
                new Document("_id", 0)
                        .append("parking_space", "$post_id")
                        .append("parking_zone", "$zone_info.zone_name")
                        .append("hourly_rate", "$zone_info.hourly_rate")
                        .append("max_parking_duration", "$zone_info.max_parking_minutes")
        );

        try (MongoCursor<Document> cursor = parkingSpaces.aggregate(Arrays.asList(
                lookupParkingZones,
                unwindZoneInfo,
                lookupActiveTransactions,
                filterAvailable,
                projectFields
        )).iterator()) {
            while (cursor.hasNext()) {
                System.out.println(cursor.next().toJson());
            }
        }
    }

    // New method to get parking recommendations
    public static List<Document> getParkingRecommendations(MongoDatabase database, int desiredSpaceId) {
        MongoCollection<Document> parkingSpaces = database.getCollection("parking_spaces");

        Bson lookupCitations = new Document("$lookup",
                new Document("from", "transactions")
                        .append("localField", "id")
                        .append("foreignField", "parking_space_id")
                        .append("pipeline", Arrays.asList(
                                new Document("$match", new Document("end", new Document("$exists", false)))
                        ))
                        .append("as", "active_transactions"));

        Bson lookupZoneInfo = new Document("$lookup",
                new Document("from", "parking_zones")
                        .append("localField", "zone_id")
                        .append("foreignField", "id")
                        .append("as", "zone_info"));

        Bson unwindZoneInfo = new Document("$unwind", "$zone_info");
        Bson calculateCitations = new Document("$addFields",
                new Document("citationCount", new Document("$size", "$active_transactions")));
        Bson sortCriteria = new Document("$sort",
                new Document("citationCount", 1).append("proximity", 1));

        Bson matchDesiredZone = new Document("$match", new Document("id", desiredSpaceId));

        List<Bson> pipeline = Arrays.asList(
                lookupCitations,
                lookupZoneInfo,
                unwindZoneInfo,
                calculateCitations,
                matchDesiredZone,
                sortCriteria
        );

        List<Document> recommendations = new ArrayList<>();
        try (MongoCursor<Document> cursor = parkingSpaces.aggregate(pipeline).iterator()) {
            while (cursor.hasNext()) {
                recommendations.add(cursor.next());
            }
        }

        return recommendations;
    }
}
