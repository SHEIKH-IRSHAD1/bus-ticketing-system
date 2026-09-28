package com.bustransit.ticketing.controller;

import com.bustransit.ticketing.dto.ValidationResponse;
import com.bustransit.ticketing.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidationController {

    private final TicketService ticketService;

    public ValidationController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * Called by the Conductor App after scanning a passenger's QR ticket.
     * On success this also marks the ticket USED - calling it twice for the
     * same ticket will succeed once and then report ALREADY_USED.
     */
    @GetMapping("/tickets/{token}/validate")
    public ValidationResponse validate(@PathVariable String token) {
        return ticketService.validateAndConsume(token);
    }
}
