package org.example.dal;

import org.example.model.Planet;
import org.example.model.SpaceObject;
import org.example.util.DBContext;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlanetDAO {

    public List<Planet> getAllPlanets() {
        List<Planet> list = new ArrayList<>();
        String sql = "SELECT * FROM Planets";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Planet(
                        rs.getInt("PlanetID"),
                        rs.getString("PlanetName"),
                        rs.getDouble("Radius_km"),
                        rs.getDouble("Mass_kg"),
                        rs.getString("TextureURL"),
                        rs.getDouble("DistanceFromSun")
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<SpaceObject> getAllSpaceObjects() {
        List<SpaceObject> list = new ArrayList<>();
        // s.* sẽ lấy luôn cả cột TextureURL mới thêm
        String sql = "SELECT s.*, p.Mass_kg, p.Radius_km FROM SpaceObjects s " +
                "JOIN Planets p ON s.PlanetID = p.PlanetID";

        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int id = rs.getInt("ObjectID");
                double alt = rs.getDouble("Altitude");
                double speed = rs.getDouble("OrbitSpeed");
                double pMass = rs.getDouble("Mass_kg");
                double pRadius = rs.getDouble("Radius_km");
                String texture = rs.getString("TextureURL"); // <--- LẤY DỮ LIỆU TỪ SQL

                // Tính toán vận tốc nếu đang trống
                if (speed <= 0) {
                    speed = calculateOrbitalVelocity(pMass, pRadius, alt);
                    updateOrbitSpeed(id, speed);
                }

                list.add(new SpaceObject(
                        id,
                        rs.getString("ObjectName"),
                        rs.getInt("PlanetID"),
                        rs.getDouble("Latitude"),
                        rs.getDouble("Longitude"),
                        alt,
                        speed,
                        rs.getString("ObjectType"),
                        texture // <--- TRUYỀN VÀO CONSTRUCTOR
                ));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public void updateOrbitSpeed(int objectId, double speed) {
        String sql = "UPDATE SpaceObjects SET OrbitSpeed = ? WHERE ObjectID = ?";
        try (Connection conn = new DBContext().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, speed);
            ps.setInt(2, objectId);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private double calculateOrbitalVelocity(double M, double R_km, double h_km) {
        double G = 6.67430e-11;
        double R = R_km * 1000;
        double h = h_km * 1000;
        // Công thức vận tốc vũ trụ cấp 1: $v = \sqrt{\frac{G \cdot M}{R + h}}$
        double velocityMps = Math.sqrt((G * M) / (R + h));
        return velocityMps / 1000;
    }
}