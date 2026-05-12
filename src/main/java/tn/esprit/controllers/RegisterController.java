package tn.esprit.controllers;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import tn.esprit.entities.User;
import tn.esprit.services.AuthService;
import tn.esprit.utils.FormValidator;

import java.io.IOException;
import java.util.Optional;

public class RegisterController {
    @FXML private RadioButton patientRadio;
    @FXML private RadioButton doctorRadio;
    @FXML private VBox patientFields;
    @FXML private VBox doctorFields;
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private TextField specialityField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerBtn;

    private AuthService authService = new AuthService();
    private static final String PATIENT_ROLE = "user";
    private static final String DOCTOR_ROLE = "doctor";

    @FXML
    public void initialize() {
        // Handle role change
        patientRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                patientFields.setVisible(true);
                patientFields.setManaged(true);
                doctorFields.setVisible(false);
                doctorFields.setManaged(false);
            }
        });

        doctorRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                patientFields.setVisible(false);
                patientFields.setManaged(false);
                doctorFields.setVisible(true);
                doctorFields.setManaged(true);
            }
        });
    }

    public void handleRegister() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Email and password are required", Alert.AlertType.ERROR);
            return;
        }

        if (!FormValidator.isValidEmail(email)) {
            showAlert("Error", "Invalid email format", Alert.AlertType.ERROR);
            return;
        }

        if (!FormValidator.hasValidMailDomain(email)) {
            showAlert("Error", "Use an email address with a real mail domain.", Alert.AlertType.ERROR);
            return;
        }

        if (password.length() < 8) {
            showAlert("Error", "Password must be at least 8 characters", Alert.AlertType.ERROR);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAlert("Error", "Passwords do not match", Alert.AlertType.ERROR);
            return;
        }

        if (authService.emailExists(email)) {
            showAlert("Error", "Email already exists", Alert.AlertType.ERROR);
            return;
        }

        String emailError = authService.sendRegistrationVerificationCode(email);
        if (emailError != null) {
            showAlert("Error", emailError, Alert.AlertType.ERROR);
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Verify Email");
        dialog.setHeaderText("Enter the 6-digit code sent to " + email);
        dialog.setContentText("Verification code:");
        Optional<String> code = dialog.showAndWait();
        if (code.isEmpty()) {
            showAlert("Error", "Email verification was cancelled. Request a new code to continue.", Alert.AlertType.ERROR);
            return;
        }

        String verificationError = authService.verifyRegistrationCode(email, code.get());
        if (verificationError != null) {
            showAlert("Error", verificationError, Alert.AlertType.ERROR);
            return;
        }

        boolean success = false;
        if (patientRadio.isSelected()) {
            success = registerPatient(email, password);
        } else {
            success = registerDoctor(email, password);
        }

        if (success) {
            showAlert("Success", "Registration successful! Please login.", Alert.AlertType.INFORMATION);
            goToLogin();
        } else {
            showAlert("Error", "Registration failed", Alert.AlertType.ERROR);
        }
    }

    private boolean registerPatient(String email, String password) {
        String fullName = fullNameField.getText().trim();
        String phone = phoneField.getText().trim();
        String address = addressField.getText().trim();

        if (fullName.isEmpty()) {
            showAlert("Error", "Full name is required for patient registration", Alert.AlertType.ERROR);
            return false;
        }

        return authService.registerPatient(fullName, email, password, phone, address);
    }

    private boolean registerDoctor(String email, String password) {
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String speciality = specialityField.getText().trim();

        if (firstName.isEmpty() || lastName.isEmpty()) {
            showAlert("Error", "First name and last name are required for doctor registration", Alert.AlertType.ERROR);
            return false;
        }

        return authService.registerDoctor(firstName, lastName, email, password, speciality);
    }

    public void handleBackToLogin() {
        goToLogin();
    }

    private void goToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Login");
            stage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to load login page", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}

