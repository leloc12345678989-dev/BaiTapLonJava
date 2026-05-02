package org.example.dal;

import org.example.model.RouteResult;
import org.example.util.DBContext;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO lưu và truy vấn:
 *   - RoutingHistory  : mỗi lần định tuyến
 *   - SatelliteLinks  : snapshot liên kết giữa 2 vệ tinh
 */
public class RoutingDAO {

    // ── RoutingHistory ───────────────────────────────────────

    /**
     * Lưu kết quả định tuyến vào DB.
     * @return RouteID mới, -1 nếu thất bại.
     */
    public int saveRoute(int planetId, int sourceId, int destId, RouteResult result) {
        // Preferred schema (new):
        //   PlanetID/SourceStationID/DestStationID/RoutePath/TotalDistance_km/HopCount/Status/CreatedAt
        // Legacy schema (as in Database/PlanetDataBasse.sql):
        //   StartPointName/EndPointName/SatelliteCount/TotalDistance/ExecutionTime/CreatedDate
        try {
            return saveRoutePreferred(planetId, sourceId, destId, result);
        } catch (SQLException preferredFailed) {
            try {
                String srcName = getStationName(sourceId);
                String dstName = getStationName(destId);
                return saveRouteLegacy(srcName, dstName, result);
            } catch (Exception legacyFailed) {
                legacyFailed.printStackTrace();
                preferredFailed.printStackTrace();
                return -1;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Lấy tối đa {@code limit} bản ghi lịch sử gần nhất.
     * Mỗi phần tử là mảng String: [RouteID, Source, Dest, Hops, Dist, Status, Time, Path]
     */
    public List<String[]> getRecentHistory(int limit) {
        try {
            return getRecentHistoryPreferred(limit);
        } catch (SQLException preferredFailed) {
            try {
                return getRecentHistoryLegacy(limit);
            } catch (Exception legacyFailed) {
                legacyFailed.printStackTrace();
                preferredFailed.printStackTrace();
                return new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private int saveRoutePreferred(int planetId, int sourceId, int destId, RouteResult result) throws SQLException {
        String sql = """
            INSERT INTO RoutingHistory
                (PlanetID, SourceStationID, DestStationID,
                 RoutePath, TotalDistance_km, HopCount, Status)
            VALUES (?,?,?,?,?,?,?);
            SELECT SCOPE_IDENTITY();
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt   (1, planetId);
            ps.setInt   (2, sourceId);
            ps.setInt   (3, destId);
            ps.setString(4, result.toPathJson());
            ps.setDouble(5, result.getTotalDistKm());
            ps.setInt   (6, result.getHopCount());
            ps.setString(7, result.getStatus().name());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            if (e instanceof SQLException se) throw se;
            throw new SQLException(e);
        }
        return -1;
    }

    private int saveRouteLegacy(String sourceName, String destName, RouteResult result) throws SQLException {
        String sql = """
            INSERT INTO RoutingHistory
                (StartPointName, EndPointName, SatelliteCount, TotalDistance, ExecutionTime)
            VALUES (?,?,?,?,?);
            SELECT SCOPE_IDENTITY();
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sourceName);
            ps.setString(2, destName);
            ps.setInt   (3, result.getHopCount());
            ps.setDouble(4, result.getTotalDistKm());
            ps.setDouble(5, 0.0);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (Exception e) {
            if (e instanceof SQLException se) throw se;
            throw new SQLException(e);
        }
        return -1;
    }

    private List<String[]> getRecentHistoryPreferred(int limit) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        String sql = """
            SELECT TOP (?) rh.RouteID,
                   gs1.StationName  AS Source,
                   gs2.StationName  AS Dest,
                   rh.HopCount,
                   CAST(rh.TotalDistance_km AS DECIMAL(10,1)) AS Dist,
                   rh.Status,
                   CONVERT(VARCHAR(19), rh.CreatedAt, 120) AS CreatedAt,
                   rh.RoutePath
            FROM   RoutingHistory rh
            JOIN   GroundStations gs1 ON rh.SourceStationID = gs1.StationID
            JOIN   GroundStations gs2 ON rh.DestStationID   = gs2.StationID
            ORDER  BY rh.CreatedAt DESC
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{
                        rs.getString("RouteID"),
                        rs.getString("Source"),
                        rs.getString("Dest"),
                        rs.getString("HopCount"),
                        rs.getString("Dist"),
                        rs.getString("Status"),
                        rs.getString("CreatedAt"),
                        rs.getString("RoutePath")
                });
            }
        } catch (Exception e) {
            if (e instanceof SQLException se) throw se;
            throw new SQLException(e);
        }
        return rows;
    }

    private List<String[]> getRecentHistoryLegacy(int limit) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        String sql = """
            SELECT TOP (?) RouteID,
                   StartPointName AS Source,
                   EndPointName   AS Dest,
                   SatelliteCount AS HopCount,
                   CAST(TotalDistance AS DECIMAL(10,1)) AS Dist,
                   CONVERT(VARCHAR(19), CreatedDate, 120) AS CreatedAt
            FROM RoutingHistory
            ORDER BY CreatedDate DESC
            """;
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{
                        rs.getString("RouteID"),
                        rs.getString("Source"),
                        rs.getString("Dest"),
                        rs.getString("HopCount"),
                        rs.getString("Dist"),
                        "UNKNOWN",
                        rs.getString("CreatedAt"),
                        "" // Path not available in legacy schema
                });
            }
            return rows;
        } catch (Exception e) {
            if (e instanceof SQLException se) throw se;
            throw new SQLException(e);
        }
    }

    private String getStationName(int stationId) {
        String sql = "SELECT StationName FROM GroundStations WHERE StationID=?";
        try (Connection c = conn(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, stationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        } catch (Exception ignored) {
            // If this fails (e.g., legacy DB without GroundStations table), fall back to a stable label.
        }
        return "Station#" + stationId;
    }

    // ── SatelliteLinks ───────────────────────────────────────

    /**
     * Upsert liên kết giữa 2 vệ tinh (luôn lưu cặp theo thứ tự nhỏ-lớn).
     */
    public void upsertLink(int satAId, int satBId, double distKm, boolean active) {
        int lo = Math.min(satAId, satBId), hi = Math.max(satAId, satBId);
        String checkSql = "SELECT LinkID FROM SatelliteLinks WHERE SatA_ID=? AND SatB_ID=?";
        try (Connection c = conn()) {
            try (PreparedStatement chk = c.prepareStatement(checkSql)) {
                chk.setInt(1, lo); chk.setInt(2, hi);
                try (ResultSet rs = chk.executeQuery()) {
                    if (rs.next()) {
                        // UPDATE
                        String upd = """
                            UPDATE SatelliteLinks
                            SET Distance_km=?, IsActive=?, UpdatedAt=GETDATE()
                            WHERE LinkID=?
                            """;
                        try (PreparedStatement ps = c.prepareStatement(upd)) {
                            ps.setDouble (1, distKm);
                            ps.setBoolean(2, active);
                            ps.setInt    (3, rs.getInt("LinkID"));
                            ps.executeUpdate();
                        }
                    } else {
                        // INSERT
                        String ins = """
                            INSERT INTO SatelliteLinks (SatA_ID, SatB_ID, Distance_km, IsActive)
                            VALUES (?,?,?,?)
                            """;
                        try (PreparedStatement ps = c.prepareStatement(ins)) {
                            ps.setInt    (1, lo);
                            ps.setInt    (2, hi);
                            ps.setDouble (3, distKm);
                            ps.setBoolean(4, active);
                            ps.executeUpdate();
                        }
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Connection conn() throws Exception { return new DBContext().getConnection(); }
}
