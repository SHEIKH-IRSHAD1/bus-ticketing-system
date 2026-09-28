package com.bustransit.ticketing.service;

import com.bustransit.ticketing.dto.ValidationResponse;
import com.bustransit.ticketing.entity.Ticket;
import com.bustransit.ticketing.entity.TicketStatus;
import com.bustransit.ticketing.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;

@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final TicketRepository ticketRepository;
    private final TokenService tokenService;

    @Value("${ticket.expiry-minutes}")
    private long expiryMinutes;

    public TicketService(TicketRepository ticketRepository, TokenService tokenService) {
        this.ticketRepository = ticketRepository;
        this.tokenService = tokenService;
    }

    public Ticket createPendingTicket(String passengerId, Long routeId) {
        Ticket ticket = new Ticket(passengerId, routeId);
        return ticketRepository.save(ticket);
    }

    /**
     * Called once a payment has been confirmed (via the Stripe webhook).
     * Generates the signed QR token and flips the ticket to ACTIVE.
     */
    @Transactional
    public Ticket activateTicketAfterPayment(Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));

        if (ticket.getStatus() != TicketStatus.PENDING_PAYMENT) {
            log.info("Ticket {} already in status {}, skipping re-activation.", ticketId, ticket.getStatus());
            return ticket;
        }

        Instant expiry = Instant.now().plusSeconds(expiryMinutes * 60);
        String token = tokenService.generate(ticket.getId(), expiry);

        ticket.setQrToken(token);
        ticket.setStatus(TicketStatus.ACTIVE);
        ticket.setExpiresAt(OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC));
        return ticketRepository.save(ticket);
    }

    /**
     * Called by the Conductor App to validate + consume a ticket.
     *
     * @Transactional matters here: if two conductors scan the same ticket at
     * nearly the same instant, the database transaction ensures only one of
     * these calls can see status=ACTIVE and win the race to mark it USED.
     */
    @Transactional
    public ValidationResponse validateAndConsume(String token) {
        TokenService.TokenVerificationResult verification = tokenService.verify(token);
        if (!verification.valid()) {
            return ValidationResponse.rejected(verification.reason());
        }

        Ticket ticket = ticketRepository.findById(verification.ticketId()).orElse(null);
        if (ticket == null) {
            return ValidationResponse.rejected("TICKET_NOT_FOUND");
        }

        if (ticket.getStatus() == TicketStatus.USED) {
            return ValidationResponse.rejected("ALREADY_USED");
        }
        if (ticket.getStatus() == TicketStatus.EXPIRED) {
            return ValidationResponse.rejected("EXPIRED");
        }
        if (ticket.getStatus() != TicketStatus.ACTIVE) {
            return ValidationResponse.rejected("NOT_ACTIVE");
        }

        ticket.setStatus(TicketStatus.USED);
        ticket.setUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        ticketRepository.save(ticket);

        return ValidationResponse.ok(ticket.getId());
    }

    public Ticket getTicket(Long ticketId) {
        return ticketRepository.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("Ticket not found: " + ticketId));
    }
}
