package com.mulligan.common.models;

/**
 * Represents a parking space in the system.
 * This class includes all relevant attributes, such as availability, preferences, and citation counts.
 */
public class ParkingSpace {
    private String zoneName;
    private String postId;
    private int citationCount;
    private boolean isAvailable;


    public ParkingSpace( String zoneName, String postId, int citationCount, boolean isAvailable) {
        this.zoneName = zoneName;
        this.postId = postId;
        this.citationCount = citationCount;
        this.isAvailable = isAvailable;
    }


    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getPostId() {
        return postId;
    }

    public void setPostId(String postId) {
        this.postId = postId;
    }

    public int getCitationCount() {
        return citationCount;
    }

    public void setCitationCount(int citationCount) {
        this.citationCount = citationCount;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    @Override
    public String toString() {
        return "ParkingSpace{" +
                ", zoneName='" + zoneName + '\'' +
                ", preferenceType='" + postId + '\'' +
                ", citationCount=" + citationCount +
                ", isAvailable=" + isAvailable +
                '}';
    }
}
