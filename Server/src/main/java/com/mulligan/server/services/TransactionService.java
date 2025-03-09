package com.mulligan.server.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.logging.Logger;
import static com.mongodb.client.model.Filters.*;
/**
 * Service class for handling parking transactions in a MongoDB database.
 *
 * This class provides various methods for managing parking events, retrieving transactions,
 * and checking the validity of vehicles and parking spaces. It interacts with the following MongoDB collections:
 * - Transactions
 * - Vehicles
 * - Parking Spaces
 * - Parking Zones
 *
 * Key Features:
 * - Start and stop parking sessions
 * - Retrieve all parking transactions
 * - Validate vehicles and parking spaces
 * - Calculate parking costs
 * - Check if a vehicle has exceeded the maximum parking duration
 *
 *   @author Oran Alster
 *   @author Jamal Majadle
 *  * @version 1.12.0
 */

public class TransactionService {
    private final MongoCollection<Document> transactionsCollection;
    private final MongoCollection<Document> vehiclesCollection;
    private final MongoCollection<Document> parkingSpacesCollection;
    private final MongoCollection<Document> parkingZonesCollection;
    private final Logger logger = Logger.getLogger(TransactionService.class.getName());

    /**
     * Constructs a TransactionService instance for MongoDB.
     *
     * @param database The MongoDatabase instance.
     */
    public TransactionService(MongoDatabase database) {
        this.transactionsCollection = database.getCollection("Transactions");
        this.vehiclesCollection = database.getCollection("vehicles");
        this.parkingSpacesCollection = database.getCollection("parking_spaces");
        this.parkingZonesCollection = database.getCollection("parking_zones");
    }

    /**
     * Checks if a vehicle's VIN is valid in the database.
     *
     * @param vin The vehicle's VIN.
     * @return True if the VIN is valid, otherwise false.
     */
    public boolean isVehicleVinValid(String vin) {
        return vehiclesCollection.countDocuments(eq("vin", vin)) > 0;
    }

    /**
     * Checks if a vehicle has an active parking session.
     *
     * @param vin The vehicle's VIN.
     * @return True if there is an active session for the given VIN, otherwise false.
     */
    public boolean hasActiveSession(String vin) {
        int vehicleId = getVehicleIdByVin(vin);
        return vehicleId != -1 && transactionsCollection.countDocuments(and(eq("vehicle_id", vehicleId), exists("end", false))) > 0;
    }

    /**
     * Retrieves the post ID of the active parking session for a vehicle.
     *
     * @param vin The vehicle's VIN.
     * @return The post ID of the active session, or null if no active session exists.
     */
    public String getActiveSessionPostId(String vin) {
        int vehicleId = getVehicleIdByVin(vin);
        if (vehicleId == -1) return null;

        Document transaction = transactionsCollection.find(and(eq("vehicle_id", vehicleId), exists("end", false))).first();
        if (transaction != null) {
            int parkingSpaceId = transaction.getInteger("parking_space_id");
            Document parkingSpace = parkingSpacesCollection.find(eq("id", parkingSpaceId)).first();
            if (parkingSpace != null) {
                return parkingSpace.getString("post_id");
            }
        }
        return null;
    }

    /**
     * Retrieves all parking transactions from the database.
     *
     * @return A formatted string of all transactions, with each record separated by a pipe ("|").
     */
    public String getAllTransactions() {
        StringBuilder transactions = new StringBuilder();

        for (Document transaction : transactionsCollection.find()) {
            int vehicleId = transaction.getInteger("vehicle_id");
            int parkingSpaceId = transaction.getInteger("parking_space_id");
            Document vehicle = vehiclesCollection.find(eq("id", vehicleId)).first();
            Document parkingSpace = parkingSpacesCollection.find(eq("id", parkingSpaceId)).first();
            Document parkingZone = parkingZonesCollection.find(eq("id", parkingSpace.getInteger("zone_id"))).first();

            if (vehicle == null || parkingSpace == null || parkingZone == null) {
                continue;
            }

            transactions.append(vehicle.getString("vin")).append(";")
                    .append(parkingSpace.getString("post_id")).append(";")
                    .append(parkingZone.getString("zone_name")).append(";")
                    .append(transaction.getDate("start")).append(";")
                    .append(transaction.getDate("end")).append(";")
                    .append(transaction.getDouble("amount")).append("|");
        }
        return transactions.toString();
    }



