package uk.gov.dwp.uc.pairtest;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultConfigurationLoader implements ConfigurationLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultConfigurationLoader.class);

    @Override
    public Properties loadProperties() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("prices.properties")) {
            if (input == null) {
                throw new TicketServiceImpl.ConfigurationException("Configuration file not found in classpath: prices.properties");
            }
            return loadPropertiesFromStream(input);
        } catch (IOException e) {
            LOGGER.error("IOException occurred while loading properties: {}", e.getMessage(), e);
            throw new TicketServiceImpl.ConfigurationException("Failed to load ticket prices from configuration", e);
        }
    }

    public void loadProperties(InputStream input) {
        if (input == null) {
            throw new TicketServiceImpl.ConfigurationException("Input stream for configuration file is null.");
        }
        try {
            loadPropertiesFromStream(input);
        } catch (IOException e) {
            throw new TicketServiceImpl.ConfigurationException("Failed to load ticket prices from input stream", e);
        }
    }

    private Properties loadPropertiesFromStream(InputStream input) throws IOException {
        Properties properties = new Properties();
        try {
            properties.load(input);
        } catch (IOException e) {
            throw new IOException("Malformed properties file", e); // Adjusted to propagate IOException
        }

        if (properties.isEmpty()) {
            throw new TicketServiceImpl.ConfigurationException("Properties file is empty or could not be loaded.");
        }

        // Validate required properties
        validateProperty(properties, "adult.ticket.price");
        validateProperty(properties, "child.ticket.price");

        return properties;
    }

    private void validateProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isEmpty()) {
            LOGGER.error("Configuration error: Missing key '{}' in properties file", key);
            throw new TicketServiceImpl.ConfigurationException("Missing configuration key: " + key);
        }
    }
}
