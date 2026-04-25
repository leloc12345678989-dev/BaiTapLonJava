package org.example.model;

public class Planet {
    private int id;
    private String name;
    private double radius;
    private double mass;
    private String textureUrl;
    // 1. THÊM BIẾN NÀY
    private double distanceFromSun;

    // 2. CẬP NHẬT CONSTRUCTOR (THÊM THAM SỐ CUỐI CÙNG)
    public Planet(int id, String name, double radius, double mass, String textureUrl, double distanceFromSun) {
        this.id = id;
        this.name = name;
        this.radius = radius;
        this.mass = mass;
        this.textureUrl = textureUrl;
        this.distanceFromSun = distanceFromSun;
    }

    // --- CÁC HÀM GETTER ---

    // 3. THÊM GETTER CHO KHOẢNG CÁCH
    public double getDistanceFromSun() {
        return distanceFromSun;
    }

    public String getTextureUrl() {
        return textureUrl;
    }

    public String getName() {
        return name;
    }

    public double getRadius() {
        return radius;
    }

    public int getId() {
        return id;
    }

    public double getMass() {
        return mass;
    }

    // Cập nhật toString để bạn debug xem đã lấy được khoảng cách chưa
    @Override
    public String toString() {
        return "Planet{" +
                "name='" + name + '\'' +
                ", radius=" + radius +
                ", distanceFromSun=" + distanceFromSun +
                '}';
    }
}