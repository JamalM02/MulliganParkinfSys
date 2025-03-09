package com.mulligan.common.models;

import java.time.LocalDateTime;

/**
 * Represents a parking citation issued to a vehicle.
 * Contains details such as the citation ID, vehicle license plate, parking space, zone, issue time, and the citation amount.
 *
 * Features:
 * - Provides getters and setters for all fields.
 * - Includes a constructor for initializing a citation.
 * - Overrides the `toString` method for easy printing of citation details.
 *
 * @author Jamal Majadle
 * @version 1.8.0
 */
public class Citation {

    private String licensePlate;
    private String parkingSpace;
    private String zoneName;
    private String issueTime;
    private double amount;
    private String reason;


    /**
     * Constructs a new Citation with the given details.
     *
     * @param licensePlate The license plate number of the vehicle.
     * @param parkingSpace The parking space associated with the citation.
     * @param zoneName The zone where the parking violation occurred.
     * @param issueTime The time the citation was issued.
     * @param amount The citation amount in monetary units.
     * @param reason The reason for issuing the citation.
     */
    public Citation(String licensePlate, String parkingSpace, String zoneName, String issueTime, double amount, String reason) {
        this.licensePlate = licensePlate;
        this.parkingSpace = parkingSpace;
        this.zoneName = zoneName;
        this.issueTime = issueTime;
        this.amount = amount;
        this.reason = reason;
    }


    /**
     * Gets the license plate number of the vehicle.
     *
     * @return The vehicle's license plate number.
     */
    public String getLicensePlate() {
        return licensePlate;
    }

    /**
     * Sets the license plate number of the vehicle.
     *
     * @param licensePlate The license plate number to set.
     */
    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }

    /**
     * Gets the parking space associated with the citation.
     *
     * @return The parking space.
     */
    public String getParkingSpace() {
        return parkingSpace;
    }

    /**
     * Sets the parking space associated with the citation.
     *
     * @param parkingSpace The parking space to set.
     */
    public void setParkingSpace(String parkingSpace) {
        this.parkingSpace = parkingSpace;
    }

    /**
     * Gets the zone name where the parking violation occurred.
     *
     * @return The zone name.
     */
    public String getZoneName() {
        return zoneName;
    }

    /**
     * Sets the zone name where the parking violation occurred.
     *
     * @param zoneName The zone name to set.
     */
    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    /**
     * Gets the time the citation was issued.
     *
     * @return The issue time.
     */
    public String getIssueTime() {
        return issueTime;
    }

    /**
     * Sets the time the citation was issued.
     *
     * @param issueTime The issue time to set.
     */
    public void setIssueTime(String issueTime) {
        this.issueTime = issueTime;
    }

    /**
     * Gets the citation amount.
     *
     * @return The citation amount.
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Sets the citation amount.
     *
     * @param amount The citation amount to set.
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /**
     * Retrieves the reason associated with this entity.
     *
     * @return The reason as a {@link String}.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Sets the reason associated with this entity.
     *
     * @param reason The reason to set, as a {@link String}.
     */
    public void setReason(String reason) {
        this.reason = reason;
    }


    /**
     * Returns a string representation of the citation, including all details.
     *
     * @return A string representation of the citation.
     */
    @Override
    public String toString() {
        return "Citation{" +
                ", vehicleId=" + licensePlate +
                ", parkingSpace='" + parkingSpace + '\'' +
                ", zoneName='" + zoneName + '\'' +
                ", issueTime=" + issueTime +
                ", amount=" + amount +
                '}';
    }
}
