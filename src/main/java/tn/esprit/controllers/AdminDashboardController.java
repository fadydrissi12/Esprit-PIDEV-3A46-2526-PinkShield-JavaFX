package tn.esprit.controllers;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import tn.esprit.entities.User;
import tn.esprit.entities.UserDashboardStats;
import tn.esprit.services.UserService;
import tn.esprit.utils.AppNavigator;
import tn.esprit.utils.FormValidator;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class AdminDashboardController {
    private static final DateTimeFormatter PROFILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Integer> idColumn;
    @FXML private TableColumn<User, String> nameColumn;
    @FXML private TableColumn<User, String> emailColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> detailsColumn;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilterCombo;
    @FXML private ComboBox<String> sortCombo;
    @FXML private Label welcomeLabel;
    @FXML private Label totalUsersLabel;
    @FXML private Label adminsCountLabel;
    @FXML private Label doctorsCountLabel;
    @FXML private Label patientsCountLabel;
    @FXML private Label tableInfoLabel;
    @FXML private Label profileFeedbackLabel;
    @FXML private TextField adminFirstNameField;
    @FXML private TextField adminLastNameField;
    @FXML private TextField adminEmailField;
    @FXML private PasswordField adminPasswordField;
    @FXML private TextField adminAccountIdField;
    @FXML private TextField adminCreatedAtField;

    @FXML private StackPane mainContent;
    @FXML private VBox dashboardView;
    @FXML private VBox profileEditView;
    @FXML private Button navDashboard;
    @FXML private Button navAppointments;
    @FXML private Button navProfileEdit;
    @FXML private Button navBlog;
    @FXML private Button navProducts;
    @FXML private Button navDailyCheckIn;

    private final UserService userService = new UserService();
    private final ObservableList<User> usersList = FXCollections.observableArrayList();
    private User loggedInUser;

    @FXML
    public void initialize() {
        setupTableColumns();
        setupFilters();
        setupRowInteractions();

        if (profileFeedbackLabel != null) {
            FormValidator.attachClearOnInput(profileFeedbackLabel, adminFirstNameField, adminLastNameField, adminEmailField, adminPasswordField);
        }

        refreshUserManagement();
    }

    @FXML
    public void showDashboard() {
        updateNavStyles(navDashboard);
        mainContent.getChildren().setAll(dashboardView);
        refreshUserManagement();
    }

    @FXML
    public void showProfileEdit() {
        updateNavStyles(navProfileEdit);
        populateProfile();
        FormValidator.setMessage(profileFeedbackLabel, "", true);
        mainContent.getChildren().setAll(profileEditView);
    }

    @FXML
    public void showBlogManagement() {
        updateNavStyles(navBlog);
        loadView("/fxml/blog_list.fxml");
    }

    @FXML
    public void showAppointmentManagement() {
        updateNavStyles(navAppointments);
        loadView("/fxml/appointment_list.fxml");
    }

    @FXML
    public void showProductManagement() {
        updateNavStyles(navProducts);
        loadView("/fxml/product_list.fxml");
    }

    @FXML
    public void showDailyCheckIn() {
        updateNavStyles(navDailyCheckIn);
        loadView("/fxml/daily_tracking.fxml");
    }

    @FXML
    public void handleSaveProfile() {
        if (loggedInUser == null) {
            return;
        }

        FormValidator.clearStates(adminFirstNameField, adminLastNameField, adminEmailField);

        String firstName = adminFirstNameField.getText().trim();
        String lastName = adminLastNameField.getText().trim();
        String email = adminEmailField.getText().trim();
        String password = adminPasswordField.getText();

        if (firstName.isEmpty()) {
            FormValidator.markInvalid(adminFirstNameField);
            FormValidator.setMessage(profileFeedbackLabel, "First name is required.", true);
            return;
        }
        if (lastName.isEmpty()) {
            FormValidator.markInvalid(adminLastNameField);
            FormValidator.setMessage(profileFeedbackLabel, "Last name is required.", true);
            return;
        }
        if (!FormValidator.isValidEmail(email)) {
            FormValidator.markInvalid(adminEmailField);
            FormValidator.setMessage(profileFeedbackLabel, "Enter a valid email address.", true);
            return;
        }
        if (!password.isBlank() && password.length() < 8) {
            FormValidator.markInvalid(adminPasswordField);
            FormValidator.setMessage(profileFeedbackLabel, "Password must contain at least 8 characters.", true);
            return;
        }

        loggedInUser.setFirstName(firstName);
        loggedInUser.setLastName(lastName);
        loggedInUser.setFullName((firstName + " " + lastName).trim());
        loggedInUser.setEmail(email);
        if (!password.isBlank()) {
            loggedInUser.setPassword(password);
        }

        String validationMessage = userService.validateUser(loggedInUser, loggedInUser.getPassword(), true);
        if (validationMessage != null) {
            if (validationMessage.toLowerCase().contains("email")) {
                FormValidator.markInvalid(adminEmailField);
            }
            FormValidator.setMessage(profileFeedbackLabel, validationMessage, true);
            return;
        }

        if (userService.updateUser(loggedInUser)) {
            welcomeLabel.setText("Administrator " + loggedInUser.getFullName());
            FormValidator.setMessage(profileFeedbackLabel, "Profile updated successfully.", false);
        } else {
            FormValidator.setMessage(profileFeedbackLabel, "Failed to update profile.", true);
        }
    }

    @FXML
    public void handleResetProfile() {
        populateProfile();
        FormValidator.setMessage(profileFeedbackLabel, "", true);
    }

    @FXML
    public void handleAddUser() {
        openUserForm(null);
    }

    @FXML
    public void handleEditUser() {
        User selectedUser = usersTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Warning", "Select a user to edit.", Alert.AlertType.WARNING);
            return;
        }
        openUserForm(selectedUser);
    }

    @FXML
    public void handleDeleteUser() {
        User selectedUser = usersTable.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Warning", "Select a user to delete.", Alert.AlertType.WARNING);
            return;
        }

        if (loggedInUser != null
                && selectedUser.getId() == loggedInUser.getId()
                && selectedUser.getRole().equals(loggedInUser.getRole())) {
            showAlert("Warning", "You cannot delete the account currently connected.", Alert.AlertType.WARNING);
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete user");
        confirmAlert.setHeaderText("Remove " + selectedUser.getFullName());
        confirmAlert.setContentText("This action permanently deletes the selected account.");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (userService.deleteUser(selectedUser.getId(), selectedUser.getRole())) {
                showAlert("Success", "User deleted successfully.", Alert.AlertType.INFORMATION);
                refreshUserManagement();
            } else {
                showAlert("Error", "Failed to delete user.", Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    public void handleRefreshUsers() {
        refreshUserManagement();
    }

    @FXML
    public void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = AppNavigator.createScene(loader.load(), getClass());

            Stage stage = (Stage) mainContent.getScene().getWindow();
            AppNavigator.applyStage(stage, scene, "PinkShield Login");
        } catch (IOException e) {
            showAlert("Error", "Failed to load login page.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    public void setLoggedInUser(User user) {
        loggedInUser = user;
        if (welcomeLabel != null && user != null) {
            welcomeLabel.setText("Administrator " + user.getFullName());
        }
        populateProfile();
        refreshUserManagement();
    }

    private void populateProfile() {
        if (loggedInUser == null || adminFirstNameField == null) {
            return;
        }
        adminFirstNameField.setText(loggedInUser.getFirstName());
        adminLastNameField.setText(loggedInUser.getLastName());
        adminEmailField.setText(loggedInUser.getEmail());
        adminPasswordField.clear();
        adminAccountIdField.setText(String.valueOf(loggedInUser.getId()));
        adminCreatedAtField.setText(loggedInUser.getCreatedAt() == null
                ? ""
                : loggedInUser.getCreatedAt().toLocalDateTime().format(PROFILE_TIMESTAMP_FORMAT));
    }

    private void setupFilters() {
        roleFilterCombo.setItems(FXCollections.observableArrayList("all", "admin", "doctor", "user"));
        roleFilterCombo.setValue("all");

        sortCombo.setItems(FXCollections.observableArrayList("name-asc", "name-desc", "email-asc", "role-asc"));
        sortCombo.setValue("name-asc");

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        roleFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        sortCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
    }

    private void setupRowInteractions() {
        usersTable.setRowFactory(tableView -> {
            TableRow<User> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openUserForm(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupTableColumns() {
        idColumn.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getId()).asObject());
        nameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFullName()));
        emailColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getEmail()));
        roleColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRole()));
        detailsColumn.setCellValueFactory(cellData -> new SimpleStringProperty(buildDetails(cellData.getValue())));

        roleColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("role-admin", "role-doctor", "role-user");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item.toUpperCase());
                getStyleClass().add("role-" + item.toLowerCase());
            }
        });
    }

    private void refreshUserManagement() {
        applyFilters();
        updateStats();
    }

    private void applyFilters() {
        usersList.setAll(userService.getUsers(
                searchField.getText(),
                roleFilterCombo.getValue(),
                sortCombo.getValue()
        ));
        usersTable.setItems(usersList);
        tableInfoLabel.setText(usersList.size() + " account(s) displayed");
    }

    private void updateStats() {
        UserDashboardStats stats = userService.getDashboardStats();
        totalUsersLabel.setText(String.valueOf(stats.getTotalCount()));
        adminsCountLabel.setText(String.valueOf(stats.getAdminCount()));
        doctorsCountLabel.setText(String.valueOf(stats.getDoctorCount()));
        patientsCountLabel.setText(String.valueOf(stats.getPatientCount()));
    }

    private String buildDetails(User user) {
        if (UserService.ROLE_DOCTOR.equals(user.getRole())) {
            return user.getSpeciality() == null || user.getSpeciality().isBlank()
                    ? "No speciality"
                    : user.getSpeciality();
        }
        if (UserService.ROLE_USER.equals(user.getRole())) {
            String phone = user.getPhone() == null || user.getPhone().isBlank() ? "No phone" : user.getPhone();
            String address = user.getAddress() == null || user.getAddress().isBlank() ? "No address" : user.getAddress();
            return phone + " | " + address;
        }
        return "System administrator";
    }

    private void openUserForm(User selectedUser) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/user_form.fxml"));
            Parent root = loader.load();

            UserFormController controller = loader.getController();
            if (selectedUser == null) {
                controller.configureForCreate();
            } else {
                controller.configureForEdit(selectedUser);
            }

            Stage stage = new Stage();
            stage.initOwner(mainContent.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL);
            stage.setTitle(selectedUser == null ? "Add user" : "Edit user");
            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();

            if (controller.isSaved()) {
                refreshUserManagement();
            }
        } catch (IOException e) {
            showAlert("Error", "Unable to open the user form.", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent view = loader.load();

            Object controller = loader.getController();
            if (controller instanceof BlogListController) {
                ((BlogListController) controller).setCurrentUser(loggedInUser);
            } else if (controller instanceof BlogDetailController) {
                ((BlogDetailController) controller).setCurrentUser(loggedInUser);
            } else if (controller instanceof ForumController) {
                ((ForumController) controller).setLoggedInUser(loggedInUser);
            } else if (controller instanceof AppointmentListController) {
                ((AppointmentListController) controller).setCurrentUser(loggedInUser);
            } else if (controller instanceof ProductListController) {
                ((ProductListController) controller).setCurrentUser(loggedInUser);
            } else if (controller instanceof DailyTrackingController) {
                ((DailyTrackingController) controller).setCurrentUser(loggedInUser);
            }

            mainContent.getChildren().setAll(view);
        } catch (IOException e) {
            showAlert("Error", "Could not load view: " + fxmlPath, Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void updateNavStyles(Button activeBtn) {
        navDashboard.getStyleClass().remove("active");
        navAppointments.getStyleClass().remove("active");
        navProfileEdit.getStyleClass().remove("active");
        navBlog.getStyleClass().remove("active");
        navProducts.getStyleClass().remove("active");
        navDailyCheckIn.getStyleClass().remove("active");
        activeBtn.getStyleClass().add("active");
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
