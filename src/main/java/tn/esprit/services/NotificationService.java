package tn.esprit.services;

import tn.esprit.entities.User;
import tn.esprit.utils.MyDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private static final String TABLE_NAME = "notifications";

    public NotificationService() {
        initializeTable();
    }

    public boolean notifyUser(User user, String type, String title, String message) {
        if (user == null) {
            return false;
        }
        return notifyUser(user.getId(), user.getEmail(), type, title, message);
    }

    public boolean notifyUser(int userId, String userEmail, String type, String title, String message) {
        String sql = "INSERT INTO " + TABLE_NAME + " (user_id, user_email, type, title, message) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, userEmail);
            ps.setString(3, safe(type));
            ps.setString(4, safe(title));
            ps.setString(5, safe(message));
            boolean saved = ps.executeUpdate() > 0;
            if (saved) {
                NotificationCenter.publish(true);
            }
            return saved;
        } catch (SQLException e) {
            System.err.println("Error saving notification: " + e.getMessage());
            return false;
        }
    }

    public boolean notifyByEmail(String userEmail, String type, String title, String message) {
        String sql = "INSERT INTO " + TABLE_NAME + " (user_email, type, title, message) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, userEmail);
            ps.setString(2, safe(type));
            ps.setString(3, safe(title));
            ps.setString(4, safe(message));
            boolean saved = ps.executeUpdate() > 0;
            if (saved) {
                NotificationCenter.publish(true);
            }
            return saved;
        } catch (SQLException e) {
            System.err.println("Error saving notification by email: " + e.getMessage());
            return false;
        }
    }

    public boolean notifySymfonyUser(User user, String title, String message, String type, String icon) {
        if (user == null) {
            return false;
        }

        String sql = "INSERT INTO notification (user_id, admin_id, title, message, type, icon, is_read, created_at) "
                + "VALUES (?, NULL, ?, ?, ?, ?, 0, NOW())";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.setString(2, safe(title));
            ps.setString(3, safe(message));
            ps.setString(4, safe(type).isBlank() ? "info" : safe(type));
            ps.setString(5, safe(icon).isBlank() ? "fas fa-bell" : safe(icon));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error saving Symfony notification: " + e.getMessage());
            return false;
        }
    }

    public List<NotificationItem> getNotificationsForUser(User user, int limit) {
        if (user == null) {
            return List.of();
        }

        String sql = "SELECT * FROM " + TABLE_NAME
                + " WHERE user_id = ? OR user_email = ? ORDER BY created_at DESC LIMIT ?";
        List<NotificationItem> notifications = new ArrayList<>();
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.setString(2, user.getEmail());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    notifications.add(map(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading notifications: " + e.getMessage());
        }
        return notifications;
    }

    public int countUnread(User user) {
        if (user == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM " + TABLE_NAME
                + " WHERE (user_id = ? OR user_email = ?) AND is_read = 0";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.setString(2, user.getEmail());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("Error counting unread notifications: " + e.getMessage());
            return 0;
        }
    }

    public void markAllRead(User user) {
        if (user == null) {
            return;
        }

        String sql = "UPDATE " + TABLE_NAME + " SET is_read = 1 WHERE user_id = ? OR user_email = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.setString(2, user.getEmail());
            ps.executeUpdate();
            NotificationCenter.publish();
        } catch (SQLException e) {
            System.err.println("Error marking notifications read: " + e.getMessage());
        }
    }

    private void initializeTable() {
        try (Statement stmt = getConnection().createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "user_id INT NULL,"
                    + "user_email VARCHAR(255),"
                    + "type VARCHAR(50) NOT NULL,"
                    + "title VARCHAR(255) NOT NULL,"
                    + "message TEXT NOT NULL,"
                    + "is_read TINYINT(1) NOT NULL DEFAULT 0,"
                    + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
                    + "INDEX idx_notifications_user_id (user_id),"
                    + "INDEX idx_notifications_user_email (user_email),"
                    + "INDEX idx_notifications_created_at (created_at)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error initializing notifications table: " + e.getMessage());
        }
    }

    private NotificationItem map(ResultSet rs) throws SQLException {
        return new NotificationItem(
                rs.getInt("id"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getBoolean("is_read"),
                rs.getTimestamp("created_at")
        );
    }

    private Connection getConnection() throws SQLException {
        Connection connection = MyDB.getInstance().getConnection();
        if (connection == null) {
            throw new SQLException("Database connection is unavailable");
        }
        return connection;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record NotificationItem(int id, String type, String title, String message, boolean read, Timestamp createdAt) {
    }
}
