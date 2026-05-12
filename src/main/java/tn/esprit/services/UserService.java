package tn.esprit.services;

import org.mindrot.jbcrypt.BCrypt;
import tn.esprit.entities.User;
import tn.esprit.entities.UserDashboardStats;
import tn.esprit.utils.MyDB;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class UserService {
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_DOCTOR = "doctor";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ALL = "all";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9 ]{8,20}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z\\s'.-]{1,49}$");

    public boolean addUser(User user, String role) {
        user.setRole(role);
        return createUser(user);
    }

    public boolean createUser(User user) {
        String role = normalizeRole(user.getRole());
        Connection connection = MyDB.getInstance().getConnection();

        if (connection == null || role == null) {
            return false;
        }

        try {
            if (ROLE_ADMIN.equals(role)) {
                String query = "INSERT INTO admin (first_name, last_name, email, password, roles) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, user.getFirstName());
                    stmt.setString(2, user.getLastName());
                    stmt.setString(3, user.getEmail());
                    stmt.setString(4, hashPasswordIfNeeded(user.getPassword()));
                    stmt.setString(5, "[\"ROLE_ADMIN\"]");
                    return executeCreateStatement(user, stmt);
                }
            }

            if (ROLE_DOCTOR.equals(role)) {
                return createDoctorUser(connection, user, true);
            }

            return createPatientUser(connection, user, true);
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUser(User user, String role) {
        user.setRole(role);
        return updateUser(user);
    }

    public boolean updateUser(User user) {
        String role = normalizeRole(user.getRole());
        Connection connection = MyDB.getInstance().getConnection();

        if (connection == null || role == null) {
            return false;
        }

        try {
            if (ROLE_ADMIN.equals(role)) {
                String query = "UPDATE admin SET first_name = ?, last_name = ?, email = ?, password = ? WHERE id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, user.getFirstName());
                    stmt.setString(2, user.getLastName());
                    stmt.setString(3, user.getEmail());
                    stmt.setString(4, hashPasswordIfNeeded(user.getPassword()));
                    stmt.setInt(5, user.getId());
                    return stmt.executeUpdate() > 0;
                }
            }

            if (ROLE_DOCTOR.equals(role)) {
                String query = "UPDATE doctor SET first_name = ?, last_name = ?, email = ?, password = ?, speciality = ? WHERE id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, user.getFirstName());
                    stmt.setString(2, user.getLastName());
                    stmt.setString(3, user.getEmail());
                    stmt.setString(4, hashPasswordIfNeeded(user.getPassword()));
                    stmt.setString(5, defaultString(user.getSpeciality()));
                    stmt.setInt(6, user.getId());
                    return stmt.executeUpdate() > 0;
                }
            }

            preserveFaceDataIfMissing(user);

            String query = "UPDATE user SET full_name = ?, email = ?, password = ?, phone = ?, address = ?, face_image_path = ?, face_token = ? WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, user.getFullName());
                stmt.setString(2, user.getEmail());
                stmt.setString(3, hashPasswordIfNeeded(user.getPassword()));
                stmt.setString(4, defaultString(user.getPhone()));
                stmt.setString(5, defaultString(user.getAddress()));
                stmt.setString(6, blankToNull(user.getFaceImagePath()));
                stmt.setString(7, blankToNull(user.getFaceToken()));
                stmt.setInt(8, user.getId());
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteUser(int userId, String role) {
        String table = getTableNameForRole(role);
        Connection connection = MyDB.getInstance().getConnection();

        if (table == null || connection == null) {
            return false;
        }

        String query = "DELETE FROM " + table + " WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    public List<User> getAllUsers() {
        return getUsers("", ROLE_ALL, "name-asc");
    }

    public List<User> getUsers(String searchTerm, String roleFilter, String sortOption) {
        List<User> users = new ArrayList<>();
        Connection connection = MyDB.getInstance().getConnection();

        if (connection == null) {
            return users;
        }

        String normalizedRole = normalizeRoleFilter(roleFilter);
        if (ROLE_ALL.equals(normalizedRole) || ROLE_ADMIN.equals(normalizedRole)) {
            users.addAll(getUsersFromTable(connection, "admin", ROLE_ADMIN));
        }
        if (ROLE_ALL.equals(normalizedRole) || ROLE_DOCTOR.equals(normalizedRole)) {
            users.addAll(getUsersFromTable(connection, "doctor", ROLE_DOCTOR));
        }
        if (ROLE_ALL.equals(normalizedRole) || ROLE_USER.equals(normalizedRole)) {
            users.addAll(getUsersFromTable(connection, "user", ROLE_USER));
        }

        String search = normalizeText(searchTerm);
        if (!search.isEmpty()) {
            users.removeIf(user -> !matchesSearch(user, search));
        }

        users.sort(resolveComparator(sortOption));
        return users;
    }

    public List<User> searchUsers(String searchTerm) {
        return getUsers(searchTerm, ROLE_ALL, "name-asc");
    }

    public User getUserById(int id, String role) {
        String table = getTableNameForRole(role);
        Connection connection = MyDB.getInstance().getConnection();

        if (table == null || connection == null) {
            return null;
        }

        String query = "SELECT * FROM " + table + " WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs, normalizeRole(role));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by id: " + e.getMessage());
        }

        return null;
    }

    public User getUserByEmail(String email, String role) {
        String table = getTableNameForRole(role);
        Connection connection = MyDB.getInstance().getConnection();
        String normalizedEmail = normalizeText(email);

        if (table == null || connection == null || normalizedEmail.isEmpty()) {
            return null;
        }

        String query = "SELECT * FROM " + table + " WHERE LOWER(email) = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, normalizedEmail);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs, normalizeRole(role));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting user by email: " + e.getMessage());
        }

        return null;
    }

    public boolean emailExists(String email) {
        return emailExistsExcluding(email, null, -1);
    }

    public boolean emailExistsExcludingId(String email, int excludeId) {
        return emailExistsExcluding(email, null, excludeId);
    }

    public boolean emailExistsExcluding(String email, String excludedRole, int excludedId) {
        String normalizedEmail = normalizeText(email);
        if (normalizedEmail.isEmpty()) {
            return false;
        }

        for (User existingUser : getAllUsers()) {
            if (!normalizedEmail.equals(normalizeText(existingUser.getEmail()))) {
                continue;
            }

            boolean sameUser = existingUser.getId() == excludedId
                    && Objects.equals(normalizeRole(existingUser.getRole()), normalizeRole(excludedRole));
            if (!sameUser) {
                return true;
            }
        }
        return false;
    }

    public String validateUser(User user, String confirmPassword, boolean updateMode) {
        if (user == null) {
            return "User data is missing.";
        }

        String role = normalizeRole(user.getRole());
        if (role == null) {
            return "Please choose a valid role.";
        }

        String email = normalizeText(user.getEmail());
        if (email.isEmpty()) {
            return "Email is required.";
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Enter a valid email address.";
        }

        String password = defaultString(user.getPassword()).trim();
        if (password.isEmpty()) {
            return "Password is required.";
        }
        if (password.length() < 8) {
            return "Password must contain at least 8 characters.";
        }
        if (confirmPassword != null && !password.equals(confirmPassword)) {
            return "Password confirmation does not match.";
        }

        if (ROLE_ADMIN.equals(role) || ROLE_DOCTOR.equals(role)) {
            if (!isValidName(user.getFirstName())) {
                return "First name is required and must contain only letters.";
            }
            if (!isValidName(user.getLastName())) {
                return "Last name is required and must contain only letters.";
            }
            user.setFullName((user.getFirstName().trim() + " " + user.getLastName().trim()).trim());
        } else {
            String fullName = defaultString(user.getFullName()).trim();
            if (fullName.isEmpty()) {
                return "Full name is required.";
            }
            if (fullName.length() < 3) {
                return "Full name must contain at least 3 characters.";
            }
        }

        if (ROLE_DOCTOR.equals(role)) {
            if (defaultString(user.getSpeciality()).trim().isEmpty()) {
                return "Doctor speciality is required.";
            }
        }

        if (ROLE_USER.equals(role)) {
            String phone = defaultString(user.getPhone()).trim();
            String address = defaultString(user.getAddress()).trim();

            if (phone.isEmpty()) {
                return "Phone number is required.";
            }
            if (!PHONE_PATTERN.matcher(phone).matches()) {
                return "Phone number must contain 8 to 20 digits.";
            }
            if (address.isEmpty()) {
                return "Address is required.";
            }
        } else if (!defaultString(user.getPhone()).trim().isEmpty()
                && !PHONE_PATTERN.matcher(user.getPhone().trim()).matches()) {
            return "Phone number format is invalid.";
        }

        if (emailExistsExcluding(email, role, updateMode ? user.getId() : -1)) {
            return "This email address already exists.";
        }

        user.setEmail(email);
        user.setRole(role);
        return null;
    }

    public UserDashboardStats getDashboardStats() {
        Connection connection = MyDB.getInstance().getConnection();
        if (connection == null) {
            return new UserDashboardStats();
        }

        int admins = countRows(connection, "admin");
        int doctors = countRows(connection, "doctor");
        int patients = countRows(connection, "user");
        return new UserDashboardStats(admins, doctors, patients);
    }

    public boolean updateUserFaceData(int userId, String faceImagePath, String faceToken) {
        Connection connection = MyDB.getInstance().getConnection();
        if (connection == null || userId <= 0) {
            return false;
        }

        String query = "UPDATE user SET face_image_path = ?, face_token = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, blankToNull(faceImagePath));
            stmt.setString(2, blankToNull(faceToken));
            stmt.setInt(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating face data for user: " + e.getMessage());
            return false;
        }
    }

    private int countRows(Connection connection, String tableName) {
        String query = "SELECT COUNT(*) FROM " + tableName;
        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            System.err.println("Error counting rows in " + tableName + ": " + e.getMessage());
            return 0;
        }
    }

    private List<User> getUsersFromTable(Connection connection, String tableName, String role) {
        List<User> users = new ArrayList<>();
        String query = "SELECT * FROM " + tableName;

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(mapUser(rs, role));
            }
        } catch (SQLException e) {
            System.err.println("Error getting users from " + tableName + ": " + e.getMessage());
        }

        return users;
    }

    private User mapUser(ResultSet rs, String role) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setEmail(rs.getString("email"));
        user.setPassword(rs.getString("password"));
        user.setRole(role);
        user.setCreatedAt(readTimestampColumn(rs, "created_at"));

        if (ROLE_ADMIN.equals(role)) {
            user.setFirstName(rs.getString("first_name"));
            user.setLastName(rs.getString("last_name"));
            user.setFullName((defaultString(user.getFirstName()) + " " + defaultString(user.getLastName())).trim());
        } else if (ROLE_DOCTOR.equals(role)) {
            user.setFirstName(rs.getString("first_name"));
            user.setLastName(rs.getString("last_name"));
            user.setFullName((defaultString(user.getFirstName()) + " " + defaultString(user.getLastName())).trim());
            user.setSpeciality(rs.getString("speciality"));
        } else {
            user.setFullName(rs.getString("full_name"));
            user.setPhone(rs.getString("phone"));
            user.setAddress(rs.getString("address"));
            user.setFaceImagePath(readOptionalColumn(rs, "face_image_path"));
            user.setFaceToken(readOptionalColumn(rs, "face_token"));
        }

        return user;
    }

    private boolean executeCreateStatement(User user, PreparedStatement stmt) throws SQLException {
        boolean created = stmt.executeUpdate() > 0;
        if (!created) {
            return false;
        }

        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                user.setId(generatedKeys.getInt(1));
            }
        }
        return true;
    }

    private boolean createPatientUser(Connection connection, User user, boolean includeVerified) throws SQLException {
        String query = includeVerified
                ? "INSERT INTO user (full_name, email, password, phone, address, face_image_path, face_token, roles, is_verified) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO user (full_name, email, password, phone, address, face_image_path, face_token, roles) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getFullName());
            stmt.setString(2, user.getEmail());
            stmt.setString(3, hashPasswordIfNeeded(user.getPassword()));
            stmt.setString(4, defaultString(user.getPhone()));
            stmt.setString(5, defaultString(user.getAddress()));
            stmt.setString(6, blankToNull(user.getFaceImagePath()));
            stmt.setString(7, blankToNull(user.getFaceToken()));
            stmt.setString(8, "[\"ROLE_USER\"]");
            if (includeVerified) {
                stmt.setInt(9, 1);
            }
            return executeCreateStatement(user, stmt);
        } catch (SQLException e) {
            if (!includeVerified || !canRetryWithoutVerified(e)) {
                throw e;
            }
            tryAddVerificationColumn(connection, "user");
            try {
                return createPatientUser(connection, user, true);
            } catch (SQLException retryError) {
                if (!canRetryWithoutVerified(retryError)) {
                    throw retryError;
                }
                return createPatientUser(connection, user, false);
            }
        }
    }

    private boolean createDoctorUser(Connection connection, User user, boolean includeVerified) throws SQLException {
        String query = includeVerified
                ? "INSERT INTO doctor (first_name, last_name, email, password, speciality, roles, is_verified) VALUES (?, ?, ?, ?, ?, ?, ?)"
                : "INSERT INTO doctor (first_name, last_name, email, password, speciality, roles) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, user.getFirstName());
            stmt.setString(2, user.getLastName());
            stmt.setString(3, user.getEmail());
            stmt.setString(4, hashPasswordIfNeeded(user.getPassword()));
            stmt.setString(5, defaultString(user.getSpeciality()));
            stmt.setString(6, "[\"ROLE_DOCTOR\"]");
            if (includeVerified) {
                stmt.setInt(7, 1);
            }
            return executeCreateStatement(user, stmt);
        } catch (SQLException e) {
            if (!includeVerified || !canRetryWithoutVerified(e)) {
                throw e;
            }
            tryAddVerificationColumn(connection, "doctor");
            try {
                return createDoctorUser(connection, user, true);
            } catch (SQLException retryError) {
                if (!canRetryWithoutVerified(retryError)) {
                    throw retryError;
                }
                return createDoctorUser(connection, user, false);
            }
        }
    }

    private boolean canRetryWithoutVerified(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("is_verified")
                || message.contains("doesn't exist in engine")
                || message.contains("does not exist in engine");
    }

    private void tryAddVerificationColumn(Connection connection, String tableName) {
        if (!"user".equals(tableName) && !"doctor".equals(tableName)) {
            return;
        }
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("ALTER TABLE `" + tableName + "` ADD COLUMN `is_verified` TINYINT(1) NOT NULL DEFAULT 1");
        } catch (SQLException e) {
            System.err.println("Could not add is_verified column automatically to " + tableName + ": " + e.getMessage());
        }
    }

    private void preserveFaceDataIfMissing(User user) {
        if (user == null || user.getId() <= 0 || !ROLE_USER.equals(normalizeRole(user.getRole()))) {
            return;
        }

        boolean missingFaceImage = isBlank(user.getFaceImagePath());
        boolean missingFaceToken = isBlank(user.getFaceToken());
        if (!missingFaceImage && !missingFaceToken) {
            return;
        }

        User persistedUser = getUserById(user.getId(), ROLE_USER);
        if (persistedUser == null) {
            return;
        }

        if (missingFaceImage) {
            user.setFaceImagePath(persistedUser.getFaceImagePath());
        }
        if (missingFaceToken) {
            user.setFaceToken(persistedUser.getFaceToken());
        }
    }

    private String readOptionalColumn(ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    private java.sql.Timestamp readTimestampColumn(ResultSet rs, String columnName) {
        try {
            return rs.getTimestamp(columnName);
        } catch (SQLException e) {
            return null;
        }
    }

    private boolean matchesSearch(User user, String searchTerm) {
        return normalizeText(user.getFullName()).contains(searchTerm)
                || normalizeText(user.getEmail()).contains(searchTerm)
                || normalizeText(user.getRole()).contains(searchTerm)
                || normalizeText(user.getSpeciality()).contains(searchTerm)
                || normalizeText(user.getPhone()).contains(searchTerm);
    }

    private Comparator<User> resolveComparator(String sortOption) {
        String option = normalizeText(sortOption);

        if ("name-desc".equals(option)) {
            return Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER).reversed();
        }
        if ("email-asc".equals(option)) {
            return Comparator.comparing(user -> normalizeText(user.getEmail()), String.CASE_INSENSITIVE_ORDER);
        }
        if ("role-asc".equals(option)) {
            return Comparator.comparing((User user) -> normalizeText(user.getRole()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(this::safeName, String.CASE_INSENSITIVE_ORDER);
        }
        return Comparator.comparing(this::safeName, String.CASE_INSENSITIVE_ORDER);
    }

    private String safeName(User user) {
        return normalizeText(user.getFullName());
    }

    private boolean isValidName(String value) {
        return NAME_PATTERN.matcher(defaultString(value).trim()).matches();
    }

    private String getTableNameForRole(String role) {
        String normalizedRole = normalizeRole(role);
        if (ROLE_ADMIN.equals(normalizedRole)) {
            return "admin";
        }
        if (ROLE_DOCTOR.equals(normalizedRole)) {
            return "doctor";
        }
        if (ROLE_USER.equals(normalizedRole)) {
            return "user";
        }
        return null;
    }

    private String normalizeRoleFilter(String roleFilter) {
        String normalized = normalizeText(roleFilter);
        return normalized.isEmpty() ? ROLE_ALL : (ROLE_ALL.equals(normalized) ? ROLE_ALL : normalizeRole(normalized));
    }

    private String normalizeRole(String role) {
        String normalized = normalizeText(role);
        if (ROLE_ADMIN.equals(normalized) || ROLE_DOCTOR.equals(normalized) || ROLE_USER.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String normalizeText(String value) {
        return defaultString(value).trim().toLowerCase(Locale.ROOT);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String hashPasswordIfNeeded(String password) {
        if (password != null
                && (password.startsWith("$2a$") || password.startsWith("$2y$") || password.startsWith("$2b$"))) {
            return password;
        }
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    private String blankToNull(String value) {
        String normalized = defaultString(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isBlank(String value) {
        return defaultString(value).trim().isEmpty();
    }
}
