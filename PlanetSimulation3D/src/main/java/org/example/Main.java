package org.example;

import org.example.dal.PlanetDAO;
import org.example.model.Planet;
import java.util.List;

// QUAN TRỌNG: Phải có dòng khai báo class này bao quanh hàm main
public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 [DEBUG] Đang khởi tạo chương trình...");

        try {
            PlanetDAO dao = new PlanetDAO();
            System.out.println("📡 [DEBUG] Đang kết nối và lấy dữ liệu từ SQL Server...");

            List<Planet> list = dao.getAllPlanets();

            System.out.println("📩 [DEBUG] Số lượng hành tinh lấy được: " + list.size());

            if (list.isEmpty()) {
                System.out.println("❌ Không tìm thấy bản ghi nào trong bảng Planets.");
            } else {
                System.out.println("✅ Kết nối thành công! Danh sách:");
                for (Planet p : list) {
                    System.out.println(">> " + p.getName() + " - Bán kính: " + p.getRadius() + " km");
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [LỖI HỆ THỐNG]: " + e.getMessage());
            e.printStackTrace();
        }
    }
} // Đừng quên dấu đóng ngoặc nhọn của class ở cuối cùng