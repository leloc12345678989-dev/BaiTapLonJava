package org.example.util;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBContext {
    public Connection getConnection() throws Exception {
        // trustServerCertificate=true giúp bỏ qua các bước bảo mật rắc rối khi làm ở máy cá nhân
        String url = "jdbc:sqlserver://localhost:1433;databaseName=PlanetSimulation_DB;encrypt=true;trustServerCertificate=true;";
        String user = "sa";
        String password = "123456"; // Mật khẩu bạn đã đổi trong SSMS
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        return DriverManager.getConnection(url, user, password);
    }
}