package tn.esprit.services;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class EmailService {
    private static final String MAIL_PROPERTIES_FILE = "mail.properties";

    public String sendPasswordResetCode(String recipientEmail, String verificationCode) {
        return sendVerificationCode(
                recipientEmail,
                "PinkShield password reset verification code",
                buildPasswordResetBody(verificationCode)
        );
    }

    public String sendRegistrationVerificationCode(String recipientEmail, String verificationCode) {
        return sendVerificationCode(
                recipientEmail,
                "PinkShield account verification code",
                buildRegistrationBody(verificationCode)
        );
    }

    private String sendVerificationCode(String recipientEmail, String subject, String body) {
        SmtpConfig config = loadConfig();
        if (!config.isConfigured()) {
            return "Email sending is not configured. Add SMTP settings in mail.properties or define SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, and SMTP_FROM.";
        }

        Properties properties = new Properties();
        properties.put("mail.smtp.host", config.host());
        properties.put("mail.smtp.port", config.port());
        properties.put("mail.smtp.auth", Boolean.toString(config.auth()));
        properties.put("mail.smtp.starttls.enable", Boolean.toString(config.startTls()));
        properties.put("mail.smtp.starttls.required", Boolean.toString(config.startTls()));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(config.ssl()));
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        properties.put("mail.smtp.ssl.trust", config.host());
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        Session session;
        if (config.auth()) {
            session = Session.getInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.user(), config.password());
                }
            });
        } else {
            session = Session.getInstance(properties);
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(config.from()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            return null;
        } catch (MessagingException e) {
            return "Failed to send verification code email: " + rootMessage(e);
        }
    }

    private String buildPasswordResetBody(String verificationCode) {
        return """
                PinkShield Password Reset

                Your verification code is: %s

                This code expires in 10 minutes.
                If you did not request a password reset, you can ignore this email.
                """.formatted(verificationCode);
    }

    private String buildRegistrationBody(String verificationCode) {
        return """
                PinkShield Account Verification

                Your registration verification code is: %s

                This code expires in 10 minutes.
                If you did not try to create a PinkShield account, you can ignore this email.
                """.formatted(verificationCode);
    }

    private SmtpConfig loadConfig() {
        Properties fileProperties = loadMailProperties();

        String host = readSetting("SMTP_HOST", "", fileProperties);
        String port = readSetting("SMTP_PORT", "587", fileProperties);
        String user = readSetting("SMTP_USER", "", fileProperties);
        String password = readSetting("SMTP_PASSWORD", "", fileProperties);
        String from = readSetting("SMTP_FROM", user, fileProperties);
        boolean auth = readBooleanSetting("SMTP_AUTH", true, fileProperties);
        boolean startTls = readBooleanSetting("SMTP_STARTTLS", true, fileProperties);
        boolean ssl = readBooleanSetting("SMTP_SSL", false, fileProperties);

        return new SmtpConfig(host, port, user, password, from, auth, startTls, ssl);
    }

    private Properties loadMailProperties() {
        Properties properties = new Properties();

        try (InputStream resourceStream = EmailService.class.getClassLoader().getResourceAsStream(MAIL_PROPERTIES_FILE)) {
            if (resourceStream != null) {
                properties.load(resourceStream);
            }
        } catch (IOException e) {
            System.err.println("Failed to read classpath " + MAIL_PROPERTIES_FILE + ": " + e.getMessage());
        }

        Path externalPath = Path.of(System.getProperty("user.dir"), MAIL_PROPERTIES_FILE);
        if (!Files.isRegularFile(externalPath)) {
            return properties;
        }

        try (InputStream fileStream = Files.newInputStream(externalPath)) {
            properties.load(fileStream);
        } catch (IOException e) {
            System.err.println("Failed to read " + externalPath + ": " + e.getMessage());
        }

        return properties;
    }

    private static String readSetting(String key, String defaultValue, Properties fileProperties) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        String fileValue = fileProperties.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }

        return defaultValue;
    }

    private static boolean readBooleanSetting(String key, boolean defaultValue, Properties fileProperties) {
        String rawValue = readSetting(key, Boolean.toString(defaultValue), fileProperties);
        return "true".equalsIgnoreCase(rawValue) || "1".equals(rawValue) || "yes".equalsIgnoreCase(rawValue);
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

    private record SmtpConfig(
            String host,
            String port,
            String user,
            String password,
            String from,
            boolean auth,
            boolean startTls,
            boolean ssl
    ) {
        private boolean isConfigured() {
            if (host.isBlank() || port.isBlank() || from.isBlank()) {
                return false;
            }

            if (auth && (user.isBlank() || password.isBlank())) {
                return false;
            }

            return true;
        }
    }
}
