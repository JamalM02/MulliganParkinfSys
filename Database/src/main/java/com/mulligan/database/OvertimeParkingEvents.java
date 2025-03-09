package com.mulligan.database;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fetches parking events that have exceeded the maximum parking duration.
 *
 * @author Matan shabi
 * @version 1.0
 */
public class OvertimeParkingEvents {
    /**
     * Fetches parking events that have exceeded the maximum parking duration.
     *
     * @param database the MongoDB database
     */
    public static void fetchOvertimeEvents(MongoDatabase database) {
        MongoCollection<Document> transactions = database.getCollection("transactions");
        MongoCollection<Document> parkingZones = database.getCollection("parking_zones");

        // Join transactions with parking zones
        Bson lookupZoneInfo = new Document("$lookup",
                new Document("from", "parking_zones")
                        .append("localField", "parking_space_id")
                        .append("foreignField", "_id")
                        .append("as", "zone_info"));

        // Unwind zone_info array
        Bson unwindZoneInfo = new Document("$unwind", "$zone_info");

        // Add elapsed time in minutes
        Bson addElapsedMinutes = new Document("$addFields",
                new Document("elapsed_minutes",
                        new Document("$divide",
                                Arrays.asList(
                                        new Document("$subtract", Arrays.asList("$$NOW", "$start")),
                                        60000 // Convert milliseconds to minutes
                                )
                        )
                )
        );

        // Filter for overtime parking
        Bson filterOvertime = new Document("$match",
                new Document("$expr",
                        new Document("$gt", Arrays.asList("$elapsed_minutes", "$zone_info.max_parking_minutes"))
                )
        );

        // Project relevant fields
        Bson projectFields = new Document("$project",
                new Document("_id", 0)
                        .append("id", 1)
                        .append("start", 1)
                        .append("elapsed_minutes", 1)
                        .append("max_parking_minutes", "$zone_info.max_parking_minutes")
                        .append("zone_name", "$zone_info.zone_name")
        );

        // Build pipeline
        List<Bson> pipeline = Arrays.asList(lookupZoneInfo, unwindZoneInfo, addElapsedMinutes, filterOvertime, projectFields);

        // Execute aggregation
        List<Document> results = transactions.aggregate(pipeline).into(new ArrayList<>());

        // Print results
        for (Document result : results) {
            System.out.println(result.toJson());
        }
    }
}
