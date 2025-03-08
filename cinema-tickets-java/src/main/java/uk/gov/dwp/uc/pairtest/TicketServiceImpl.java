package uk.gov.dwp.uc.pairtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import java.util.Objects;
import java.util.Properties;

public class TicketServiceImpl implements TicketService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TicketServiceImpl.class);
    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;
    private static final int MAX_TICKETS = 25;
    private static final String ERR_NO_TICKETS = "No ticket requests provided";
    private static final String ERR_INVALID_ACCOUNT = "Invalid account ID";
    private final int adultTicketPrice;
    private final int childTicketPrice;
    public TicketServiceImpl(TicketPaymentService paymentService, SeatReservationService reservationService, DefaultConfigurationLoader configLoader) {
        this.paymentService = Objects.requireNonNull(paymentService, "PaymentService must not be null");
        this.reservationService = Objects.requireNonNull(reservationService, "ReservationService must not be null");

        // Use the configLoader as a local variable
        Objects.requireNonNull(configLoader, "ConfigurationLoader must not be null");
        Properties properties = configLoader.loadProperties();
        this.adultTicketPrice = Integer.parseInt(properties.getProperty("adult.ticket.price", "25"));
        this.childTicketPrice = Integer.parseInt(properties.getProperty("child.ticket.price", "15"));

        if (adultTicketPrice <= 0 || childTicketPrice <= 0) {
            throw new ConfigurationException("Ticket prices must be positive numbers.");
        }
    }
    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new InvalidPurchaseException(ERR_INVALID_ACCOUNT);
        }
    }
    // Validate that at least one adult ticket is purchased when child or infant tickets are present
    private void validateTicketCounts(int totalAdultTickets, int totalChildTickets, int totalInfantTickets) {
        if (totalAdultTickets == 0 && (totalChildTickets > 0 || totalInfantTickets > 0)) {
            LOGGER.warn("Invalid ticket configuration: {} child and {} infant tickets without an adult ticket", totalChildTickets, totalInfantTickets);
            throw new InvalidPurchaseException("Child and Infant tickets require an accompanying Adult ticket purchase.");
        }
    }
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        LOGGER.debug("Processing ticket requests: {}", (Object[]) ticketTypeRequests);
        int totalAdultTickets = 0;
        int totalChildTickets = 0;
        int totalInfantTickets = 0;

        validateAccountId(accountId);

        if (ticketTypeRequests == null || ticketTypeRequests.length == 0) {
            throw new InvalidPurchaseException(ERR_NO_TICKETS);
        }

        // Iterate over ticket requests
        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                throw new InvalidPurchaseException("Null ticket request encountered");
            }
            if (request.getTicketType() == null) {
                LOGGER.warn("Unexpected ticket type encountered: null");
                throw new InvalidPurchaseException("Unexpected ticket type encountered: null");
            }
            // Validate that no ticket count is negative or zero
            if (request.getNoOfTickets() <= 0) {
                throw new InvalidPurchaseException("Invalid ticket count: Ticket count must be a positive number.");
            }
            // Using enhanced switch for readability
            switch (request.getTicketType()) {
                case ADULT -> totalAdultTickets += request.getNoOfTickets();
                case CHILD -> totalChildTickets += request.getNoOfTickets();
                case INFANT -> totalInfantTickets += request.getNoOfTickets();
                default -> LOGGER.warn("Unexpected ticket type encountered: {}", request.getTicketType());
            }
        }

        int totalTickets = totalAdultTickets + totalChildTickets + totalInfantTickets;

        if (totalTickets == 0 || totalTickets > MAX_TICKETS) {
            throw new InvalidPurchaseException("Invalid ticket purchase: You must buy at least 1 ticket, and a maximum of 25 tickets can be purchased at a time.");
        }
        // Validate the total ticket counts against the business rules
        validateTicketCounts(totalAdultTickets, totalChildTickets, totalInfantTickets);

        // Calculate the total payment amount based on ticket types and prices
        int totalAmountToPay = (totalAdultTickets * adultTicketPrice) + (totalChildTickets * childTicketPrice);

        // Make payment request
        paymentService.makePayment(accountId, totalAmountToPay);

        // Reserve seats (excluding infants)
        int totalSeatsToReserve = totalAdultTickets + totalChildTickets;
        // Reserve seats for all but infants
        reservationService.reserveSeat(accountId, totalSeatsToReserve);

        LOGGER.info("Tickets successfully purchased: {} adults, {} children, {} infants for account ID: {}. Total amount to pay: {}", totalAdultTickets, totalChildTickets, totalInfantTickets, accountId, totalAmountToPay);

    }
    // Custom exception for configuration errors
    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConfigurationException(String message) {
            super(message);
        }
    }
}