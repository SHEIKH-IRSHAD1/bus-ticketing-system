package com.bustransit.ticketing.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StripeCheckoutService {

    @Value("${stripe.success-url-base}")
    private String successUrlBase;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Value("${ticket.price-amount-cents}")
    private long priceAmountCents;

    @Value("${ticket.price-currency}")
    private String priceCurrency;

    /**
     * Creates a Stripe-hosted Checkout Session in whatever mode the configured
     * API key is (test or live). The ticketId is stashed in metadata so the
     * webhook handler can map the payment event back to our ticket.
     */
    public Session createCheckoutSession(Long ticketId, Long routeId) throws StripeException {
        // We know our own ticketId before creating the session, so we put it
        // straight into the redirect URL. (Stripe's {CHECKOUT_SESSION_ID}
        // placeholder is a different ID and would not identify our ticket.)
        String separator = successUrlBase.contains("?") ? "&" : "?";
        String successUrl = successUrlBase + separator + "ticketId=" + ticketId;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .putMetadata("ticketId", String.valueOf(ticketId))
                .putMetadata("routeId", String.valueOf(routeId))
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(priceCurrency)
                                                .setUnitAmount(priceAmountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Bus Ticket - Route " + routeId)
                                                                .build())
                                                .build())
                                .build())
                .build();

        return Session.create(params);
    }

    /**
     * Verifies the request genuinely came from Stripe using the signing
     * secret from the dashboard, then parses it into an Event. Never skip
     * this check - without it, anyone who finds your webhook URL could POST
     * fake "payment succeeded" events.
     */
    public Event verifyAndParseEvent(String payload, String sigHeader) throws SignatureVerificationException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}
