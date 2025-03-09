package com.mulligan.common.models;

import javafx.beans.property.SimpleStringProperty;

/**
 * Represents a parking event including details about the vehicle, parking session,
 * and associated citation (if any).
 *
 * This model is used to bind parking event data to a JavaFX TableView. It encapsulates all
 * the relevant information related to a parking session, such as vehicle details, parking zone,
 * duration, cost, and any citations issued. The use of {@link SimpleStringProperty} allows this
 * data to be easily bound to UI components in a JavaFX application.
 *
 * Typical usage includes creating instances of this class to represent individual parking events
 * and displaying them in tabular format within the application's user interface.
 *
 * @author Jamal Majadle
 * @version 1.3.0
 */
public class ParkingEvent {

    private final SimpleStringProperty vehicleNumber;
    private final SimpleStringProperty owner;
    private final SimpleStringProperty parkingZone;
    private final SimpleStringProperty parkingSpace;
    private final SimpleStringProperty parkingStartTime;
    private final SimpleStringProperty parkingEndTime;
    private final SimpleStringProperty parkingDuration;
    private final SimpleStringProperty parkingCost;
    private final SimpleStringProperty citationIssueTime;
    private final SimpleStringProperty citationAmount;
    private final SimpleStringProperty citationReason;

    /**
     * Constructs a ParkingEvent object with the specified details.
     *
     * @param vehicleNumber     The vehicle's number plate (e.g., "ABC123").
     * @param owner             The owner's name (e.g., "John Doe").
     * @param parkingZone       The parking zone where the vehicle is parked (e.g., "Zone A").
     * @param parkingSpace      The parking space assigned to the vehicle (e.g., "Space 12").
     * @param parkingStartTime  The start time of the parking session (e.g., "2024-11-01T08:30").
     * @param parkingEndTime    The end time of the parking session (e.g., "2024-11-01T10:00").
     * @param parkingDuration   The duration of the parking session in HH:mm format (e.g., "01:30").
     * @param parkingCost       The cost incurred for the parking session (e.g., "$5.00").
     * @param citationIssueTime The time when a citation was issued, if applicable (e.g., "2024-11-01T09:45").
     * @param citationAmount    The monetary value of the citation (e.g., "$50.00").
     * @param citationReason    The reason for the citation (e.g., "Overdue Parking").
     */
    public ParkingEvent(String vehicleNumber, String owner, String parkingZone, String parkingSpace,
                        String parkingStartTime, String parkingEndTime, String parkingDuration, String parkingCost,
                        String citationIssueTime, String citationAmount, String citationReason) {
        this.vehicleNumber = new SimpleStringProperty(vehicleNumber);
        this.owner = new SimpleStringProperty(owner);
        this.parkingZone = new SimpleStringProperty(parkingZone);
        this.parkingSpace = new SimpleStringProperty(parkingSpace);
        this.parkingStartTime = new SimpleStringProperty(parkingStartTime);
        this.parkingEndTime = new SimpleStringProperty(parkingEndTime);
        this.parkingDuration = new SimpleStringProperty(parkingDuration);
        this.parkingCost = new SimpleStringProperty(parkingCost);
        this.citationIssueTime = new SimpleStringProperty(citationIssueTime);
        this.citationAmount = new SimpleStringProperty(citationAmount);
        this.citationReason = new SimpleStringProperty(citationReason);
    }

    /**
     * Retrieves the vehicle's number plate.
     *
     * @return The vehicle number as a {@link String}.
     */
    public String getVehicleNumber() { return vehicleNumber.get(); }

    /**
     * Retrieves the owner's name.
     *
     * @return The owner's name as a {@link String}.
     */
    public String getOwner() { return owner.get(); }

    /**
     * Retrieves the parking zone where the vehicle is parked.
     *
     * @return The parking zone as a {@link String}.
     */
    public String getParkingZone() { return parkingZone.get(); }

    /**
     * Retrieves the specific parking space assigned to the vehicle.
     *
     * @return The parking space as a {@link String}.
     */
    public String getParkingSpace() { return parkingSpace.get(); }

    /**
     * Retrieves the start time of the parking session.
     *
     * @return The parking start time as a {@link String}.
     */
    public String getParkingStartTime() { return parkingStartTime.get(); }

    /**
     * Retrieves the end time of the parking session.
     *
     * @return The parking end time as a {@link String}.
     */
    public String getParkingEndTime() { return parkingEndTime.get(); }

    /**
     * Retrieves the duration of the parking session.
     *
     * @return The parking duration in HH:mm format as a {@link String}.
     */
    public String getParkingDuration() { return parkingDuration.get(); }

    /**
     * Retrieves the cost of the parking session.
     *
     * @return The parking cost as a {@link String}.
     */
    public String getParkingCost() { return parkingCost.get(); }

    /**
     * Retrieves the time when a citation was issued, if applicable.
     *
     * @return The citation issue time as a {@link String}.
     */
    public String getCitationIssueTime() { return citationIssueTime.get(); }

    /**
     * Retrieves the amount associated with the citation.
     *
     * @return The citation amount as a {@link String}.
     */
    public String getCitationAmount() { return citationAmount.get(); }

    /**
     * Retrieves the reason for the citation.
     *
     * @return The citation reason as a {@link String}.
     */
    public String getCitationReason() { return citationReason.get(); }

    /**
     * Retrieves the property for the citation reason. This is primarily used for
     * binding the citation reason in JavaFX TableView.
     *
     * @return The {@link SimpleStringProperty} for the citation reason.
     */
    public SimpleStringProperty citationReasonProperty() { return citationReason; }
}
