package org.example.dal;

import org.example.model.GroundStation;
import org.example.util.DBContext;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO thao tác bảng GroundStations – đầy đủ CRUD.
 */
public class GroundStationDAO {

    // ── READ ─────────────────────────────────────────────────
    public List<GroundStation> getAll() {
        return query("SELECT * FROM GroundStations ORDER BY PlanetID, StationName", ps -> {});
    }

    public List<GroundStation> getByPlanet(int planetId) {
        return query("SELECT * FROM GroundStations WHERE PlanetID=? ORDER BY StationName",
                ps -> ps.setInt(1, planetId));
    }

    public GroundStation getById(int id) {
        List<GroundStation> list = query(
                "SELECT * FROM GroundStations WHERE StationID=?",
                ps -> ps.setInt(1, id));
        return list.isEmpty() ? null : list.get(0);
    }

    // ── CREATE ───────────────────────────────────────────────
    /** @return StationID mới, hoặc -1 nếu thất bại */
    public int insert(GroundStation gs) {
        String sql = """
            INSERT INTO GroundStations
                (StationName, PlanetID, Latitude, Longitude, Description)
            VALUES (?,?,?,?,?);
            SELECT SCOPE_IDENTITY();
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gs.getStationName());
            ps.setInt   (2, gs.getPlanetId());
            ps.setDouble(3, gs.getLatitude());
            ps.setDouble(4, gs.getLongitude());
            ps.setString(5, gs.getDescription());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return -1;
    }

    // ── UPDATE ───────────────────────────────────────────────
    public boolean update(GroundStation gs) {
        String sql = """
            UPDATE GroundStations
            SET StationName=?, Latitude=?, Longitude=?, Description=?
            WHERE StationID=?
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, gs.getStationName());
            ps.setDouble(2, gs.getLatitude());
            ps.setDouble(3, gs.getLongitude());
            ps.setString(4, gs.getDescription());
            ps.setInt   (5, gs.getStationId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // ── DELETE ───────────────────────────────────────────────
    public boolean delete(int stationId) {
        String sql = "DELETE FROM GroundStations WHERE StationID=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // ── HELPERS ──────────────────────────────────────────────
    @FunctionalInterface interface ParamSetter { void set(PreparedStatement ps) throws SQLException; }

    private List<GroundStation> query(String sql, ParamSetter setter) {
        List<GroundStation> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            setter.set(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    private GroundStation map(ResultSet rs) throws SQLException {
        return new GroundStation(
                rs.getInt   ("StationID"),
                rs.getString("StationName"),
                rs.getInt   ("PlanetID"),
                rs.getDouble("Latitude"),
                rs.getDouble("Longitude"),
                rs.getString("Description")
        );
    }

    private Connection conn() throws Exception { return new DBContext().getConnection(); }
}