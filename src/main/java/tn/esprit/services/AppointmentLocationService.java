package tn.esprit.services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class AppointmentLocationService {
    private static final String CONFIG_FILE = "geoapify.properties";
    private static final double CLINIC_LATITUDE = 36.8444;
    private static final double CLINIC_LONGITUDE = 10.1985;
    private static final String CLINIC_NAME = "PinkShield Medical Center";
    private static final String CLINIC_ADDRESS = "Centre Urbain Nord, Tunis, Tunisia";

    public String getClinicName() {
        return CLINIC_NAME;
    }

    public String getClinicAddress() {
        return CLINIC_ADDRESS;
    }

    public double getClinicLatitude() {
        return CLINIC_LATITUDE;
    }

    public double getClinicLongitude() {
        return CLINIC_LONGITUDE;
    }

    public String getDirectionsUrl() {
        return String.format(
                "https://www.openstreetmap.org/?mlat=%.4f&mlon=%.4f#map=16/%.4f/%.4f",
                CLINIC_LATITUDE,
                CLINIC_LONGITUDE,
                CLINIC_LATITUDE,
                CLINIC_LONGITUDE
        );
    }

    public List<String> getStaticPreviewUrls() {
        List<String> urls = new ArrayList<>();

        urls.add(String.format(
                "https://staticmap.openstreetmap.de/staticmap.php?center=%.4f,%.4f&zoom=15&size=640x320&markers=%.4f,%.4f,red-pushpin",
                CLINIC_LATITUDE,
                CLINIC_LONGITUDE,
                CLINIC_LATITUDE,
                CLINIC_LONGITUDE
        ));

        String geoapifyKey = readConfigValue("GEOAPIFY_API_KEY", "geoapify.api.key");
        if (!geoapifyKey.isBlank()) {
            urls.add(String.format(
                    "https://maps.geoapify.com/v1/staticmap?style=osm-bright&width=640&height=320&center=lonlat:%.4f,%.4f&zoom=15&marker=lonlat:%.4f,%.4f;color:%%23c0396b;size:medium&apiKey=%s",
                    CLINIC_LONGITUDE,
                    CLINIC_LATITUDE,
                    CLINIC_LONGITUDE,
                    CLINIC_LATITUDE,
                    geoapifyKey
            ));
        }

        return urls;
    }

    private String readConfigValue(String envName, String propertyName) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        Properties fileProperties = loadConfigProperties();
        String fileValue = fileProperties.getProperty(propertyName);
        return fileValue == null ? "" : fileValue.trim();
    }

    private Properties loadConfigProperties() {
        Properties properties = new Properties();

        try (InputStream resourceStream = AppointmentLocationService.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (resourceStream != null) {
                properties.load(resourceStream);
            }
        } catch (IOException e) {
            System.err.println("Failed to read classpath " + CONFIG_FILE + ": " + e.getMessage());
        }

        Path externalPath = Path.of(System.getProperty("user.dir"), CONFIG_FILE);
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
}
