package org.example.service;

import org.example.dal.RoutingDAO;
import org.example.model.GroundStation;
import org.example.model.Planet;
import org.example.model.RouteResult;
import org.example.model.SpaceObject;

import java.util.*;

/**
 * Dịch vụ định tuyến vệ tinh A → B.
 *
 * Luồng xử lý:
 *  1. Tính tọa độ Cartesian 3D của tất cả nút (trạm mặt đất + vệ tinh).
 *  2. Kiểm tra Line-of-Sight (LOS) giữa từng cặp nút: đường thẳng nối
 *     2 nút không được xuyên qua bề mặt hành tinh.
 *  3. Chạy Dijkstra trên đồ thị LOS để tìm đường đi ngắn nhất.
 *  4. Lưu kết quả (đường đi + liên kết vệ tinh) vào CSDL.
 */
public class RoutingService {

    /** Hệ số bán kính: đường thẳng bị chặn nếu khoảng cách vuông góc < R * MARGIN */
    // NOTE: Ground stations are placed exactly on the planet surface (radius R).
    // If we inflate the blocking sphere (margin > 1), the station point becomes "inside"
    // the sphere and every station->satellite segment will intersect it, killing all links.
    private static final double LOS_MARGIN = 1.0;

    private final RoutingDAO dao = new RoutingDAO();

    // ─────────────────────────────────────────────────────────
    //  PUBLIC
    // ─────────────────────────────────────────────────────────