    /**
     * Starts a parking event for a vehicle.
     *
     * @param vin       The vehicle's VIN.
     * @param postId    The post ID of the parking space.
     * @param startTime The start time of the parking event.
     */
    public void startParkingEvent(String vin, String postId, LocalDateTime startTime) {
        int vehicleId = getVehicleIdByVin(vin);
        int parkingSpaceId = getParkingSpaceIdByPostId(postId);

        if (vehicleId != -1 && parkingSpaceId != -1) {
            Instant startInstant = startTime.atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneId.of("UTC"))
                    .toInstant();

            Document transaction = new Document("vehicle_id", vehicleId)
                    .append("parking_space_id", parkingSpaceId)
                    .append("start", startInstant);
            transactionsCollection.insertOne(transaction);
            logger.info("Started parking event for vehicle " + vin + " in space " + postId);
        } else {
            logger.severe("Failed to start parking event: Invalid VIN or Post ID.");
        }
    }


    /**
     * Stops a parking event for a vehicle and calculates the total cost.
     *
     * @param vin     The vehicle's VIN.
     * @param postId  The post ID of the parking space.
     * @param endTime The end time of the parking event.
     * @return The total cost of the parking event. Returns -1 if an error occurs.
     */
    public double stopParkingEvent(String vin, String postId, LocalDateTime endTime) {
        int vehicleId = getVehicleIdByVin(vin);
        int parkingSpaceId = getParkingSpaceIdByPostId(postId);

        if (vehicleId == -1 || parkingSpaceId == -1) {
            logger.severe("Failed to stop parking event: Invalid VIN or Post ID.");
            return -1;
        }

        Document transaction = transactionsCollection.find(and(
                eq("vehicle_id", vehicleId),
                eq("parking_space_id", parkingSpaceId),
                exists("end", false)
        )).first();

        if (transaction == null) {
            logger.severe("No active transaction found for VIN " + vin + " and Post ID " + postId);
            return -1;
        }

        double hourlyRate = ((Number) getHourlyRate(parkingSpaceId)).doubleValue();
        if (hourlyRate < 0) {
            logger.severe("Invalid hourly rate: " + hourlyRate);
            return -1;
        }

        if (transaction.get("start") == null) {
            logger.severe("Start time is null for transaction: " + transaction);
            return -1;
        }

        LocalDateTime startTime = transaction.get("start", Date.class).toInstant()
                .atZone(ZoneId.of("UTC"))
                .toLocalDateTime();

        Instant endInstant = endTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneId.of("UTC"))
                .toInstant();
        LocalDateTime utcEndTime = endInstant.atZone(ZoneId.of("UTC")).toLocalDateTime();

        Duration duration = Duration.between(startTime, utcEndTime);
        if (duration.isNegative()) {
            logger.severe("Invalid duration. Check start and end times.");
            return -1;
        }

        double hours = Math.max(duration.toMinutes() / 60.0, 0);
        double amount = Math.round(hourlyRate * hours * 100.0) / 100.0;

        transaction.append("end", endInstant).append("amount", amount);
        transactionsCollection.replaceOne(eq("_id", transaction.getObjectId("_id")), transaction);

        logger.info("Stopped parking event for vehicle " + vin + " in space " + postId + " with amount: $" + amount);
        return amount;
    }

    /**
     * Retrieves the vehicle ID by its VIN.
     *
     * @param vin The vehicle's VIN.
     * @return The vehicle ID, or -1 if the VIN is invalid.
     */
    public int getVehicleIdByVin(String vin) {
        Document vehicle = vehiclesCollection.find(eq("vin", vin)).first();
        return vehicle != null ? vehicle.getInteger("id") : -1;
    }

    /**
     * Retrieves the parking space ID by its post ID.
     *
     * @param postId The post ID of the parking space.
     * @return The parking space ID, or -1 if the post ID is invalid.
     */
    private int getParkingSpaceIdByPostId(String postId) {
        Document parkingSpace = parkingSpacesCollection.find(eq("post_id", postId)).first();
        return parkingSpace != null ? parkingSpace.getInteger("id") : -1;
    }

    /**
     * Retrieves the hourly parking rate for a given parking space ID.
     *
     * @param parkingSpaceId The parking space ID.
     * @return The hourly rate, or -1 if the parking space or zone is invalid.
     */
    private int getHourlyRate(int parkingSpaceId) {
        Document parkingSpace = parkingSpacesCollection.find(eq("id", parkingSpaceId)).first();
        if (parkingSpace != null) {
            int zoneId = parkingSpace.getInteger("zone_id");
            Document zone = parkingZonesCollection.find(eq("id", zoneId)).first();
            return zone != null ? zone.getInteger("hourly_rate") : -1;
        }
        return -1;
    }

    /**
     * Checks if a vehicle has exceeded the maximum parking duration for a given parking space.
     *
     * @param vin The vehicle's VIN.
     * @return True if the vehicle has exceeded the maximum parking duration, otherwise false.
     */
    public boolean hasExceededMaxParkingMinutes(String vin) {
        try {
            int vehicleId = getVehicleIdByVin(vin);

            if (vehicleId == -1) {
                logger.severe("Failed to check max parking minutes: Invalid vehicle ID.");
                return false;
            }

            Document activeTransaction = transactionsCollection.find(and(
                    eq("vehicle_id", vehicleId),
                    eq("end", null)
            )).first();

            if (activeTransaction == null) {
                logger.info("No active transaction found for vehicle " + vin);
                return false;
            }

            int parkingSpaceId = activeTransaction.getInteger("parking_space_id");
            Document parkingSpace = parkingSpacesCollection.find(eq("id", parkingSpaceId)).first();

            if (parkingSpace == null) {
                logger.severe("Failed to retrieve parking space for active transaction.");
                return false;
            }

            int maxParkingMinutes = parkingSpace.getInteger("max_parking_minutes");
            LocalDateTime startTime = LocalDateTime.parse(activeTransaction.getString("start"));
            long elapsedMinutes = Duration.between(startTime, LocalDateTime.now()).toMinutes();

            return elapsedMinutes > maxParkingMinutes;
        } catch (Exception e) {
            logger.severe("Error checking parking duration for vehicle " + vin + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a vehicle has an active parking session in a specific parking space.
     *
     * @param vehicleId The vehicle ID.
     * @param parkingSpaceId The parking space ID.
     * @return True if the vehicle has an active session in the given space, otherwise false.
     */
    public boolean hasActiveSessionByVehicleAndSpace(int vehicleId, int parkingSpaceId) {
        return transactionsCollection.countDocuments(and(
                eq("vehicle_id", vehicleId),
                eq("parking_space_id", parkingSpaceId),
                eq("end", null)
        )) > 0;
    }
}
