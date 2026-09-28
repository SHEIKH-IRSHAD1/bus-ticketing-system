package com.bustransit.ticketing.service;

import com.bustransit.ticketing.dto.ValidationResponse;
import com.bustransit.ticketing.entity.Ticket;
import com.bustransit.ticketing.entity.TicketStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs against a real (in-memory H2) database via the "test" profile rather
 * than mocking the repository, since the behavior under test - a ticket can
 * only ever be validated once - is exactly the kind of thing a mock could
 * accidentally "pass" without actually proving anything.
 */
@SpringBootTest
@ActiveProfiles("test")
class TicketServiceTest {

    @Autowired
    private TicketService ticketService;

    @Test
    @Transactional
    void ticketCanOnlyBeValidatedOnce() {
        Ticket pending = ticketService.createPendingTicket("passenger-1", 1L);
        Ticket active = ticketService.activateTicketAfterPayment(pending.getId());

        assertEquals(TicketStatus.ACTIVE, active.getStatus());
        assertNotNull(active.getQrToken());

        ValidationResponse first = ticketService.validateAndConsume(active.getQrToken());
        assertTrue(first.valid());
        assertEquals(active.getId(), first.ticketId());

        ValidationResponse second = ticketService.validateAndConsume(active.getQrToken());
        assertFalse(second.valid());
        assertEquals("ALREADY_USED", second.reason());
    }

    @Test
    @Transactional
    void activatingAlreadyActiveTicketIsIdempotent() {
        Ticket pending = ticketService.createPendingTicket("passenger-2", 1L);
        Ticket firstActivation = ticketService.activateTicketAfterPayment(pending.getId());
        String firstToken = firstActivation.getQrToken();

        // Simulates a retried Stripe webhook calling activation a second time.
        Ticket secondActivation = ticketService.activateTicketAfterPayment(pending.getId());

        assertEquals(firstToken, secondActivation.getQrToken());
        assertEquals(TicketStatus.ACTIVE, secondActivation.getStatus());
    }
}
