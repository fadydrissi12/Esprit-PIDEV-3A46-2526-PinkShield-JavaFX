package tn.esprit.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import tn.esprit.entities.User;
import tn.esprit.utils.MyDB;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ForumService {
    private static final String POST_TABLE = "post";
    private static final String COMMENT_TABLE = "comment";
    private static final String LIKE_TABLE = "post_like";
    private static final String BAD_WORDS_ENDPOINT = "https://www.purgomalum.com/service/containsprofanity?text=";
    private static final Duration API_TIMEOUT = Duration.ofSeconds(5);

    private final Connection connection;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(API_TIMEOUT)
            .build();

    public ForumService() {
        this.connection = MyDB.getInstance().getConnection();
    }

    public List<ForumPost> fetchAllPosts(User loggedInUser) throws SQLException {
        ensureConnection();

        TableColumns postColumns = loadColumns(POST_TABLE);
        String titleColumn = postColumns.firstExisting("title", "subject", "name");
        String contentColumn = postColumns.firstExisting("content", "body", "message", "description");
        String createdAtColumn = postColumns.firstExisting("created_at", "createdAt", "date", "published_at");
        String userColumn = postColumns.firstExisting("user_id", "author_id", "patient_id", "doctor_id", "created_by_id");
        String authorNameColumn = postColumns.firstExisting("author_name", "username", "user_name", "doctor_name", "patient_name");
        String authorEmailColumn = postColumns.firstExisting("author_email", "email", "user_email");
        String authorRoleColumn = postColumns.firstExisting("author_role", "role", "user_role");
        String imageColumn = postColumns.firstExisting("image_path", "image", "imagePath", "picture", "photo");

        String sql = "SELECT * FROM post ORDER BY " + safeOrderColumn(createdAtColumn) + " DESC, id DESC";
        List<ForumPost> posts = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int postId = rs.getInt("id");
                int authorId = readNullableInt(rs, userColumn) == null ? 0 : readNullableInt(rs, userColumn);
                boolean doctorPost = readBoolean(rs, "is_doctor_post");
                String authorName = readString(rs, authorNameColumn);
                if (authorName == null || authorName.isBlank()) {
                    authorName = readString(rs, authorEmailColumn);
                }
                posts.add(new ForumPost(
                        postId,
                        valueOrDefault(readString(rs, titleColumn), "Forum post"),
                        valueOrDefault(readString(rs, contentColumn), ""),
                        authorId,
                        valueOrDefault(authorName, resolveAuthorName(authorId, doctorPost)),
                        doctorPost,
                        readBooleanDefaultTrue(rs, "allow_comments"),
                        readString(rs, imageColumn),
                        readTimestamp(rs, createdAtColumn),
                        countLikes(postId),
                        countComments(postId),
                        loggedInUser != null && hasUserLikedPost(postId, loggedInUser.getId())
                ));
            }
        }

        return posts;
    }

    public int createPost(User author, String title, String content, boolean disableComments) throws SQLException {
        ensureConnection();
        if (author == null || author.getId() <= 0) {
            throw new SQLException("Logged-in user is required to create a forum post.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new SQLException("Post content is required.");
        }
        if (containsBadWords(valueOrDefault(title, "") + " " + content)) {
            throw new SQLException("Your post contains inappropriate language. Please edit it and try again.");
        }

        TableColumns postColumns = loadColumns(POST_TABLE);
        String titleColumn = postColumns.firstExisting("title", "subject", "name");
        String contentColumn = postColumns.firstExisting("content", "body", "message", "description");
        String createdAtColumn = postColumns.firstExisting("created_at", "createdAt", "date", "published_at");
        String userColumn = postColumns.firstExisting("user_id", "author_id", "patient_id", "doctor_id", "created_by_id");
        String authorNameColumn = postColumns.firstExisting("author_name", "username", "user_name", "doctor_name", "patient_name");
        String authorEmailColumn = postColumns.firstExisting("author_email", "email", "user_email");
        String authorRoleColumn = postColumns.firstExisting("author_role", "role", "user_role");

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (titleColumn != null) {
            columns.add(titleColumn);
            values.add(valueOrDefault(title, "Forum post"));
        }
        if (contentColumn != null) {
            columns.add(contentColumn);
            values.add(content.trim());
        }
        if (userColumn != null) {
            columns.add(userColumn);
            values.add(author.getId());
        }
        if (authorNameColumn != null) {
            columns.add(authorNameColumn);
            values.add(displayName(author));
        }
        if (authorEmailColumn != null) {
            columns.add(authorEmailColumn);
            values.add(valueOrDefault(author.getEmail(), "unknown@pinkshield.local"));
        }
        if (authorRoleColumn != null) {
            columns.add(authorRoleColumn);
            values.add(symfonyRole(author));
        }
        columns.add("is_doctor_post");
        values.add(isDoctor(author));
        columns.add("allow_comments");
        values.add(!disableComments);
        if (createdAtColumn != null) {
            columns.add(createdAtColumn);
            values.add(Timestamp.valueOf(LocalDateTime.now()));
        }

        String placeholders = String.join(", ", values.stream().map(value -> "?").toList());
        String sql = "INSERT INTO post (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindValues(ps, values);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    public boolean updatePost(int postId, String title, String content, boolean disableComments, User editor) throws SQLException {
        ensureConnection();
        if (postId <= 0) {
            return false;
        }
        if (!canManagePost(postId, editor)) {
            throw new SQLException("You can only edit your own posts. Doctors can manage all posts.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new SQLException("Post content is required.");
        }

        TableColumns postColumns = loadColumns(POST_TABLE);
        String titleColumn = postColumns.firstExisting("title", "subject", "name");
        String contentColumn = postColumns.firstExisting("content", "body", "message", "description");

        List<String> assignments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        if (titleColumn != null) {
            assignments.add(titleColumn + " = ?");
            values.add(valueOrDefault(title, "Forum post"));
        }
        if (contentColumn != null) {
            assignments.add(contentColumn + " = ?");
            values.add(content.trim());
        }
        assignments.add("allow_comments = ?");
        values.add(!disableComments);
        values.add(postId);

        String sql = "UPDATE post SET " + String.join(", ", assignments) + " WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindValues(ps, values);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deletePost(int postId, User actor) throws SQLException {
        ensureConnection();
        if (postId <= 0) {
            return false;
        }
        if (!canManagePost(postId, actor)) {
            throw new SQLException("You can only delete your own posts. Doctors can manage all posts.");
        }

        TableColumns commentColumns = loadColumns(COMMENT_TABLE);
        String postReferenceColumn = commentColumns.firstExisting("post_id", "blog_post_id");
        if (postReferenceColumn != null) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM comment WHERE " + postReferenceColumn + " = ?")) {
                ps.setInt(1, postId);
                ps.executeUpdate();
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM post_like WHERE post_id = ?")) {
            ps.setInt(1, postId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM post WHERE id = ?")) {
            ps.setInt(1, postId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean canManagePost(int postId, User actor) throws SQLException {
        ensureConnection();
        if (postId <= 0 || actor == null) {
            return false;
        }
        if (isDoctor(actor)) {
            return true;
        }

        TableColumns postColumns = loadColumns(POST_TABLE);
        String userColumn = postColumns.firstExisting("user_id", "author_id", "patient_id", "doctor_id", "created_by_id");
        String authorEmailColumn = postColumns.firstExisting("author_email", "email", "user_email");

        List<String> checks = new ArrayList<>();
        if (userColumn != null && actor.getId() > 0) {
            checks.add(userColumn + " = ?");
        }
        if (authorEmailColumn != null && actor.getEmail() != null && !actor.getEmail().isBlank()) {
            checks.add(authorEmailColumn + " = ?");
        }
        if (checks.isEmpty()) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM post WHERE id = ? AND (" + String.join(" OR ", checks) + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            ps.setInt(index++, postId);
            if (userColumn != null && actor.getId() > 0) {
                ps.setInt(index++, actor.getId());
            }
            if (authorEmailColumn != null && actor.getEmail() != null && !actor.getEmail().isBlank()) {
                ps.setString(index, actor.getEmail());
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public boolean toggleLike(int postId, User user) throws SQLException {
        ensureConnection();
        if (postId <= 0 || user == null || user.getId() <= 0) {
            return false;
        }

        if (hasUserLikedPost(postId, user.getId())) {
            String deleteSql = "DELETE FROM post_like WHERE post_id = ? AND user_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(deleteSql)) {
                ps.setInt(1, postId);
                ps.setInt(2, user.getId());
                ps.executeUpdate();
            }
            return false;
        }

        TableColumns likeColumns = loadColumns(LIKE_TABLE);
        String createdAtColumn = likeColumns.firstExisting("created_at", "createdAt");
        String userRoleColumn = likeColumns.firstExisting("user_role", "author_role", "role");
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        columns.add("post_id");
        values.add(postId);
        columns.add("user_id");
        values.add(user.getId());
        if (userRoleColumn != null) {
            columns.add(userRoleColumn);
            values.add(symfonyRole(user));
        }
        if (createdAtColumn != null) {
            columns.add(createdAtColumn);
            values.add(Timestamp.valueOf(LocalDateTime.now()));
        }

        String placeholders = String.join(", ", values.stream().map(value -> "?").toList());
        String sql = "INSERT INTO post_like (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bindValues(ps, values);
            ps.executeUpdate();
            return true;
        }
    }

    public boolean hasUserLikedPost(int postId, int userId) throws SQLException {
        ensureConnection();
        String sql = "SELECT COUNT(*) FROM post_like WHERE post_id = ? AND user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    public int countLikes(int postId) throws SQLException {
        ensureConnection();
        String sql = "SELECT COUNT(*) FROM post_like WHERE post_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countComments(int postId) throws SQLException {
        ensureConnection();
        TableColumns commentColumns = loadColumns(COMMENT_TABLE);
        String postReferenceColumn = commentColumns.firstExisting("post_id", "blog_post_id");
        if (postReferenceColumn == null) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM comment WHERE " + postReferenceColumn + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int addComment(int postId, User author, String content, Integer parentCommentId) throws SQLException {
        ensureConnection();
        if (postId <= 0 || author == null || author.getId() <= 0) {
            throw new SQLException("Post and logged-in user are required to comment.");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new SQLException("Comment content is required.");
        }
        if (containsBadWords(content)) {
            throw new SQLException("Your comment contains inappropriate language. Please edit it and try again.");
        }

        ForumPost post = findPostById(postId, author);
        if (post != null && !post.allowComments()) {
            throw new SQLException("Comments are disabled for this post.");
        }

        TableColumns commentColumns = loadColumns(COMMENT_TABLE);
        String contentColumn = commentColumns.firstExisting("content", "body", "message", "description");
        String userColumn = commentColumns.firstExisting("user_id", "author_id", "patient_id", "doctor_id", "created_by_id");
        String authorNameColumn = commentColumns.firstExisting("author_name", "username", "user_name", "doctor_name", "patient_name");
        String authorEmailColumn = commentColumns.firstExisting("author_email", "email", "user_email");
        String authorRoleColumn = commentColumns.firstExisting("author_role", "role", "user_role");
        String createdAtColumn = commentColumns.firstExisting("created_at", "createdAt", "date");
        String postReferenceColumn = commentColumns.firstExisting("post_id", "blog_post_id");
        String parentCommentColumn = commentColumns.firstExisting("parent_comment_id", "parent_id");

        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        if (postReferenceColumn == null) {
            throw new SQLException("Comment table is missing a post reference column.");
        }
        if ("blog_post_id".equalsIgnoreCase(postReferenceColumn)) {
            ensureBlogPostMirror(postId);
        }

        columns.add(postReferenceColumn);
        values.add(postId);
        if (userColumn != null) {
            columns.add(userColumn);
            values.add(author.getId());
        }
        if (authorNameColumn != null) {
            columns.add(authorNameColumn);
            values.add(displayName(author));
        }
        if (authorEmailColumn != null) {
            columns.add(authorEmailColumn);
            values.add(valueOrDefault(author.getEmail(), "unknown@pinkshield.local"));
        }
        if (authorRoleColumn != null) {
            columns.add(authorRoleColumn);
            values.add(symfonyRole(author));
        }
        if (contentColumn != null) {
            columns.add(contentColumn);
            values.add(content.trim());
        }
        if (parentCommentColumn != null) {
            columns.add(parentCommentColumn);
            values.add(parentCommentId == null || parentCommentId <= 0 ? null : parentCommentId);
        }
        if (createdAtColumn != null) {
            columns.add(createdAtColumn);
            values.add(Timestamp.valueOf(LocalDateTime.now()));
        }

        String placeholders = String.join(", ", values.stream().map(value -> "?").toList());
        String sql = "INSERT INTO comment (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";
        int commentId = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindValues(ps, values);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                commentId = keys.next() ? keys.getInt(1) : 0;
            }
        }
        if (post != null) {
            notifyPostAuthorAsync(post, author);
        }
        return commentId;
    }

    public List<ForumComment> fetchCommentsWithReplies(int postId) throws SQLException {
        ensureConnection();

        TableColumns commentColumns = loadColumns(COMMENT_TABLE);
        String contentColumn = commentColumns.firstExisting("content", "body", "message", "description");
        String userColumn = commentColumns.firstExisting("user_id", "author_id", "patient_id", "doctor_id", "created_by_id");
        String authorNameColumn = commentColumns.firstExisting("author_name", "username", "user_name", "doctor_name", "patient_name");
        String authorEmailColumn = commentColumns.firstExisting("author_email", "email", "user_email");
        String createdAtColumn = commentColumns.firstExisting("created_at", "createdAt", "date");
        String postReferenceColumn = commentColumns.firstExisting("post_id", "blog_post_id");
        String parentCommentColumn = commentColumns.firstExisting("parent_comment_id", "parent_id");

        if (postReferenceColumn == null) {
            return List.of();
        }

        String sql = "SELECT * FROM comment WHERE " + postReferenceColumn + " = ? ORDER BY "
                + safeOrderColumn(createdAtColumn) + " ASC, id ASC";

        Map<Integer, MutableCommentNode> nodesById = new LinkedHashMap<>();
        List<MutableCommentNode> roots = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    Integer parentId = readNullableInt(rs, parentCommentColumn);
                    int authorId = userColumn == null ? 0 : rs.getInt(userColumn);
                    String authorName = readString(rs, authorNameColumn);
                    if (authorName == null || authorName.isBlank()) {
                        authorName = readString(rs, authorEmailColumn);
                    }
                    MutableCommentNode node = new MutableCommentNode(
                            id,
                            postId,
                            parentId,
                            authorId,
                            valueOrDefault(authorName, authorId > 0 ? "User #" + authorId : "PinkShield Member"),
                            valueOrDefault(readString(rs, contentColumn), ""),
                            readTimestamp(rs, createdAtColumn)
                    );
                    nodesById.put(id, node);
                }
            }
        }

        for (MutableCommentNode node : nodesById.values()) {
            if (node.parentCommentId == null || !nodesById.containsKey(node.parentCommentId)) {
                roots.add(node);
            } else {
                nodesById.get(node.parentCommentId).replies.add(node);
            }
        }

        return roots.stream().map(MutableCommentNode::toRecord).toList();
    }

    public ForumPost findPostById(int postId, User loggedInUser) throws SQLException {
        return fetchAllPosts(loggedInUser).stream()
                .filter(post -> post.id() == postId)
                .findFirst()
                .orElse(null);
    }

    public boolean containsBadWords(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BAD_WORDS_ENDPOINT + encodedText))
                    .timeout(API_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && "true".equalsIgnoreCase(response.body().trim());
        } catch (Exception e) {
            System.err.println("Bad words API check failed, allowing text: " + e.getMessage());
            return false;
        }
    }

    public void sendEmailNotification(String recipientEmail, String postTitle) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return;
        }

        String smtpUser = readSetting("GMAIL_SMTP_USER", readSetting("SMTP_USER", ""));
        String smtpPassword = readSetting("GMAIL_SMTP_PASSWORD", readSetting("SMTP_PASSWORD", ""));
        String smtpFrom = readSetting("GMAIL_SMTP_FROM", readSetting("SMTP_FROM", smtpUser));

        if (smtpUser.isBlank() || smtpPassword.isBlank() || smtpFrom.isBlank()) {
            System.err.println("Forum email notification skipped: Gmail SMTP credentials are not configured.");
            return;
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPassword);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject("New comment on your PinkShield forum post");
            message.setText("""
                    Hello,

                    Someone commented on your forum post: %s

                    Open PinkShield to read the conversation.
                    """.formatted(valueOrDefault(postTitle, "Forum post")));
            Transport.send(message);
        } catch (MessagingException e) {
            System.err.println("Forum email notification failed: " + rootMessage(e));
        }
    }

    private void notifyPostAuthorAsync(ForumPost post, User commenter) {
        CompletableFuture.runAsync(() -> {
            try {
                String recipientEmail = findPostAuthorEmail(post.id());
                if (recipientEmail == null || recipientEmail.isBlank()) {
                    return;
                }
                if (commenter != null && commenter.getEmail() != null && recipientEmail.equalsIgnoreCase(commenter.getEmail())) {
                    return;
                }
                sendEmailNotification(recipientEmail, post.title());
            } catch (SQLException e) {
                System.err.println("Could not resolve forum post author email: " + e.getMessage());
            }
        });
    }

    private String findPostAuthorEmail(int postId) throws SQLException {
        TableColumns postColumns = loadColumns(POST_TABLE);
        String authorEmailColumn = postColumns.firstExisting("author_email", "email", "user_email");
        if (authorEmailColumn == null) {
            return null;
        }

        String sql = "SELECT " + authorEmailColumn + " FROM post WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void ensureBlogPostMirror(int postId) throws SQLException {
        if (postId <= 0 || !tableExists("blog_post")) {
            return;
        }
        try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM blog_post WHERE id = ?")) {
            check.setInt(1, postId);
            try (ResultSet rs = check.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        TableColumns blogColumns = loadColumns("blog_post");
        TableColumns postColumns = loadColumns(POST_TABLE);
        List<String> mirrorColumns = new ArrayList<>();
        List<String> selectColumns = new ArrayList<>();

        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "id", "id");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "title", "title");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "content", "content");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "author_email", "author_email");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "author_name", "author_name");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "author_role", "author_role");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "created_at", "created_at");
        addMirrorColumn(mirrorColumns, selectColumns, blogColumns, postColumns, "image_path", "image_path");

        if (!mirrorColumns.contains("id")
                || !mirrorColumns.contains("title")
                || !mirrorColumns.contains("content")
                || !mirrorColumns.contains("author_email")
                || !mirrorColumns.contains("author_name")
                || !mirrorColumns.contains("author_role")
                || !mirrorColumns.contains("created_at")) {
            throw new SQLException("Cannot mirror forum post into blog_post for comment foreign key.");
        }

        String sql = "INSERT INTO blog_post (" + String.join(", ", mirrorColumns) + ") "
                + "SELECT " + String.join(", ", selectColumns) + " FROM post WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, postId);
            ps.executeUpdate();
        }
    }

    private void addMirrorColumn(
            List<String> mirrorColumns,
            List<String> selectColumns,
            TableColumns targetColumns,
            TableColumns sourceColumns,
            String targetColumn,
            String sourceColumn
    ) {
        String realTarget = targetColumns.firstExisting(targetColumn);
        String realSource = sourceColumns.firstExisting(sourceColumn);
        if (realTarget == null || realSource == null) {
            return;
        }
        mirrorColumns.add(realTarget);
        selectColumns.add(realSource);
    }

    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getTables(connection.getCatalog(), null, tableName, null)) {
            return rs.next();
        }
    }

    public boolean isDoctor(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toLowerCase(Locale.ROOT);
        return "doctor".equals(role) || "role_doctor".equals(role) || role.contains("role_doctor");
    }

    private String symfonyRole(User user) {
        if (isDoctor(user)) {
            return "ROLE_DOCTOR";
        }
        String role = user == null ? "" : valueOrDefault(user.getRole(), "").trim().toLowerCase(Locale.ROOT);
        if ("admin".equals(role) || role.contains("role_admin")) {
            return "ROLE_ADMIN";
        }
        return "ROLE_USER";
    }

    private String resolveAuthorName(int authorId, boolean doctorPost) {
        if (authorId <= 0) {
            return "PinkShield Member";
        }

        String[] tables = doctorPost ? new String[]{"doctor", "user", "admin"} : new String[]{"user", "doctor", "admin"};
        for (String table : tables) {
            try {
                TableColumns columns = loadColumns(table);
                String fullNameColumn = columns.firstExisting("full_name", "name", "username");
                if (fullNameColumn != null) {
                    String name = readAuthorValue(table, fullNameColumn, authorId);
                    if (name != null && !name.isBlank()) {
                        return name;
                    }
                }

                String firstName = columns.firstExisting("first_name", "firstname", "prenom");
                String lastName = columns.firstExisting("last_name", "lastname", "nom");
                if (firstName != null || lastName != null) {
                    String first = firstName == null ? "" : valueOrDefault(readAuthorValue(table, firstName, authorId), "");
                    String last = lastName == null ? "" : valueOrDefault(readAuthorValue(table, lastName, authorId), "");
                    String combined = (first + " " + last).trim();
                    if (!combined.isBlank()) {
                        return combined;
                    }
                }
            } catch (SQLException ignored) {
                // Author display is optional; keep loading posts even if a user table differs.
            }
        }

        return "User #" + authorId;
    }

    private String readAuthorValue(String tableName, String columnName, int authorId) throws SQLException {
        if (!"user".equals(tableName) && !"doctor".equals(tableName) && !"admin".equals(tableName)) {
            return null;
        }

        String sql = "SELECT " + columnName + " FROM " + tableName + " WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, authorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String displayName(User user) {
        if (user == null) {
            return "PinkShield Member";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName().trim();
        }
        String combined = (valueOrDefault(user.getFirstName(), "") + " " + valueOrDefault(user.getLastName(), "")).trim();
        if (!combined.isBlank()) {
            return combined;
        }
        return valueOrDefault(user.getEmail(), "PinkShield Member");
    }

    private String readSetting(String key, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return defaultValue;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? throwable.getClass().getSimpleName()
                : current.getMessage();
    }

    private void bindValues(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            int parameterIndex = i + 1;
            if (value == null) {
                ps.setObject(parameterIndex, null);
            } else if (value instanceof Boolean bool) {
                ps.setInt(parameterIndex, bool ? 1 : 0);
            } else if (value instanceof Integer integer) {
                ps.setInt(parameterIndex, integer);
            } else if (value instanceof Timestamp timestamp) {
                ps.setTimestamp(parameterIndex, timestamp);
            } else {
                ps.setString(parameterIndex, value.toString());
            }
        }
    }

    private TableColumns loadColumns(String tableName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        Map<String, String> realNames = new HashMap<>();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, tableName, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                columns.add(name.toLowerCase(Locale.ROOT));
                realNames.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
        return new TableColumns(columns, realNames);
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is unavailable.");
        }
    }

    private String safeOrderColumn(String columnName) {
        return columnName == null ? "id" : columnName;
    }

    private String readString(ResultSet rs, String columnName) throws SQLException {
        return columnName == null ? null : rs.getString(columnName);
    }

    private Timestamp readTimestamp(ResultSet rs, String columnName) throws SQLException {
        return columnName == null ? null : rs.getTimestamp(columnName);
    }

    private boolean readBoolean(ResultSet rs, String columnName) throws SQLException {
        return rs.getInt(columnName) == 1;
    }

    private boolean readBooleanDefaultTrue(ResultSet rs, String columnName) throws SQLException {
        return rs.getObject(columnName) == null || rs.getInt(columnName) == 1;
    }

    private Integer readNullableInt(ResultSet rs, String columnName) throws SQLException {
        if (columnName == null) {
            return null;
        }
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record ForumPost(
            int id,
            String title,
            String content,
            int authorId,
            String authorName,
            boolean doctorPost,
            boolean allowComments,
            String imagePath,
            Timestamp createdAt,
            int likeCount,
            int commentCount,
            boolean likedByCurrentUser
    ) {
    }

    public record ForumComment(
            int id,
            int postId,
            Integer parentCommentId,
            int authorId,
            String authorName,
            String content,
            Timestamp createdAt,
            List<ForumComment> replies
    ) {
    }

    private record TableColumns(Set<String> lowerCaseNames, Map<String, String> realNames) {
        private String firstExisting(String... candidates) {
            for (String candidate : candidates) {
                String key = candidate.toLowerCase(Locale.ROOT);
                if (lowerCaseNames.contains(key)) {
                    return realNames.get(key);
                }
            }
            return null;
        }
    }

    private static final class MutableCommentNode {
        private final int id;
        private final int postId;
        private final Integer parentCommentId;
        private final int authorId;
        private final String authorName;
        private final String content;
        private final Timestamp createdAt;
        private final List<MutableCommentNode> replies = new ArrayList<>();

        private MutableCommentNode(int id, int postId, Integer parentCommentId, int authorId, String authorName, String content, Timestamp createdAt) {
            this.id = id;
            this.postId = postId;
            this.parentCommentId = parentCommentId;
            this.authorId = authorId;
            this.authorName = authorName;
            this.content = content;
            this.createdAt = createdAt;
        }

        private ForumComment toRecord() {
            return new ForumComment(
                    id,
                    postId,
                    parentCommentId,
                    authorId,
                    authorName,
                    content,
                    createdAt,
                    replies.stream().map(MutableCommentNode::toRecord).toList()
            );
        }
    }
}
