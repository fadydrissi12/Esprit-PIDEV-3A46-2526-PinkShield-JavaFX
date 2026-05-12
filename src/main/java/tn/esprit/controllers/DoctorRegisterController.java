package tn.esprit.controllers;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import tn.esprit.services.AuthService;
import tn.esprit.utils.AppNavigator;
import tn.esprit.utils.FormValidator;
import tn.esprit.utils.RecaptchaWidget;

import java.io.IOException;
import java.util.Optional;

public class DoctorRegisterController {
    @FXML private Label feedbackLabel;
    @FXML private TextField emailField;
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
    @FXML private ComboBox<String> specialityCombo;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label recaptchaStatusLabel;
    @FXML private Button registerButton;
    @FXML private WebView recaptchaWebView;

    private final AuthService authService = new AuthService();
    private final RecaptchaWidget recaptchaWidget = new RecaptchaWidget();

    @FXML
    public void initialize() {
        specialityCombo.setItems(FXCollections.observableArrayList(
                "Cardiology",
                "Dermatology",
                "General Medicine",
                "Gynecology",
                "Neurology",
                "Pediatrics",
                "Psychiatry",
                "Radiology"
        ));

        FormValidator.attachClearOnInput(
                feedbackLabel,
                emailField,
                firstNameField,
                lastNameField,
                passwordField,
                confirmPasswordField
        );
        specialityCombo.valueProperty().addListener((obs, oldValue, newValue) -> {
            specialityCombo.setStyle("");
            FormValidator.setMessage(feedbackLabel, "", true);
        });
        recaptchaWidget.attach(recaptchaWebView, recaptchaStatusLabel);
    }

