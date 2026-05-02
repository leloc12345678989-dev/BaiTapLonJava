package org.example.model;

/**
 * Trạm mặt đất trên bề mặt hành tinh.
 * Là điểm nguồn A hoặc đích B trong bài toán định tuyến.
 */
public class GroundStation {
    private int    stationId;
    private String stationName;
    private int    planetId;
    private double latitude;    // Vĩ độ  [-90 , 90]
    private double longitude;   // Kinh độ [-180, 180]
    private String description;

    public GroundStation(int stationId, String stationName, int planetId,
                         double latitude, double longitude, String description) {
        this.stationId   = stationId;
        this.stationName = stationName;
        this.planetId    = planetId;
        this.latitude    = latitude;
        this.longitude   = longitude;
        this.description = description;
    }

    // Getters
    public int    getStationId()   { return stationId;   }
    public String getStationName() { return stationName; }
    public int    getPlanetId()    { return planetId;    }
    public double getLatitude()    { return latitude;    }
    public double getLongitude()   { return longitude;   }
    public String getDescription() { return description; }

    /**
     * Chuyển vị trí mặt đất → tọa độ Cartesian 3D (km từ tâm hành tinh).
     */
    public double[] toCartesian(double planetRadius) {
        double lat = Math.toRadians(latitude);
        double lon = Math.toRadians(longitude);
        return new double[]{
                planetRadius * Math.cos(lat) * Math.cos(lon),
                planetRadius * Math.sin(lat),
                planetRadius * Math.cos(lat) * Math.sin(lon)
        };
    }

    @Override
    public String toString() { return stationName; }
}