    /**
     * Tìm tuyến đường từ stationA → stationB trên hành tinh planet,
     * dùng danh sách vệ tinh liên lạc {@code satellites}.
     *
     * @param saveToDb true → lưu kết quả vào DB
     */
    public RouteResult findRoute(Planet planet,
                                 GroundStation stationA,
                                 GroundStation stationB,
                                 List<SpaceObject> satellites,
                                 boolean saveToDb) {

        // Lọc vệ tinh liên lạc cùng hành tinh
        List<SpaceObject> commSats = satellites.stream()
                .filter(s -> s.getPlanetId() == planet.getId())
                .toList();

        if (commSats.isEmpty()) {
            RouteResult r = new RouteResult(RouteResult.Status.NO_SATELLITES,
                    "Không có vệ tinh liên lạc nào quanh " + planet.getName());
            if (saveToDb) dao.saveRoute(planet.getId(),
                    stationA.getStationId(), stationB.getStationId(), r);
            return r;
        }

        double R = planet.getRadius();

        // ── Xây bảng nút ────────────────────────────────────
        // Key format: "GS_<id>" hoặc "SAT_<id>"
        Map<String, double[]> pos   = new LinkedHashMap<>();
        Map<String, String>   names = new HashMap<>();

        String keyA = "GS_"  + stationA.getStationId();
        String keyB = "GS_"  + stationB.getStationId();

        pos.put(keyA, stationA.toCartesian(R));
        pos.put(keyB, stationB.toCartesian(R));
        names.put(keyA, "📡 " + stationA.getStationName());
        names.put(keyB, "📡 " + stationB.getStationName());

        for (SpaceObject s : commSats) {
            String key = "SAT_" + s.getObjectId();
            pos.put(key, satCartesian(s, R));
            names.put(key, "🛰 " + s.getObjectName());
        }

        // ── Xây đồ thị có trọng số theo LOS ─────────────────
        List<String> nodes = new ArrayList<>(pos.keySet());
        Map<String, Map<String, Double>> graph = new HashMap<>();
        nodes.forEach(n -> graph.put(n, new HashMap<>()));

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                String u = nodes.get(i), v = nodes.get(j);
                boolean uGs = u.startsWith("GS_"), vGs = v.startsWith("GS_");
                if (uGs && vGs) continue;                       // GS không nối trực tiếp nhau

                double[] pu = pos.get(u), pv = pos.get(v);
                if (!los(pu, pv, R * LOS_MARGIN)) continue;     // bị hành tinh chặn

                double dist = dist3(pu, pv);
                graph.get(u).put(v, dist);
                graph.get(v).put(u, dist);

                // Lưu liên kết vệ tinh – vệ tinh vào DB
                if (saveToDb && !uGs && !vGs) {
                    dao.upsertLink(
                            Integer.parseInt(u.substring(4)),
                            Integer.parseInt(v.substring(4)),
                            dist, true);
                }
            }
        }

        // ── Dijkstra ─────────────────────────────────────────
        // Quick diagnostics: if a station can't see any satellite (LOS), routing can never succeed.
        int degA = graph.getOrDefault(keyA, Map.of()).size();
        int degB = graph.getOrDefault(keyB, Map.of()).size();
        if (degA == 0 || degB == 0) {
            String msg = (degA == 0 && degB == 0)
                    ? "Khong co ve tinh nao nhin thay duoc tu ca 2 tram (LOS deu bi chan)."
                    : (degA == 0
                        ? ("Tram nguon \"" + stationA.getStationName() + "\" khong nhin thay ve tinh nao (LOS bi chan).")
                        : ("Tram dich \"" + stationB.getStationName() + "\" khong nhin thay ve tinh nao (LOS bi chan)."));
            msg += " (A links=" + degA + ", B links=" + degB + ", sats=" + commSats.size() + ")";
            RouteResult r = new RouteResult(RouteResult.Status.NO_PATH, msg);
            if (saveToDb) dao.saveRoute(planet.getId(),
                    stationA.getStationId(), stationB.getStationId(), r);
            return r;
        }

        Map<String, Double> d    = new HashMap<>();
        Map<String, String> prev = new HashMap<>();
        nodes.forEach(n -> d.put(n, Double.MAX_VALUE));
        d.put(keyA, 0.0);

        PriorityQueue<String> pq = new PriorityQueue<>(
                Comparator.comparingDouble(n -> d.getOrDefault(n, Double.MAX_VALUE)));
        pq.add(keyA);

        while (!pq.isEmpty()) {
            String u = pq.poll();
            if (u.equals(keyB)) break;
            for (Map.Entry<String, Double> e : graph.get(u).entrySet()) {
                double nd = d.get(u) + e.getValue();
                if (nd < d.getOrDefault(e.getKey(), Double.MAX_VALUE)) {
                    d.put(e.getKey(), nd);
                    prev.put(e.getKey(), u);
                    pq.remove(e.getKey());
                    pq.add(e.getKey());
                }
            }
        }

        // ── Tái tạo đường đi ─────────────────────────────────
        if (!d.containsKey(keyB) || d.get(keyB) == Double.MAX_VALUE) {
            RouteResult r = new RouteResult(RouteResult.Status.NO_PATH,
                    "Không tìm thấy đường đi từ \"" + stationA.getStationName()
                    + "\" đến \"" + stationB.getStationName()
                    + "\". Vệ tinh có thể đang ở phía khuất hoặc quá ít vệ tinh."
                    + " (A links=" + graph.getOrDefault(keyA, Map.of()).size()
                    + ", B links=" + graph.getOrDefault(keyB, Map.of()).size()
                    + ", sats=" + commSats.size() + ")");
            if (saveToDb) dao.saveRoute(planet.getId(),
                    stationA.getStationId(), stationB.getStationId(), r);
            return r;
        }

        LinkedList<String> pathIds   = new LinkedList<>();
        LinkedList<String> pathNames = new LinkedList<>();
        for (String cur = keyB; cur != null; cur = prev.get(cur)) {
            pathIds.addFirst(cur);
            pathNames.addFirst(names.get(cur));
        }

        int hops = (int) pathIds.stream().filter(k -> k.startsWith("SAT_")).count();
        RouteResult result = new RouteResult(
                new ArrayList<>(pathNames),
                new ArrayList<>(pathIds),
                d.get(keyB), hops);

        if (saveToDb) dao.saveRoute(planet.getId(),
                stationA.getStationId(), stationB.getStationId(), result);

        return result;
    }

    // ─────────────────────────────────────────────────────────
    //  GEOMETRY HELPERS
    // ─────────────────────────────────────────────────────────

    /** Tọa độ Cartesian của vệ tinh (km từ tâm hành tinh). */
    private double[] satCartesian(SpaceObject s, double R) {
        double r   = R + s.getAltitude();
        double lat = Math.toRadians(s.getLatitude());
        double lon = Math.toRadians(s.getLongitude());
        return new double[]{
                r * Math.cos(lat) * Math.cos(lon),
                r * Math.sin(lat),
                r * Math.cos(lat) * Math.sin(lon)
        };
    }

    /**
     * Line-of-Sight: true nếu đoạn thẳng PQ KHÔNG xuyên qua quả cầu bán kính R.
     * Dùng phương trình bậc 2: |P + t·(Q-P)|² = R²
     */
    private boolean los(double[] P, double[] Q, double R) {
        double dx = Q[0]-P[0], dy = Q[1]-P[1], dz = Q[2]-P[2];
        double a  = dx*dx + dy*dy + dz*dz;
        if (a == 0) return true;
        double b  = 2*(P[0]*dx + P[1]*dy + P[2]*dz);
        double c  = P[0]*P[0] + P[1]*P[1] + P[2]*P[2] - R*R;
        double disc = b*b - 4*a*c;
        if (disc < 0) return true;                    // không giao
        double t1 = (-b - Math.sqrt(disc)) / (2*a);
        double t2 = (-b + Math.sqrt(disc)) / (2*a);
        // Bị chặn khi giao điểm nằm trong đoạn (0,1)
        return !((t1 > 1e-6 && t1 < 1-1e-6) || (t2 > 1e-6 && t2 < 1-1e-6));
    }

    private double dist3(double[] a, double[] b) {
        double dx=a[0]-b[0], dy=a[1]-b[1], dz=a[2]-b[2];
        return Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
}
