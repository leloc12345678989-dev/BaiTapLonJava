package org.example.dal;

import org.example.model.SpaceObject;
import org.example.util.DBContext;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SpaceObjectDAO {

    public List<SpaceObject> getAll() {
        return query("SELECT * FROM SpaceObjects ORDER BY PlanetID, ObjectName", ps -> {
        });
    }

    public List<SpaceObject> getByPlanet(int planetId) {
        return query(
                "SELECT * FROM SpaceObjects WHERE PlanetID = ? ORDER BY ObjectName",
                ps -> ps.setInt(1, planetId)
        );
    }

    public SpaceObject getById(int objectId) {
        List<SpaceObject> objects = query(
                "SELECT * FROM SpaceObjects WHERE ObjectID = ?",
                ps -> ps.setInt(1, objectId)
        );
        return objects.isEmpty() ? null : objects.get(0);
    }

    public int insert(SpaceObject object) {
        String sql = """
                INSERT INTO SpaceObjects
                    (ObjectName, PlanetID, Latitude, Longitude, Altitude, OrbitSpeed, ObjectType, TextureURL)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?);
                SELECT SCOPE_IDENTITY();
                """;
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindObject(ps, object);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public boolean update(SpaceObject object) {
        String sql = """
                UPDATE SpaceObjects
                SET ObjectName = ?,
                    PlanetID = ?,
                    Latitude = ?,
                    Longitude = ?,
                    Altitude = ?,
                    OrbitSpeed = ?,
                    ObjectType = ?,
                    TextureURL = ?
                WHERE ObjectID = ?
                """;
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            bindObject(ps, object);
            ps.setInt(9, object.getObjectId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int objectId) {
        String sql = "DELETE FROM SpaceObjects WHERE ObjectID = ?";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, objectId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateOrbitSpeed(int objectId, double speed) {
        String sql = "UPDATE SpaceObjects SET OrbitSpeed = ? WHERE ObjectID = ?";
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, speed);
            ps.setInt(2, objectId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void bindObject(PreparedStatement ps, SpaceObject object) throws SQLException {
        ps.setString(1, object.getObjectName());
        ps.setInt(2, object.getPlanetId());
        ps.setDouble(3, object.getLatitude());
        ps.setDouble(4, object.getLongitude());
        ps.setDouble(5, object.getAltitude());
        ps.setDouble(6, object.getOrbitSpeed());
        ps.setString(7, object.getObjectType());
        ps.setString(8, object.getTextureUrl());
    }

    private List<SpaceObject> query(String sql, ParamSetter setter) {
        List<SpaceObject> objects = new ArrayList<>();
        try (Connection conn = conn(); PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    objects.add(map(rs));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return objects;
    }

    private SpaceObject map(ResultSet rs) throws SQLException {
        return new SpaceObject(
                rs.getInt("ObjectID"),
                rs.getString("ObjectName"),
                rs.getInt("PlanetID"),
                rs.getDouble("Latitude"),
                rs.getDouble("Longitude"),
                rs.getDouble("Altitude"),
                rs.getDouble("OrbitSpeed"),
                rs.getString("ObjectType"),
                rs.getString("TextureURL")
        );
    }

    private Connection conn() throws Exception {
        return new DBContext().getConnection();
    }

    @FunctionalInterface
    private interface ParamSetter {
        void set(PreparedStatement ps) throws SQLException;
    }
}
