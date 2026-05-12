package tn.esprit.controllers;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import tn.esprit.entities.User;
import tn.esprit.services.ForumService;
import tn.esprit.services.ForumService.ForumComment;
import tn.esprit.services.ForumService.ForumPost;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ForumController {
    @FXML private ListView<ForumPost> postListView;
    @FXML private ListView<ForumComment> commentListView;
    @FXML private TextField postTitleField;
    @FXML private TextArea postContentArea;
    @FXML private CheckBox disableCommentsCheckBox;
    @FXML private TextField commentField;
    @FXML private Button addCommentButton;
    @FXML private Button likeButton;
    @FXML private Button refreshButton;
    @FXML private Label feedbackLabel;
    @FXML private Label selectedPostTitleLabel;
    @FXML private Label selectedPostMetaLabel;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ForumService forumService = new ForumService();
    private User loggedInUser;
    private ForumPost selectedPost;

    @FXML
    public void initialize() {
        configurePostList();
        configureCommentList();
        updateCommentControls(null);
        showDoctorControls();
        hideLegacyLikeButton();
        loadPosts();
    }

    public void setLoggedInUser(User user) {
        this.loggedInUser = user;
        showDoctorControls();
        loadPosts();
    }

    @FXML
    private void handleCreatePost() {
        if (loggedInUser == null) {
            showError("Sign in before creating a forum post.");
            return;
        }

        String title = postTitleField == null ? "" : postTitleField.getText();
        String content = postContentArea == null ? "" : postContentArea.getText();
        boolean disableComments = forumService.isDoctor(loggedInUser)
                && disableCommentsCheckBox != null
                && disableCommentsCheckBox.isSelected();

        runBackground(() -> {
            forumService.createPost(loggedInUser, title, content, disableComments);
            return null;
        }, ignored -> {
            clearPostForm();
            loadPosts();
            showInfo("Post published.");
        });
    }

    @FXML
    private void handleRefresh() {
        loadPosts();
    }

    @FXML
    private void handleToggleLike() {
        if (selectedPost == null) {
            showError("Choose a post first.");
            return;
        }
        if (loggedInUser == null) {
            showError("Sign in before liking a post.");
            return;
        }

        int postId = selectedPost.id();
        runBackground(() -> {
            forumService.toggleLike(postId, loggedInUser);
            return null;
        }, ignored -> loadPostsAndKeepSelection(postId));
    }

    private void togglePostLike(ForumPost post) {
        if (post == null) {
            return;
        }
        if (loggedInUser == null) {
            showError("Sign in before liking a post.");
            return;
        }

        runBackground(() -> {
            forumService.toggleLike(post.id(), loggedInUser);
            return null;
        }, ignored -> loadPostsAndKeepSelection(post.id()));
    }

    @FXML
    private void handleAddComment() {
        addComment(null);
    }

    private void addComment(Integer parentCommentId) {
        if (selectedPost == null) {
            showError("Choose a post first.");
            return;
        }
        if (!selectedPost.allowComments()) {
            showError("Comments are disabled for this post.");
            return;
        }
        if (loggedInUser == null) {
            showError("Sign in before commenting.");
            return;
        }

        String content = commentField == null ? "" : commentField.getText();
        int postId = selectedPost.id();

        runBackground(() -> {
            forumService.addComment(postId, loggedInUser, content, parentCommentId);
            return null;
        }, ignored -> {
            if (commentField != null) {
                commentField.clear();
            }
            loadComments(postId);
            showInfo(parentCommentId == null ? "Comment added." : "Reply added.");
        });
    }

    private void loadPosts() {
        runBackground(() -> forumService.fetchAllPosts(loggedInUser), posts -> {
            if (postListView != null) {
                postListView.setItems(FXCollections.observableArrayList(posts));
            }
            if (!posts.isEmpty()) {
                selectPost(posts.get(0));
            } else {
                selectPost(null);
            }
        });
    }

    private void loadPostsAndKeepSelection(int postId) {
        runBackground(() -> forumService.fetchAllPosts(loggedInUser), posts -> {
            if (postListView != null) {
                postListView.setItems(FXCollections.observableArrayList(posts));
            }
            ForumPost refreshed = posts.stream()
                    .filter(post -> post.id() == postId)
                    .findFirst()
                    .orElse(posts.isEmpty() ? null : posts.get(0));
            selectPost(refreshed);
        });
    }

    private void loadComments(int postId) {
        runBackground(() -> forumService.fetchCommentsWithReplies(postId), comments -> {
            if (commentListView != null) {
                commentListView.setItems(FXCollections.observableArrayList(comments));
            }
        });
    }

    private void configurePostList() {
        if (postListView == null) {
            return;
        }

        postListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ForumPost post, boolean empty) {
                super.updateItem(post, empty);
                setPadding(new Insets(0, 0, 14, 0));
                if (empty || post == null) {
                    setText(null);
                    setGraphic(null);
                    getStyleClass().remove("doctor-forum-post");
                    return;
                }

                VBox container = new VBox(14);
                container.setStyle("""
                        -fx-background-color: #1e1e2f;
                        -fx-background-radius: 15;
                        -fx-padding: 15;
                        """);

                HBox header = new HBox(10);
                header.setStyle("-fx-alignment: center-left;");

                StackPane avatar = new StackPane();
                avatar.setMinSize(42, 42);
                avatar.setPrefSize(42, 42);
                avatar.setMaxSize(42, 42);
                avatar.setStyle("-fx-background-color: #475569; -fx-background-radius: 999;");
                FontAwesomeIconView avatarIcon = new FontAwesomeIconView();
                avatarIcon.setGlyphName("USER");
                avatarIcon.setSize("18");
                avatarIcon.setStyle("-fx-fill: #ffffff;");
                avatar.getChildren().add(avatarIcon);

                VBox authorBox = new VBox(3);
                Label author = new Label(post.authorName());
                author.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 14px;");
                Label date = new Label(post.createdAt() == null ? "" : post.createdAt().toLocalDateTime().format(DATE_FORMAT));
                date.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 11px;");
                authorBox.getChildren().addAll(author, date);

                Region headerSpacer = new Region();
                HBox.setHgrow(headerSpacer, Priority.ALWAYS);
                Label badge = new Label("Doctor Tip");
                badge.setVisible(post.doctorPost());
                badge.setManaged(post.doctorPost());
                badge.setStyle("-fx-background-color: #ff4757; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 4 10; -fx-font-size: 11px;");
                header.getChildren().addAll(avatar, authorBox, headerSpacer, badge);

                VBox body = new VBox(8);
                Label title = new Label(post.title());
                title.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 16px;");
                title.setWrapText(true);

                Label content = new Label(post.content());
                content.setStyle("-fx-text-fill: #ced6e0; -fx-font-size: 13px;");
                content.setWrapText(true);
                body.getChildren().addAll(title, content);

                ImageView imageView = createPostImageView(post.imagePath());
                body.getChildren().add(imageView);

                HBox footer = new HBox(8);
                footer.setStyle("-fx-alignment: center-left; -fx-padding: 2 0 0 0;");
                FontAwesomeIconView replyIcon = new FontAwesomeIconView();
                replyIcon.setGlyphName("COMMENT_ALT");
                replyIcon.setSize("15");
                replyIcon.setStyle("-fx-fill: #a4b0be;");
                Label replyLabel = new Label("Reply");
                replyLabel.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 12px; -fx-font-weight: bold;");
                Label commentCount = new Label(post.commentCount() + " comment" + (post.commentCount() == 1 ? "" : "s"));
                commentCount.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 12px;");
                replyIcon.setOnMouseClicked(event -> selectPost(post));
                replyLabel.setOnMouseClicked(event -> selectPost(post));
                commentCount.setOnMouseClicked(event -> selectPost(post));
                Region footerSpacer = new Region();
                HBox.setHgrow(footerSpacer, Priority.ALWAYS);
                FontAwesomeIconView heartIcon = new FontAwesomeIconView();
                heartIcon.setGlyphName("HEART");
                heartIcon.setSize("22");
                heartIcon.setStyle(post.likedByCurrentUser() ? "-fx-fill: #ff4757;" : "-fx-fill: #747d8c;");
                heartIcon.setOnMouseClicked(event -> togglePostLike(post));
                Label likeCount = new Label(Integer.toString(post.likeCount()));
                likeCount.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 12px; -fx-font-weight: bold;");

                Label editAction = new Label("Edit");
                editAction.setStyle("-fx-text-fill: #70a1ff; -fx-font-size: 12px; -fx-font-weight: bold;");
                editAction.setOnMouseClicked(event -> editPost(post));
                Label deleteAction = new Label("Delete");
                deleteAction.setStyle("-fx-text-fill: #ff4757; -fx-font-size: 12px; -fx-font-weight: bold;");
                deleteAction.setOnMouseClicked(event -> deletePost(post));
                boolean canManage = canManagePost(post);
                editAction.setVisible(canManage);
                editAction.setManaged(canManage);
                deleteAction.setVisible(canManage);
                deleteAction.setManaged(canManage);

                footer.getChildren().addAll(replyIcon, replyLabel, commentCount, footerSpacer, editAction, deleteAction, heartIcon, likeCount);

                container.getChildren().addAll(header, body, footer);

                setText(null);
                setGraphic(container);
            }
        });

        postListView.getSelectionModel().selectedItemProperty().addListener((obs, oldPost, newPost) -> selectPost(newPost));
    }

    private void configureCommentList() {
        if (commentListView == null) {
            return;
        }

        commentListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ForumComment comment, boolean empty) {
                super.updateItem(comment, empty);
                if (empty || comment == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(renderComment(comment, 0));
            }
        });
    }

    private VBox renderComment(ForumComment comment, int depth) {
        VBox wrapper = new VBox(7);
        wrapper.setPadding(new Insets(8, 8, 8, 8 + (depth * 18)));

        Label content = new Label(comment.content());
        content.setWrapText(true);
        Label meta = new Label(comment.authorName() + formatDate(comment.createdAt()));
        meta.setStyle("-fx-text-fill: #a4b0be; -fx-font-size: 11;");

        HBox actions = new HBox(8);
        if (selectedPost != null && selectedPost.allowComments()) {
            Button replyButton = new Button("Reply");
            replyButton.setOnAction(event -> addComment(comment.id()));
            actions.getChildren().add(replyButton);
        }

        wrapper.getChildren().addAll(content, meta);
        if (!actions.getChildren().isEmpty()) {
            wrapper.getChildren().add(actions);
        }

        for (ForumComment reply : comment.replies()) {
            wrapper.getChildren().add(renderComment(reply, depth + 1));
        }

        return wrapper;
    }

    private void selectPost(ForumPost post) {
        selectedPost = post;
        if (postListView != null && post != null && postListView.getSelectionModel().getSelectedItem() != post) {
            postListView.getSelectionModel().select(post);
        }

        if (selectedPostTitleLabel != null) {
            selectedPostTitleLabel.setText(post == null ? "Select a post" : post.title());
        }
        if (selectedPostMetaLabel != null) {
            selectedPostMetaLabel.setText(post == null ? "" : buildPostMeta(post));
        }
        if (likeButton != null) {
            likeButton.setDisable(post == null);
            likeButton.setText(post != null && post.likedByCurrentUser() ? "Unlike" : "Heart");
            likeButton.setVisible(false);
            likeButton.setManaged(false);
        }

        updateCommentControls(post);
        if (post == null) {
            if (commentListView != null) {
                commentListView.getItems().clear();
            }
        } else {
            loadComments(post.id());
        }
    }

    private void updateCommentControls(ForumPost post) {
        boolean allowComments = post != null && post.allowComments();
        if (commentField != null) {
            commentField.setVisible(allowComments);
            commentField.setManaged(allowComments);
            commentField.setDisable(!allowComments);
        }
        if (addCommentButton != null) {
            addCommentButton.setVisible(allowComments);
            addCommentButton.setManaged(allowComments);
            addCommentButton.setDisable(!allowComments);
        }
    }

    private void showDoctorControls() {
        boolean doctor = forumService.isDoctor(loggedInUser);
        if (disableCommentsCheckBox != null) {
            disableCommentsCheckBox.setVisible(doctor);
            disableCommentsCheckBox.setManaged(doctor);
            if (!doctor) {
                disableCommentsCheckBox.setSelected(false);
            }
        }
    }

    private void clearPostForm() {
        if (postTitleField != null) {
            postTitleField.clear();
        }
        if (postContentArea != null) {
            postContentArea.clear();
        }
        if (disableCommentsCheckBox != null) {
            disableCommentsCheckBox.setSelected(false);
        }
    }

    private String buildPostMeta(ForumPost post) {
        return post.authorName()
                + " | " + post.likeCount() + " heart(s)"
                + (post.allowComments() ? " | Comments open" : " | Comments disabled")
                + formatDate(post.createdAt());
    }

    private ImageView createPostImageView(String imagePath) {
        ImageView imageView = new ImageView();
        imageView.setFitHeight(220);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        if (imagePath == null || imagePath.isBlank()) {
            hideImage(imageView);
            return imageView;
        }

        try {
            String source = resolveImageSource(imagePath.trim());
            if (source == null || source.isBlank()) {
                hideImage(imageView);
                return imageView;
            }
            if (!source.startsWith("http://") && !source.startsWith("https://") && !source.startsWith("file:")) {
                File file = new File(source);
                if (!file.isFile()) {
                    hideImage(imageView);
                    return imageView;
                }
                source = file.toURI().toString();
            }

            Image image = new Image(source, 0, 220, true, true, false);
            if (image.isError()) {
                hideImage(imageView);
                return imageView;
            }

            imageView.setImage(image);
            imageView.setVisible(true);
            imageView.setManaged(true);
            return imageView;
        } catch (IllegalArgumentException e) {
            hideImage(imageView);
            return imageView;
        }
    }

    private String resolveImageSource(String rawPath) {
        if (rawPath.startsWith("http://") || rawPath.startsWith("https://") || rawPath.startsWith("file:")) {
            return rawPath;
        }

        String normalized = rawPath.replace("\\", "/");
        Path direct = Path.of(rawPath);
        if (Files.isRegularFile(direct)) {
            return direct.toUri().toString();
        }

        List<Path> candidates = List.of(
                Path.of(System.getProperty("user.dir"), normalized),
                Path.of(System.getProperty("user.dir"), "public", normalized),
                Path.of(System.getProperty("user.dir")).getParent() == null ? Path.of(normalized) : Path.of(System.getProperty("user.dir")).getParent().resolve("public").resolve(normalized),
                Path.of("C:/Users/driss/Downloads/Esprit-PIDEV-3A46-2526-PinkShield/public").resolve(normalized),
                Path.of("C:/Users/driss/Downloads/PinkShield-main/public").resolve(normalized),
                Path.of("C:/Users/driss/Downloads/PinkShield-main (1)/public").resolve(normalized),
                Path.of("C:/Users/driss/Downloads/PinkShield-Gestion_Blog/public").resolve(normalized)
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toUri().toString();
            }
        }

        String webPath = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        return "http://127.0.0.1:8000/" + webPath;
    }

    private void editPost(ForumPost post) {
        if (post == null) {
            return;
        }
        TextInputDialog titleDialog = new TextInputDialog(post.title());
        titleDialog.setTitle("Edit Post");
        titleDialog.setHeaderText("Update post title");
        titleDialog.setContentText("Title:");
        Optional<String> title = titleDialog.showAndWait();
        if (title.isEmpty()) {
            return;
        }

        TextInputDialog contentDialog = new TextInputDialog(post.content());
        contentDialog.setTitle("Edit Post");
        contentDialog.setHeaderText("Update post content");
        contentDialog.setContentText("Content:");
        Optional<String> content = contentDialog.showAndWait();
        if (content.isEmpty()) {
            return;
        }

        runBackground(() -> forumService.updatePost(post.id(), title.get(), content.get(), !post.allowComments(), loggedInUser), updated -> {
            loadPostsAndKeepSelection(post.id());
            showInfo(updated ? "Post updated." : "Post could not be updated.");
        });
    }

    private void deletePost(ForumPost post) {
        if (post == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Post");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete this post?");
        confirm.showAndWait().ifPresent(button -> {
            if (button.getButtonData().isCancelButton()) {
                return;
            }
            runBackground(() -> forumService.deletePost(post.id(), loggedInUser), deleted -> {
                loadPosts();
                showInfo(deleted ? "Post deleted." : "Post could not be deleted.");
            });
        });
    }

    private boolean canManagePost(ForumPost post) {
        if (post == null || loggedInUser == null) {
            return false;
        }
        if (forumService.isDoctor(loggedInUser)) {
            return true;
        }
        if (post.authorId() > 0 && loggedInUser.getId() == post.authorId()) {
            return true;
        }
        return loggedInUser.getEmail() != null
                && post.authorName() != null
                && loggedInUser.getEmail().equalsIgnoreCase(post.authorName());
    }

    private void hideImage(ImageView imageView) {
        imageView.setImage(null);
        imageView.setVisible(false);
        imageView.setManaged(false);
    }

    private void hideLegacyLikeButton() {
        if (likeButton != null) {
            likeButton.setVisible(false);
            likeButton.setManaged(false);
        }
    }

    private String formatDate(java.sql.Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return " | " + timestamp.toLocalDateTime().format(DATE_FORMAT);
    }

    private void showInfo(String message) {
        if (feedbackLabel != null) {
            feedbackLabel.setText(message);
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
            return;
        }
        System.out.println(message);
    }

    private void showError(String message) {
        if (feedbackLabel != null) {
            feedbackLabel.setText(message);
            feedbackLabel.setVisible(true);
            feedbackLabel.setManaged(true);
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Forum");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private <T> void runBackground(ForumTask<T> forumTask, SuccessHandler<T> successHandler) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return forumTask.run();
            }
        };

        task.setOnSucceeded(event -> successHandler.handle(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            showError(error instanceof SQLException && error.getMessage() != null
                    ? error.getMessage()
                    : "Forum action failed. Please try again.");
        });

        Thread thread = new Thread(task, "pinkshield-forum-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FunctionalInterface
    private interface ForumTask<T> {
        T run() throws Exception;
    }

    @FunctionalInterface
    private interface SuccessHandler<T> {
        void handle(T value);
    }
}
