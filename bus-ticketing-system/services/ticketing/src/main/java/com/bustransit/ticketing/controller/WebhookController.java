package com.bustransit.ticketing.controller;

import com.bustransit.ticketing.service.PaymentService;
import com.bustransit.ticketing.service.StripeCheckoutService;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final StripeCheckoutService stripeCheckoutService;
    private final PaymentService paymentService;

    public WebhookController(StripeCheckoutService stripeCheckoutService, PaymentService paymentService) {
        this.stripeCheckoutService = stripeCheckoutService;
        this.paymentService = paymentService;
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = stripeCheckoutService.verifyAndParseEvent(payload, sigHeader);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected webhook with invalid signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        if ("checkout.session.completed".equals(event.getType())) {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject stripeObject;
            try {
                // getObject() is empty when the event's API version differs from the
                // one this SDK was built against. Falling back to deserializeUnsafe()
                // is Stripe's documented workaround - without it, a *paid* ticket
                // would silently never activate.
                stripeObject = deserializer.getObject().isPresent()
                        ? deserializer.getObject().get()
                        : deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                log.error("Could not deserialize checkout.session.completed event: {}", e.getMessage());
                // 500 (not 200) so the failure is visible in the Stripe dashboard and retried.
                return ResponseEntity.internalServerError().body("Deserialization failed");
            }

            Session session = (Session) stripeObject;
            Long ticketId = Long.valueOf(session.getMetadata().get("ticketId"));
            long amountCents = session.getAmountTotal() != null ? session.getAmountTotal() : 0L;
            String currency = session.getCurrency() != null ? session.getCurrency() : "usd";
            paymentService.handleCheckoutCompleted(ticketId, session.getId(), amountCents, currency);
        } else {
            log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }

        // Always return 200 quickly for handled/ignored events - a non-2xx
        // response is what makes Stripe retry, so only fail this loudly for
        // genuine verification failures (handled above).
        return ResponseEntity.ok("received");
    }
}
