package tn.esprit.utils;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * FormValidator — Port of form-validation.js to JavaFX
 * Provides AJAX-like validation for form fields with error display
 */
public class FormValidator {

    private static final Pattern EMAIL_PATTERN = 
        Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    
    private static final Pattern PASSWORD_MIN_LENGTH = 
        Pattern.compile(".{8,}");
    
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("^\\d{8,20}$");

    // ============ STATIC UTILITY METHODS ============
    
    /**
     * Static method: Validates email format
     */
    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean hasValidMailDomain(String email) {
        if (!isValidEmail(email)) {
            return false;
        }

        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
        if (domain.isBlank() || !domain.contains(".") || domain.startsWith(".") || domain.endsWith(".")) {
            return false;
        }

        Hashtable<String, String> environment = new Hashtable<>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");

        try {
            DirContext context = new InitialDirContext(environment);
            Attributes mxRecords = context.getAttributes(domain, new String[]{"MX"});
            Attribute mx = mxRecords.get("MX");
            if (mx != null && mx.size() > 0) {
                return true;
            }

            Attributes addressRecords = context.getAttributes(domain, new String[]{"A", "AAAA"});
            Attribute a = addressRecords.get("A");
            Attribute aaaa = addressRecords.get("AAAA");
            return (a != null && a.size() > 0) || (aaaa != null && aaaa.size() > 0);
        } catch (NamingException e) {
            return false;
        }
    }

