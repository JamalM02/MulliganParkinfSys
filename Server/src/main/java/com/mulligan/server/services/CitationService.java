package com.mulligan.server.services;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;
import java.text.DecimalFormat;
import java.util.StringJoiner;

import static com.mongodb.client.model.Filters.eq;

/**
 * Service class for managing citation-related operations.
 *
 *  This class provides functionalities to issue and retrieve citations from the database.
 * Citations are issued for parking violations, and detailed information about each citation
 * is stored in the database.
 *
 * MongoDB collections used:
 * - citations
 * - vehicles
 * - parking_spaces
 * - parking_zones
 *
 *  @author Oran Alster
 *   @author Jamal Majadle
 * @version 1.11.0
 */
public class CitationService {
    private final MongoCollection<Document> citationsCollection;
    private final MongoCollection<Document> vehiclesCollection;
    private final MongoCollection<Document> parkingSpacesCollection;
    private final MongoCollection<Document> parkingZonesCollection;
    private final Logger logger = Logger.getLogger(CitationService.class.getName());

    /**
     * Constructs a CitationService instance.
     *
     * @param database The MongoDB database to be used by the service.
     */
    public CitationService(MongoDatabase database) {
        this.citationsCollection = database.getCollection("Citations");
        this.vehiclesCollection = database.getCollection("vehicles");
        this.parkingSpacesCollection = database.getCollection("parking_spaces");
        this.parkingZonesCollection = database.getCollection("parking_zones");
    }

    /**
     * Issues a citation for a vehicle.
     * This method inserts a new citation document into the MongoDB collection.
     *
     * @param vehicleId      The ID of the vehicle associated with the citation.
     * @param parkingSpaceId The ID of the parking space where the violation occurred.
     * @param citationAmount The monetary amount of the citation.
     * @param issueTime      The time the citation was issued.
     * @param reason         The reason for issuing the citation.
     */
    public void issueCitation(int vehicleId, int parkingSpaceId, double citationAmount, LocalDateTime issueTime, String reason) {
        try {
            Document citation = new Document("vehicle_id", vehicleId)
                    .append("parking_space_id", parkingSpaceId)
                    .append("amount", citationAmount)
                    .append("issue_time", issueTime.atZone(ZoneId.systemDefault()).toInstant())
                    .append("reason", reason);

            citationsCollection.insertOne(citation);
        } catch (Exception e) {
            logger.severe("Failed to issue citation: " + e.getMessage());
        }
    }

    /**
     * Retrieves all citation records from the database and formats them into a single string.
     * Each record includes the citation ID, vehicle license plate, parking space, zone name,
     * issue time, citation amount, and the reason for the citation.
     *
     * @return A formatted string containing all citations from the database.
     *         Returns an empty string if no citations are found.
     */
    public String getAllCitations() {
        StringJoiner response = new StringJoiner("|");
        DecimalFormat decimalFormat = new DecimalFormat("#.00");

        try {
            for (Document citation : citationsCollection.find()) {
                int vehicleId = citation.getInteger("vehicle_id");
                int parkingSpaceId = citation.getInteger("parking_space_id");
                logger.info("vehicleId: " + vehicleId + " parkingSpaceId: " + parkingSpaceId);
                Document vehicle = vehiclesCollection.find(eq("id", vehicleId)).first();
                Document parkingSpace = parkingSpacesCollection.find(eq("id", parkingSpaceId)).first();
                Document parkingZone = parkingZonesCollection.find(eq("id", parkingSpace.getInteger("zone_id"))).first();

                if (vehicle == null || parkingSpace == null || parkingZone == null) {
                    continue;
                }

                String record = vehicle.getString("vin") + ";" +
                        parkingSpace.getString("post_id") + ";" +
                        parkingZone.getString("zone_name") + ";" +
                        citation.getDate("issue_time") + ";" +
                        decimalFormat.format(citation.getDouble("amount")) + ";" +
                        citation.getString("reason");

                response.add(record);
            }
        } catch (Exception e) {
            logger.severe("Failed to retrieve citations: " + e.getMessage());
        }

        return response.toString();
    }

}
