import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.ConfigurationLoader;
import uk.gov.dwp.uc.pairtest.DefaultConfigurationLoader;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl;
import uk.gov.dwp.uc.pairtest.TicketServiceImpl.ConfigurationException;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TicketServiceImplTest {
    public static final String ERROR_MESSAGE_CHILD_INFANT_WITHOUT_ADULT = "Child and Infant tickets require an accompanying Adult ticket purchase.";
    @Mock
    private TicketPaymentService ticketPaymentService;
    @Mock
    private SeatReservationService seatReservationService;
    @Mock
    private DefaultConfigurationLoader configLoader;
    private TicketServiceImpl ticketService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Mock configLoader
        configLoader = Mockito.mock(DefaultConfigurationLoader.class);
        // Stub the loadProperties() method to return a valid Properties object
        Properties mockProperties = new Properties();
        mockProperties.setProperty("adult.ticket.price", "25");
        mockProperties.setProperty("child.ticket.price", "15");
        when(configLoader.loadProperties()).thenReturn(mockProperties);
        // Initialize the service with mock services and the mocked configLoader
        ticketService = new TicketServiceImpl(ticketPaymentService, seatReservationService, configLoader);
    }

    @Test
    public void shouldReturnValidProperties() {
        Properties properties = configLoader.loadProperties();
        assertNotNull(properties);
        assertEquals("25", properties.getProperty("adult.ticket.price"));
        assertEquals("15", properties.getProperty("child.ticket.price"));
    }

    @Test
    public void testConfigurationLoader() {
        ConfigurationLoader loader = new DefaultConfigurationLoader();
        Properties properties = loader.loadProperties();
        assertNotNull(properties);
    }

    @Test
    public void shouldProcessValidAdultOnlyTicketPurchase() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        ticketService.purchaseTickets(1L, adultRequest);
        verify(ticketPaymentService).makePayment(1L, 50); // 2 * 25
        verify(seatReservationService).reserveSeat(1L, 2); // 2 seats for 2 adults
    }

    @Test
    public void shouldProcessValidMixedTicketPurchase() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2);
        TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);
        ticketService.purchaseTickets(1L, adultRequest, childRequest, infantRequest);

        verify(ticketPaymentService).makePayment(1L, 65); // (2 * 25) + (1 * 15)
        verify(seatReservationService).reserveSeat(1L, 3); // 2 adults + 1 child (Infant doesn't need a seat)
    }

    @Test
    public void shouldRejectChildOnlyPurchase() {
        TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 2);
        InvalidPurchaseException invalidPurchaseException = assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(1L, childRequest));
        assertEquals(ERROR_MESSAGE_CHILD_INFANT_WITHOUT_ADULT, invalidPurchaseException.getMessage());
    }

    @Test
    public void shouldRejectInfantOnlyPurchase() {
        TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);
        InvalidPurchaseException invalidPurchaseException = assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(1L, infantRequest));
        assertEquals(ERROR_MESSAGE_CHILD_INFANT_WITHOUT_ADULT, invalidPurchaseException.getMessage());
    }

    @Test
    public void shouldRejectChildAndInfantWithoutAdult() {
        TicketTypeRequest childRequest = new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1);
        TicketTypeRequest infantRequest = new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1);
        InvalidPurchaseException invalidPurchaseException = assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(1L, childRequest, infantRequest));
        assertEquals(ERROR_MESSAGE_CHILD_INFANT_WITHOUT_ADULT, invalidPurchaseException.getMessage());
    }

    @Test
    public void shouldRejectMoreThan25Tickets() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 26);
        InvalidPurchaseException invalidPurchaseException = assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(1L, adultRequest));
        assertEquals("Invalid ticket purchase: You must buy at least 1 ticket, and a maximum of 25 tickets can be purchased at a time.", invalidPurchaseException.getMessage());
    }

    @Test
    public void shouldRejectInvalidAccountId() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
        InvalidPurchaseException invalidPurchaseException = assertThrows(InvalidPurchaseException.class, () -> ticketService.purchaseTickets(0L, adultRequest));
        assertEquals("Invalid account ID", invalidPurchaseException.getMessage());
    }

    @Test
    public void shouldCreateConfigurationExceptionWithMessage() {
        TicketServiceImpl.ConfigurationException exception = new TicketServiceImpl.ConfigurationException("Test message");
        assertEquals("Test message", exception.getMessage());
    }

    @Test
    public void shouldCreateConfigurationExceptionWithMessageAndCause() {
        Throwable cause = new IOException("Root cause");
        TicketServiceImpl.ConfigurationException exception = new ConfigurationException("Test message", cause);
        assertEquals("Test message", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    public void shouldThrowExceptionForNullTicketTypeRequest() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 1);
        TicketTypeRequest nullRequest = null;

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1L, adultRequest, nullRequest);
        });
        assertEquals("Null ticket request encountered", exception.getMessage());
    }

    @Test
    public void shouldProcessMaximumAllowedTickets() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 25);
        ticketService.purchaseTickets(1L, adultRequest);

        verify(ticketPaymentService).makePayment(1L, 625); // 25 * 25
        verify(seatReservationService).reserveSeat(1L, 25);
    }

    @Test
    public void shouldRejectZeroTickets() {
        TicketTypeRequest adultRequest = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 0);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1L, adultRequest);
        });
        assertEquals("Invalid ticket count: Ticket count must be a positive number.", exception.getMessage());
    }

    @Test
    public void shouldRejectNegativeTicketCount() {
        TicketTypeRequest negativeTickets = new TicketTypeRequest(TicketTypeRequest.Type.ADULT, -1);

        InvalidPurchaseException exception = assertThrows(InvalidPurchaseException.class, () -> {
            ticketService.purchaseTickets(1L, negativeTickets);
        });
        assertEquals("Invalid ticket count: Ticket count must be a positive number.", exception.getMessage());
    }

    @Test
    void shouldLogWarningForUnexpectedTicketType() {
        // Create a TicketTypeRequest with a null ticket type
        TicketTypeRequest invalidRequest = new TicketTypeRequest(null, 2);
        // Assert that InvalidPurchaseException is thrown
        Exception exception = assertThrows(
                InvalidPurchaseException.class, () -> ticketService.purchaseTickets(12345L, invalidRequest));
        // Verify the exception message
        assertEquals("Unexpected ticket type encountered: null", exception.getMessage());
    }

    @Test
    void shouldThrowConfigurationExceptionForInvalidTicketPrices() {
        // Mock the payment and reservation services
        TicketPaymentService mockPaymentService = Mockito.mock(TicketPaymentService.class);
        SeatReservationService mockReservationService = Mockito.mock(SeatReservationService.class);

        // Mock the DefaultConfigurationLoader to return invalid ticket prices
        DefaultConfigurationLoader mockLoader = Mockito.mock(DefaultConfigurationLoader.class);
        Properties mockProperties = new Properties();
        mockProperties.setProperty("adult.ticket.price", "-10"); // Invalid negative price
        mockProperties.setProperty("child.ticket.price", "0");  // Invalid zero price
        when(mockLoader.loadProperties()).thenReturn(mockProperties);
        // Assert that the ConfigurationException is thrown
        assertThrows(TicketServiceImpl.ConfigurationException.class, () -> new TicketServiceImpl(mockPaymentService, mockReservationService, mockLoader));
    }

    @Test
    void shouldThrowExceptionWhenTicketRequestsAreNull() {
        // Assert that the exception is thrown when ticketTypeRequests is null
        Exception exception = assertThrows(
                InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(12345L, (TicketTypeRequest[]) null));
        assertEquals("No ticket requests provided", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenTicketRequestsAreEmpty() {
        // Assert that the exception is thrown when ticketTypeRequests is an empty array
        Exception exception = assertThrows(
                InvalidPurchaseException.class,
                () -> ticketService.purchaseTickets(12345L, new TicketTypeRequest[0]));
        assertEquals("No ticket requests provided", exception.getMessage());
    }
}