package com.bustransit.ticketing.controller;

import com.bustransit.ticketing.dto.PurchaseRequest;
import com.bustransit.ticketing.dto.PurchaseResponse;
import com.bustransit.ticketing.entity.Ticket;
import com.bustransit.ticketing.service.StripeCheckoutService;
import com.bustransit.ticketing.service.TicketService;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tickets")
public class PurchaseController {

    private final TicketService ticketService;
    private final StripeCheckoutService stripeCheckoutService;

    public PurchaseController(TicketService ticketService, StripeCheckoutService stripeCheckoutService) {
        this.ticketService = ticketService;
        this.stripeCheckoutService = stripeCheckoutService;
    }

    /**
     * Called by the Passenger PWA after it scans the bus's QR code.
     * Creates a PENDING_PAYMENT ticket row, then a Stripe Checkout Session,
     * and returns the hosted checkout URL for the browser to redirect to.
     */
    @PostMapping("/purchase")
    public ResponseEntity<?> purchase(@Valid @RequestBody PurchaseRequest request) {
        Ticket ticket = ticketService.createPendingTicket(request.passengerId(), request.routeId());

        try {
            Session session = stripeCheckoutService.createCheckoutSession(ticket.getId(), request.routeId());
            return ResponseEntity.ok(new PurchaseResponse(ticket.getId(), session.getUrl()));
        } catch (StripeException e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to create checkout session: " + e.getMessage());
        }
    }
}
