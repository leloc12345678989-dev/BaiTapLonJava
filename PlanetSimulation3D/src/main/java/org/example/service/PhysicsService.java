package org.example.service;

public class PhysicsService {
    private static final double G = 6.67430e-11; // Hằng số hấp dẫn

    public static double calculateVelocity(double planetMass, double planetRadiusKm, double altitudeKm) {
        // Đổi tất cả sang đơn vị chuẩn (m) để tính toán
        double rMeters = planetRadiusKm * 1000;
        double hMeters = altitudeKm * 1000;

        // v = sqrt(G * M / (R + h))
        double velocity = Math.sqrt((G * planetMass) / (rMeters + hMeters));

        // Trả về km/s để lưu vào DB
        return velocity / 1000;
    }
}