import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.gov.dwp.uc.pairtest.DefaultConfigurationLoader;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultConfigurationLoaderTest {

    private final DefaultConfigurationLoader loader = new DefaultConfigurationLoader();

    private void createPropertiesFile(File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("adult.ticket.price=25\nchild.ticket.price=15\n");
        }
    }

    @Test
    void shouldLoadPropertiesSuccessfully(@TempDir Path tempDir) throws IOException {
        // Create a temporary properties file with valid keys
        File tempFile = tempDir.resolve("prices.properties").toFile();
        createPropertiesFile(tempFile);
        // Temporarily replace the file path using System properties
        System.setProperty("java.class.path", tempFile.getParent());

        Properties properties = loader.loadProperties();

        // Verify the loaded properties
        assertEquals("25", properties.getProperty("adult.ticket.price"));
        assertEquals("15", properties.getProperty("child.ticket.price"));
    }

    @Test
    void shouldThrowExceptionWhenAdultTicketPriceIsMissing() {
        InputStream input = new ByteArrayInputStream("child.ticket.price=15\n".getBytes(StandardCharsets.UTF_8));
        Exception exception = assertThrows(
                TicketServiceImpl.ConfigurationException.class,
                () -> loader.loadProperties(input)
        );

        assertEquals("Missing configuration key: adult.ticket.price", exception.getMessage());
    }

    @Test
    void shouldThrowConfigurationExceptionForMalformedProperties() {
        InputStream input = new ByteArrayInputStream("invalidFormat".getBytes(StandardCharsets.UTF_8));
        Exception exception = assertThrows(
                TicketServiceImpl.ConfigurationException.class,
                () -> loader.loadProperties(input)
        );

        assertTrue(exception.getMessage().contains("Missing configuration key")); // Adjust assertion to fit the implementation
    }

    @Test
    void shouldThrowExceptionWhenBothKeysAreMissing() {
        InputStream input = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        Exception exception = assertThrows(
                TicketServiceImpl.ConfigurationException.class,
                () -> loader.loadProperties(input)
        );

        assertEquals("Properties file is empty or could not be loaded.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenInputStreamIsNull() {
        Exception exception = assertThrows(
                TicketServiceImpl.ConfigurationException.class,
                () -> loader.loadProperties(null)
        );
        assertEquals("Input stream for configuration file is null.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenPropertiesFileIsEmpty() {
        InputStream input = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));
        Exception exception = assertThrows(
                TicketServiceImpl.ConfigurationException.class,
                () -> loader.loadProperties(input)
        );
        assertEquals("Properties file is empty or could not be loaded.", exception.getMessage());
    }
}