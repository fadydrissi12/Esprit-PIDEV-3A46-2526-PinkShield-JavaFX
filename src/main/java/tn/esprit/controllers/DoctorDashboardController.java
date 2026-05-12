package tn.esprit.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tn.esprit.entities.User;
import tn.esprit.services.AppointmentService;
import tn.esprit.services.UserService;
import tn.esprit.utils.AppNavigator;
import tn.esprit.utils.FormValidator;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class DoctorDashboardController {
    private static final DateTimeFormatter PROFILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label welcomeLabel;
    @FXML private Label feedbackLabel;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField emailField;
    @FXML private TextField specialityField;
    @FXML private PasswordField passwordField;
    @FXML private TextField accountIdField;
    @FXML private TextField createdAtField;
    @FXML private Label totalAppointmentsLabel;
    @FXML private Label upcomingAppointmentsLabel;
    @FXML private Label dashboardRoleLabel;
    @FXML private Label doctorNameSummaryLabel;
    @FXML private Label doctorEmailSummaryLabel;
    @FXML private Label doctorSpecialitySummaryLabel;

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
    private final AppointmentService appointmentService = new AppointmentService();
    private User loggedInUser;

    @FXML
    public void initialize() {
        FormValidator.attachClearOnInput(feedbackLabel, firstNameField, lastNameField, emailField, specialityField, passwordField);
    }

    public void setLoggedInUser(User user) {
        loggedInUser = user;
        if (user == null) {
            return;
        }

        welcomeLabel.setText("Dr. " + user.getLastName() + ", your workspace is ready");
        dashboardRoleLabel.setText(user.getSpeciality() == null || user.getSpeciality().isBlank()
                ? "Doctor account"
                : user.getSpeciality());
        populateProfile();
        populateDashboardSummary();
        updateAppointmentCards();
    }

    @FXML
    public void showDashboard() {
        updateNavStyles(navDashboard);
        populateDashboardSummary();
        updateAppointmentCards();
        mainContent.getChildren().setAll(dashboardView);
    }

    @FXML
    public void showProfileEdit() {
        updateNavStyles(navProfileEdit);
        populateProfile();
        FormValidator.setMessage(feedbackLabel, "", true);
        mainContent.getChildren().setAll(profileEditView);
    }

    @FXML
    public void showAppointments() {
        updateNavStyles(navAppointments);
        loadView("/fxml/appointment_list.fxml");
    }

    @FXML
    public void showBlog() {
        updateNavStyles(navBlog);
        loadView("/fxml/blog_list.fxml");
    }

    @FXML
    public void showProducts() {
        updateNavStyles(navProducts);
        loadView("/fxml/product_list.fxml");
    }

    @FXML
    public void showDailyCheckIn() {
        updateNavStyles(navDailyCheckIn);
        loadView("/fxml/daily_tracking.fxml");
    }

    @FXML
    public void handleResetProfile() {
        populateProfile();
        FormValidator.setMessage(feedbackLabel, "", true);
    }

    @FXML
    public void handleSaveProfile() {
        if (loggedInUser == null) {
            return;
        }

        FormValidator.clearStates(firstNameField, lastNameField, emailField, specialityField);

        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String speciality = specialityField.getText().trim();
        String password = passwordField.getText();

        if (firstName.isEmpty()) {
            FormValidator.markInvalid(firstNameField);
            FormValidator.setMessage(feedbackLabel, "First name is required.", true);
            return;
        }
        if (lastName.isEmpty()) {
            FormValidator.markInvalid(lastNameField);
            FormValidator.setMessage(feedbackLabel, "Last name is required.", true);
            return;
        }
        if (!FormValidator.isValidEmail(email)) {
            FormValidator.markInvalid(emailField);
            FormValidator.setMessage(feedbackLabel, "Enter a valid email address.", true);
            return;
        }
        if (speciality.isEmpty()) {
            FormValidator.markInvalid(specialityField);
            FormValidator.setMessage(feedbackLabel, "Speciality is required.", true);
            return;
        }
        if (!password.isBlank() && password.length() < 8) {
            FormValidator.markInvalid(passwordField);
            FormValidator.setMessage(feedbackLabel, "Password must contain at least 8 characters.", true);
            return;
        }

        loggedInUser.setFirstName(firstName);
        loggedInUser.setLastName(lastName);
        loggedInUser.setFullName((firstName + " " + lastName).trim());
        loggedInUser.setEmail(email);
        loggedInUser.setSpeciality(speciality);
        if (!password.isBlank()) {
            loggedInUser.setPassword(password);
        }

        String validationMessage = userService.validateUser(loggedInUser, loggedInUser.getPassword(), true);
        if (validationMessage != null) {
            FormValidator.markInvalid(emailField);
            FormValidator.setMessage(feedbackLabel, validationMessage, true);
            return;
        }

        if (userService.updateUser(loggedInUser)) {
            welcomeLabel.setText("Dr. " + loggedInUser.getLastName() + ", your workspace is ready");
            dashboardRoleLabel.setText(loggedInUser.getSpeciality());
            populateDashboardSummary();
            FormValidator.setMessage(feedbackLabel, "Profile updated successfully.", false);
        } else {
            FormValidator.setMessage(feedbackLabel, "Failed to update profile.", true);
        }
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

    private void populateProfile() {
        if (loggedInUser == null) {
            return;
        }
        firstNameField.setText(loggedInUser.getFirstName());
        lastNameField.setText(loggedInUser.getLastName());
        emailField.setText(loggedInUser.getEmail());
        specialityField.setText(loggedInUser.getSpeciality() == null ? "" : loggedInUser.getSpeciality());
        passwordField.clear();
        accountIdField.setText(String.valueOf(loggedInUser.getId()));
        createdAtField.setText(loggedInUser.getCreatedAt() == null
                ? ""
                : loggedInUser.getCreatedAt().toLocalDateTime().format(PROFILE_TIMESTAMP_FORMAT));
    }

    private void populateDashboardSummary() {
        if (loggedInUser == null) {
            return;
        }
        doctorNameSummaryLabel.setText("Name: Dr. " + loggedInUser.getFullName());
        doctorEmailSummaryLabel.setText("Email: " + loggedInUser.getEmail());
        doctorSpecialitySummaryLabel.setText("Speciality: "
                + (loggedInUser.getSpeciality() == null || loggedInUser.getSpeciality().isBlank()
                ? "Not specified"
                : loggedInUser.getSpeciality()));
    }

    private void updateAppointmentCards() {
        totalAppointmentsLabel.setText(String.valueOf(appointmentService.countAllAppointments()));
        upcomingAppointmentsLabel.setText(String.valueOf(appointmentService.countAllUpcomingAppointments()));
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
