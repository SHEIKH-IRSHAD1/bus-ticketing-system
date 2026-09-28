package com.bustransit.ticketing.controller;

import com.bustransit.ticketing.dto.TicketStatusResponse;
import com.bustransit.ticketing.entity.Ticket;
import com.bustransit.ticketing.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketStatusController {

    private final TicketService ticketService;

    public TicketStatusController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Polled by the Passenger PWA after returning from Stripe checkout, until
     * status flips from PENDING_PAYMENT to ACTIVE (which happens once the
     * webhook has been processed - usually within a second or two).
     */
    @GetMapping("/tickets/{ticketId}/status")
    public TicketStatusResponse status(@PathVariable Long ticketId) {
        Ticket ticket = ticketService.getTicket(ticketId);
        String qrToken = ticket.getStatus().name().equals("ACTIVE") ? ticket.getQrToken() : null;
        return new TicketStatusResponse(ticket.getId(), ticket.getStatus().name(), qrToken);
    }
}
