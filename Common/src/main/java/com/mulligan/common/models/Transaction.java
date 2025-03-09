package com.mulligan.common.models;

import java.time.LocalDateTime;

/**
 * Represents a parking transaction.
 * Contains details such as the transaction ID, vehicle license plate, parking space, zone,
 * start and end times, and the transaction amount.
 *
 * Features:
 * - Provides getters and setters for all fields.
 * - Includes a constructor for initializing a transaction.
 * - Overrides the `toString` method for easy representation of transaction details.
 *
 * @author Jamal Majadle
 * @version 1.5.0
 */
public class Transaction {

    private String licensePlate; // VIN
    private String parkingSpace;
    private String zoneName; // New Field
    private String start;
    private String end;
    private double amount;

    /**
     * Constructs a new Transaction with the given details.
     *
     * @param licensePlate The license plate number of the vehicle.
     * @param parkingSpace The parking space associated with the transaction.
     * @param zoneName The zone where the transaction occurred.
     * @param start The start time of the parking session.
     * @param end The end time of the parking session.
     * @param amount The amount charged for the transaction.
     */
    public Transaction(String licensePlate, String parkingSpace, String zoneName, String start, String end, double amount) {
        this.licensePlate = licensePlate;
        this.parkingSpace = parkingSpace;
        this.zoneName = zoneName;
        this.start = start;
        this.end = end;
        this.amount = amount;
    }

    public String getLicensePlate() { return licensePlate; }
    public void setLicensePlate(String licensePlate) { this.licensePlate = licensePlate; }

    public String getParkingSpace() { return parkingSpace; }
    public void setParkingSpace(String parkingSpace) { this.parkingSpace = parkingSpace; }

    public String getZoneName() { return zoneName; } // Getter for Zone Name
    public void setZoneName(String zoneName) { this.zoneName = zoneName; }

    public String getStart() { return start; }
    public void setStart(String start) { this.start = start; }

    public String getEnd() { return end; }
    public void setEnd(String end) { this.end = end; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}
