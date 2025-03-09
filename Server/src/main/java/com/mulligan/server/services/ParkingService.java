package com.mulligan.server.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mulligan.common.models.ParkingSpace;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.*;

/**
 * Service class for managing parking zones, spaces, and related transactions.
 *
 * This class interacts with the MongoDB database to perform operations such as:
 * - Validating parking zones and spaces
 * - Checking availability of parking spaces
 * - Fetching parking and citation history for vehicles
 * - Retrieving all parking zones and spaces
 *
 * MongoDB collections used:
 * - parking_zones
 * - parking_spaces
 * - transactions
 * - vehicles
 * - citations
 *
 *  @author Oran Alster
 *  @author Jamal Majadle
 * @version 1.11.0
 */
public class ParkingService {
    private final MongoCollection<Document> parkingZonesCollection;
    private final MongoCollection<Document> parkingSpacesCollection;
    private final MongoCollection<Document> transactionsCollection;
    private final MongoCollection<Document> vehiclesCollection;
    private final MongoCollection<Document> citationsCollection;
    private final Logger logger = Logger.getLogger(ParkingService.class.getName());

    /**
     * Initializes the ParkingService with a MongoDB database.
     *
     * @param database The MongoDB database used for parking-related data.
     */
    public ParkingService(MongoDatabase database) {
        this.parkingZonesCollection = database.getCollection("parking_zones");
        this.parkingSpacesCollection = database.getCollection("parking_spaces");
        this.transactionsCollection = database.getCollection("Transactions");
        this.vehiclesCollection = database.getCollection("vehicles");
        this.citationsCollection = database.getCollection("Citations");
    }

    /**
     * Checks if the specified parking zone name exists in the database.
     * @param zoneName The name of the parking zone to validate.
     * @return True if the parking zone exists in the database, otherwise false.
     */
    public boolean isZoneNameValid(String zoneName) {
        return parkingZonesCollection.countDocuments(eq("zone_name", zoneName)) > 0;
    }

    /**
     * Checks if the specified parking space (identified by zone name and post ID) exists in the database.
     * @param zoneName The name of the parking zone.
     * @param postId The identifier of the parking space within the zone.
     * @return True if the parking space exists in the database, otherwise false.
     */
    public boolean isParkingSpaceValid(String zoneName, String postId) {
        Document parkingZone = parkingZonesCollection.find(eq("zone_name", zoneName)).first();
        if (parkingZone == null) return false;

        int zoneId = parkingZone.getInteger("id");
        return parkingSpacesCollection.countDocuments(and(eq("zone_id", zoneId), eq("post_id", postId))) > 0;
    }

    /**
     * Checks if a parking space is available (i.e., not occupied) based on the zone name and post ID.
     * @param zoneName The name of the parking zone.
     * @param postId The identifier of the parking space within the zone.
     * @return True if the parking space is available, otherwise false.
     */
    public boolean isParkingSpaceAvailable(String zoneName, String postId) {
        Document parkingZone = parkingZonesCollection.find(eq("zone_name", zoneName)).first();
        if (parkingZone == null) return false;

        int zoneId = parkingZone.getInteger("id");
        Document parkingSpace = parkingSpacesCollection.find(and(eq("zone_id", zoneId), eq("post_id", postId))).first();
        if (parkingSpace == null) return false;

        int parkingSpaceId = parkingSpace.getInteger("id");
        return transactionsCollection.countDocuments(and(eq("parking_space_id", parkingSpaceId), exists("end", false))) == 0;
    }

    /**
     * Retrieves the ID of a parking space based on the zone name and post ID.
     * @param zoneName The name of the parking zone.
     * @param parkingSpace The identifier of the parking space within the zone.
     * @return The ObjectId of the parking space if found, otherwise null.
     */
    public int getParkingSpaceIdByZoneAndPost(String zoneName, String parkingSpace) {
        Document parkingZone = parkingZonesCollection.find(eq("zone_name", zoneName)).first();
        if (parkingZone == null) return -1;

        int zoneId = parkingZone.getInteger("id");
        Document space = parkingSpacesCollection.find(and(eq("zone_id", zoneId), eq("post_id", parkingSpace))).first();
        return space != null ? space.getInteger("id") : -1;
    }

