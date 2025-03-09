package com.mulligan.common.models;

/**
 * Represents a parking space recommendation.
 *
 * This class holds the details of a recommended parking space, including:
 *
 *     The zone where the parking space is located.
 *     The specific space identifier.
 *     The number of citations the space has received.
 *
 *
 *
 * @author Jamal Majadle
 * @version 2.0
 */
public class ParkingSpaceRecommendation {
    private String space;
    private int citations;

    public ParkingSpaceRecommendation(String space, int citations) {
        this.space = space;
        this.citations = citations;
    }

    // Getters and setters

    public String getSpace() {
        return space;
    }

    public void setSpace(String space) {
        this.space = space;
    }

    public int getCitations() {
        return citations;
    }

    public void setCitations(int citations) {
        this.citations = citations;
    }
}
