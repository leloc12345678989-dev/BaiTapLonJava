package org.example.model;

import java.util.List;

/**
 * Kết quả của một lần định tuyến vệ tinh từ A đến B.
 */
public class RouteResult {

    public enum Status { SUCCESS, NO_PATH, NO_SATELLITES }

    private final Status       status;
    private final List<String> pathNames;   // Tên thân thiện: ["📡 Đà Nẵng","🛰 VNREDSat","📡 HN"]
    private final List<String> pathIds;     // ID nội bộ:      ["GS_1","SAT_3","GS_2"]
    private final double       totalDistKm;
    private final int          hopCount;    // số vệ tinh trung gian
    private final String       message;

    /** Constructor thành công */
    public RouteResult(List<String> pathNames, List<String> pathIds,
                       double totalDistKm, int hopCount) {
        this.status       = Status.SUCCESS;
        this.pathNames    = pathNames;
        this.pathIds      = pathIds;
        this.totalDistKm  = totalDistKm;
        this.hopCount     = hopCount;
        this.message      = "Tìm thấy đường đi qua " + hopCount + " vệ tinh trung gian.";
    }

    /** Constructor thất bại */
    public RouteResult(Status status, String message) {
        this.status      = status;
        this.message     = message;
        this.pathNames   = List.of();
        this.pathIds     = List.of();
        this.totalDistKm = 0;
        this.hopCount    = 0;
    }

    public boolean      isSuccess()        { return status == Status.SUCCESS; }
    public Status       getStatus()        { return status;       }
    public List<String> getPathNames()     { return pathNames;    }
    public List<String> getPathIds()       { return pathIds;      }
    public double       getTotalDistKm()   { return totalDistKm;  }
    public int          getHopCount()      { return hopCount;     }
    public String       getMessage()       { return message;      }

    /** Chuỗi hiển thị: A → SAT1 → SAT2 → B */
    public String getPathString() {
        if (pathNames.isEmpty()) return "Không có đường đi";
        return String.join(" → ", pathNames);
    }

    /** Serialize thành JSON đơn giản để lưu DB */
    public String toPathJson() {
        if (pathIds.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < pathIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(pathIds.get(i)).append("\"");
        }
        return sb.append("]").toString();
    }
}