    /**
     * Retrieves all parking zones from the database, regardless of their availability.
     * @return A list of all parking zone names.
     */
    public List<String> getAllAvailableParkingZones() {
        List<String> zones = new ArrayList<>();
        Bson filter = Filters.and(
                Filters.ne("zone_name", "NULL"),
                Filters.exists("zone_name", true)
        );
        for (Document zone : parkingZonesCollection.find(filter)) {
            zones.add(zone.getString("zone_name"));
        }
        return zones;
    }

    /**
     * Retrieves all parking spaces for a specific parking zone.
     * @param zoneName The name of the parking zone.
     * @return A list of parking space IDs within the specified zone.
     */
    public List<String> getAllParkingSpacesByZone(String zoneName) {
        List<String> parkingSpaces = new ArrayList<>();
        Document parkingZone = parkingZonesCollection.find(eq("zone_name", zoneName)).first();
        if (parkingZone == null) return parkingSpaces;

        int  zoneId = parkingZone.getInteger("id");
        for (Document space : parkingSpacesCollection.find(eq("zone_id", zoneId))) {
            parkingSpaces.add(space.getString("post_id"));
        }
        return parkingSpaces;
    }

    /**
     * Fetches the parking and citation history for a specific vehicle by VIN.
     * @param vin The vehicle's VIN.
     * @return A list of strings representing the vehicle's parking and citation history.
     */
    public List<String> getVehicleHistoryByVin(String vin) {
        List<String> history = new ArrayList<>();

        Document vehicle = vehiclesCollection.find(eq("vin", vin)).first();
        if (vehicle == null) {
            return history;
        }

        int vehicleId = vehicle.getInteger("id");

        for (Document transaction : transactionsCollection.find(eq("vehicle_id", vehicleId))) {
            String zoneName = "N/A";
            String postId = "N/A";
            String start = "N/A";
            String end = "N/A";
            String duration = "N/A";
            String amount = "N/A";
            String issueTime = "N/A";
            String citationAmount = "N/A";
            String citationReason = "N/A";

            int parkingSpaceId = transaction.getInteger("parking_space_id");
            Document parkingSpace = (parkingSpaceId != -1)
                    ? parkingSpacesCollection.find(eq("id", parkingSpaceId)).first()
                    : null;

            if (parkingSpace != null) {
                int zoneId = parkingSpace.getInteger("zone_id");
                Document zone = (zoneId != -1)
                        ? parkingZonesCollection.find(eq("id", zoneId)).first()
                        : null;
                if (zone != null) {
                    zoneName = zone.getString("zone_name");
                }
                postId = parkingSpace.getString("post_id");
            }

            if (transaction != null) {
                start = transaction.getDate("start") != null ? transaction.getDate("start").toString() : "N/A";
                end = transaction.getDate("end") != null ? transaction.getDate("end").toString() : "N/A";

                if (transaction.getDate("start") != null && transaction.getDate("end") != null) {
                    long startTime = transaction.getDate("start").getTime();
                    long endTime = transaction.getDate("end").getTime();
                    long durationInMinutes = (endTime - startTime) / (1000 * 60);
                    duration = String.valueOf(durationInMinutes);
                }

                amount = transaction.getDouble("amount") != null ? transaction.getDouble("amount").toString() : "N/A";
            }

            Document citation = citationsCollection.find(and(
                    eq("vehicle_id", vehicleId),
                    eq("parking_space_id", parkingSpaceId),
                    gte("issue_time", transaction.getDate("start")),
                    or(
                            lte("issue_time", transaction.getDate("end")),
                            and(
                                    eq("transaction.end", null),
                                    gte("issue_time", transaction.getDate("start"))
                            )
                    )
            )).first();



            if (citation != null) {
                issueTime = citation.getDate("issue_time") != null ? citation.getDate("issue_time").toString() : "N/A";
                citationAmount = citation.getDouble("amount") != null ? citation.getDouble("amount").toString() : "N/A";
                citationReason = citation.getString("reason") != null ? citation.getString("reason") : "N/A";
            }



            String record = vin + "|" +
                    vehicle.getString("owner_name") + "|" +
                    zoneName + "|" +
                    postId + "|" +
                    start + "|" +
                    end + "|" +
                    duration + "|" +
                    amount + "|" +
                    issueTime + "|" +
                    citationAmount + "|" +
                    citationReason;

            history.add(record);
        }

        return history;
    }

}