    @FXML
    public void handleRegister() {
        clearFeedback();

        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();
        String email = emailField.getText().trim();
        String speciality = specialityCombo.getValue();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (firstName.isEmpty()) {
            showFieldError(firstNameField, "First name is required.");
            return;
        }
        if (lastName.isEmpty()) {
            showFieldError(lastNameField, "Last name is required.");
            return;
        }
        if (!FormValidator.isValidEmail(email)) {
            showFieldError(emailField, "Enter a valid email address.");
            return;
        }
        if (!FormValidator.hasValidMailDomain(email)) {
            showFieldError(emailField, "Use an email address with a real mail domain.");
            return;
        }
        if (speciality == null || speciality.isBlank()) {
            specialityCombo.setStyle("-fx-border-color: #ff6b6b; -fx-border-width: 2;");
            FormValidator.setMessage(feedbackLabel, "Medical speciality is required.", true);
            return;
        }
        if (password.length() < 8) {
            showFieldError(passwordField, "Password must contain at least 8 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            FormValidator.markInvalid(passwordField);
            FormValidator.markInvalid(confirmPasswordField);
            FormValidator.setMessage(feedbackLabel, "Password confirmation does not match.", true);
            return;
        }
        if (authService.emailExists(email)) {
            showFieldError(emailField, "This email address already exists.");
            return;
        }
        if (!recaptchaWidget.isConfigured()) {
            FormValidator.setMessage(feedbackLabel, recaptchaWidget.getConfigurationMessage(), true);
            return;
        }
        if (!recaptchaWidget.hasToken()) {
            FormValidator.setMessage(feedbackLabel, "Complete the reCAPTCHA security check first.", true);
            return;
        }

        registerButton.setDisable(true);
        registerButton.setText("Sending code...");

        Task<DoctorRegistrationResult> verificationTask = new Task<>() {
            @Override
            protected DoctorRegistrationResult call() {
                var verification = recaptchaWidget.verifyCurrentToken();
                if (!verification.success()) {
                    return DoctorRegistrationResult.failed(verification.message(), verification.resetRequired());
                }

                String emailError = authService.sendRegistrationVerificationCode(email);
                if (emailError != null) {
                    return DoctorRegistrationResult.failed(emailError, true);
                }

                return DoctorRegistrationResult.codeSent();
            }
        };

        verificationTask.setOnSucceeded(event -> {
            restoreRegisterButton();

            DoctorRegistrationResult result = verificationTask.getValue();
            if (result.resetCaptcha()) {
                recaptchaWidget.reset();
            }

            if (!result.success()) {
                FormValidator.setMessage(feedbackLabel, result.message(), true);
                return;
            }

            promptDoctorVerificationCode(firstName, lastName, email, password, speciality);
        });

        verificationTask.setOnFailed(event -> {
            restoreRegisterButton();
            recaptchaWidget.reset();
            Throwable error = verificationTask.getException();
            FormValidator.setMessage(
                    feedbackLabel,
                    error == null ? "Registration failed. Please try again." : error.getMessage(),
                    true
            );
        });

        Thread backgroundThread = new Thread(verificationTask, "doctor-register-verification");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    private void promptDoctorVerificationCode(String firstName, String lastName, String email, String password, String speciality) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Verify Email");
        dialog.setHeaderText("Enter the 6-digit code sent to " + email);
        dialog.setContentText("Verification code:");

        Optional<String> code = dialog.showAndWait();
        if (code.isEmpty()) {
            recaptchaWidget.reset();
            FormValidator.setMessage(feedbackLabel, "Email verification was cancelled. Request a new code to continue.", true);
            return;
        }

        registerButton.setDisable(true);
        registerButton.setText("Creating account...");

        Task<DoctorRegistrationResult> registrationTask = new Task<>() {
            @Override
            protected DoctorRegistrationResult call() {
                String verificationError = authService.verifyRegistrationCode(email, code.get());
                if (verificationError != null) {
                    return DoctorRegistrationResult.failed(verificationError, true);
                }

                boolean success = authService.registerDoctor(firstName, lastName, email, password, speciality);
                if (!success) {
                    return DoctorRegistrationResult.failed(
                            "Registration failed. Check the entered details and try again.",
                            true
                    );
                }

                return DoctorRegistrationResult.created();
            }
        };

        registrationTask.setOnSucceeded(event -> {
            restoreRegisterButton();
            recaptchaWidget.reset();

            DoctorRegistrationResult result = registrationTask.getValue();
            if (!result.success()) {
                FormValidator.setMessage(feedbackLabel, result.message(), true);
                return;
            }

            showAlert("Success", "Registration successful. Please sign in.", Alert.AlertType.INFORMATION);
            loadScene("/fxml/login.fxml", "PinkShield Login");
        });

        registrationTask.setOnFailed(event -> {
            restoreRegisterButton();
            recaptchaWidget.reset();
            Throwable error = registrationTask.getException();
            FormValidator.setMessage(
                    feedbackLabel,
                    error == null ? "Registration failed. Please try again." : error.getMessage(),
                    true
            );
        });

        Thread backgroundThread = new Thread(registrationTask, "doctor-register-create-account");
        backgroundThread.setDaemon(true);
        backgroundThread.start();
    }

    @FXML
    public void handleBackToRoleSelect() {
        loadScene("/fxml/register.fxml", "PinkShield Register");
    }

    @FXML
    public void handleBackToLogin() {
        loadScene("/fxml/login.fxml", "PinkShield Login");
    }

    private void clearFeedback() {
        FormValidator.clearStates(
                firstNameField,
                lastNameField,
                emailField,
                passwordField,
                confirmPasswordField
        );
        specialityCombo.setStyle("");
        FormValidator.setMessage(feedbackLabel, "", true);
    }

    private void showFieldError(TextField field, String message) {
        FormValidator.markInvalid(field);
        FormValidator.setMessage(feedbackLabel, message, true);
    }

    private void showFieldError(PasswordField field, String message) {
        FormValidator.markInvalid(field);
        FormValidator.setMessage(feedbackLabel, message, true);
    }

    private void loadScene(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Scene scene = AppNavigator.createScene(loader.load(), getClass());

            Stage stage = (Stage) registerButton.getScene().getWindow();
            AppNavigator.applyStage(stage, scene, title);
        } catch (IOException e) {
            showAlert("Error", "Failed to load " + title + ".", Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void restoreRegisterButton() {
        registerButton.setDisable(false);
        registerButton.setText("Register as Doctor");
    }

    private record DoctorRegistrationResult(boolean success, String message, boolean resetCaptcha) {
        private static DoctorRegistrationResult created() {
            return new DoctorRegistrationResult(true, "", true);
        }

        private static DoctorRegistrationResult codeSent() {
            return new DoctorRegistrationResult(true, "", false);
        }

        private static DoctorRegistrationResult failed(String message, boolean resetCaptcha) {
            return new DoctorRegistrationResult(false, message, resetCaptcha);
        }
    }
}