    /**
     * Static method: Validates phone format (8-20 digits)
     */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.replaceAll("\\D", "")).matches();
    }

    /**
     * Mark a field as invalid with error styling
     */
    public static void markInvalid(Control field) {
        if (field instanceof TextInputControl) {
            ((TextInputControl) field).setStyle("-fx-border-color: #ff4444; -fx-border-width: 2;");
        }
    }

    /**
     * Clear field state
     */
    public static void clearFieldState(Control field) {
        if (field instanceof TextInputControl) {
            ((TextInputControl) field).setStyle("");
        }
    }

    /**
     * Clear states of multiple fields
     */
    public static void clearStates(Control... fields) {
        for (Control field : fields) {
            clearFieldState(field);
        }
    }

    /**
     * Set message on a label with color (error or success)
     */
    public static void setMessage(Label label, String message, boolean isError) {
        if (label == null) return;
        
        label.setText(message);
        if (isError) {
            label.setStyle("-fx-text-fill: #ff6b6b; -fx-padding: 10;");
        } else {
            label.setStyle("-fx-text-fill: #4ade80; -fx-padding: 10;");
        }
        label.setVisible(!message.isEmpty());
        label.setManaged(!message.isEmpty());
    }

    /**
     * Attach listeners to clear message when field receives input
     */
    public static void attachClearOnInput(Label feedbackLabel, Control... fields) {
        for (Control field : fields) {
            if (field instanceof TextInputControl) {
                ((TextInputControl) field).textProperty().addListener((obs, oldVal, newVal) -> {
                    setMessage(feedbackLabel, "", true);
                });
            }
        }
    }

    // ============ INSTANCE METHODS ============

    /**
     * Validates email format
     */
    public boolean validateEmail(TextField emailField) {
        String email = emailField.getText().trim();
        
        if (email.isEmpty()) {
            showError(emailField, "Email is required");
            return false;
        }
        
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showError(emailField, "Invalid email format");
            return false;
        }
        
        clearError(emailField);
        return true;
    }

    /**
     * Validates password strength
     */
    public boolean validatePassword(PasswordField passwordField) {
        String password = passwordField.getText();
        
        if (password.isEmpty()) {
            showError(passwordField, "Password is required");
            return false;
        }
        
        if (!PASSWORD_MIN_LENGTH.matcher(password).matches()) {
            showError(passwordField, "Password must be at least 8 characters");
            return false;
        }
        
        clearError(passwordField);
        return true;
    }

    /**
     * Validates text field is not empty
     */
    public boolean validateRequired(TextField field, String fieldName) {
        String value = field.getText().trim();
        
        if (value.isEmpty()) {
            showError(field, fieldName + " is required");
            return false;
        }
        
        clearError(field);
        return true;
    }

    /**
     * Validates combo box has selection
     */
    public boolean validateRequired(ComboBox<?> field, String fieldName) {
        if (field.getValue() == null) {
            showErrorCombo(field, fieldName + " is required");
            return false;
        }
        
        clearErrorCombo(field);
        return true;
    }

    /**
     * Shows error state and message for text input
     */
    public void showError(TextInputControl field, String message) {
        field.setStyle("-fx-border-color: #ff4444; -fx-border-width: 2; -fx-padding: 10;");
        field.getStyleClass().add("is-invalid");
        field.getStyleClass().remove("is-valid");
        
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setText("⚠ " + message);
        errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 11;");
        errorLabel.setVisible(true);
    }

    /**
     * Shows error state for ComboBox
     */
    public void showErrorCombo(ComboBox<?> field, String message) {
        field.setStyle("-fx-border-color: #ff4444; -fx-border-width: 2;");
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setText("⚠ " + message);
        errorLabel.setVisible(true);
    }

    /**
     * Clears error state for text input
     */
    public void clearError(TextInputControl field) {
        field.setStyle("");
        field.getStyleClass().remove("is-invalid");
        field.getStyleClass().add("is-valid");
        
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setVisible(false);
    }

    /**
     * Clears error state for ComboBox
     */
    public void clearErrorCombo(ComboBox<?> field) {
        field.setStyle("");
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setVisible(false);
    }

    /**
     * Resets field to initial state
     */
    public void resetField(TextInputControl field) {
        field.setStyle("");
        field.getStyleClass().removeAll("is-invalid", "is-valid");
        
        Label errorLabel = getOrCreateErrorLabel(field);
        errorLabel.setVisible(false);
    }

    /**
     * Gets or creates an error label below the field
     */
    private Label getOrCreateErrorLabel(Control field) {
        Label errorLabel = (Label) field.getUserData();
        
        if (errorLabel == null) {
            errorLabel = new Label();
            errorLabel.setWrapText(true);
            errorLabel.setStyle("-fx-text-fill: #ff6b6b; -fx-font-size: 11; -fx-padding: 4 0 0 0;");
            field.setUserData(errorLabel);
            
            if (field.getParent() instanceof VBox) {
                VBox parent = (VBox) field.getParent();
                parent.getChildren().add(errorLabel);
            }
        }
        
        return errorLabel;
    }

    /**
     * Validates entire form
     */
    public boolean validateForm(Control... fields) {
        boolean allValid = true;
        
        for (Control field : fields) {
            if (field instanceof TextField) {
                TextField tf = (TextField) field;
                if (tf.getText().trim().isEmpty()) {
                    showError(tf, "This field is required");
                    allValid = false;
                }
            } else if (field instanceof PasswordField) {
                PasswordField pf = (PasswordField) field;
                if (!validatePassword(pf)) {
                    allValid = false;
                }
            } else if (field instanceof ComboBox) {
                ComboBox<?> cb = (ComboBox<?>) field;
                if (cb.getValue() == null) {
                    showErrorCombo(cb, "Selection required");
                    allValid = false;
                }
            }
        }
        
        return allValid;
    }

    /**
     * Attaches blur listeners to a TextField for real-time validation
     */
    public void attachEmailValidation(TextField emailField) {
        emailField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && !emailField.getText().isEmpty()) {
                validateEmail(emailField);
            }
        });
    }

    /**
     * Attaches blur listeners to a PasswordField for real-time validation
     */
    public void attachPasswordValidation(PasswordField passwordField) {
        passwordField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused && !passwordField.getText().isEmpty()) {
                validatePassword(passwordField);
            }
        });
    }

    /**
     * Show success state
     */
    public void showSuccess(TextInputControl field) {
        field.setStyle("-fx-border-color: #22c55e; -fx-border-width: 2;");
        field.getStyleClass().add("is-valid");
        field.getStyleClass().remove("is-invalid");
    }
}
