package org.example.model;

public class SpaceObject {
    private int objectId;
    private String objectName;
    private int planetId;
    private double latitude;
    private double longitude;
    private double altitude;
    private double orbitSpeed;
    private String objectType;
    private String textureUrl; // <--- THÊM BIẾN NÀY

    public SpaceObject(int id, String name, int pId, double lat, double lon, double alt, double speed, String type, String texture) {
        this.objectId = id;
        this.objectName = name;
        this.planetId = pId;
        this.latitude = lat;
        this.longitude = lon;
        this.altitude = alt;
        this.orbitSpeed = speed;
        this.objectType = type;
        this.textureUrl = texture; // <--- GÁN GIÁ TRỊ
    }

    // Các Getter cần thiết
    public int getPlanetId() { return planetId; }
    public int getObjectId() { return objectId; }
    public String getObjectName() { return objectName; }
    public double getAltitude() { return altitude; }
    public double getOrbitSpeed() { return orbitSpeed; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTextureUrl() { return textureUrl; } // <--- GETTER MỚI
}