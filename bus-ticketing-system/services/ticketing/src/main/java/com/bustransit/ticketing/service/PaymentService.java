package com.bustransit.ticketing.service;

import com.bustransit.ticketing.entity.Payment;
import com.bustransit.ticketing.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final TicketService ticketService;

    public PaymentService(PaymentRepository paymentRepository, TicketService ticketService) {
        this.paymentRepository = paymentRepository;
        this.ticketService = ticketService;
    }

    /**
     * Stripe retries webhooks that don't get a fast 2xx response, so the same
     * "checkout.session.completed" event can arrive more than once. We guard
     * against double-processing two ways: an application-level pre-check
     * (fast path, avoids unnecessary work) AND a unique DB constraint on
     * provider_ref (the real safety net, since the pre-check alone has a
     * race window between two near-simultaneous webhook deliveries).
     */
    @Transactional
    public void handleCheckoutCompleted(Long ticketId, String providerRef, long amountCents, String currency) {
        if (paymentRepository.findByProviderRef(providerRef).isPresent()) {
            log.info("Payment with provider_ref={} already processed, ignoring duplicate webhook.", providerRef);
            return;
        }

        try {
            Payment payment = new Payment(ticketId, providerRef, amountCents, currency, "SUCCEEDED");
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // The unique constraint caught a race the pre-check above missed.
            // That's fine - the other concurrent request already recorded the
            // payment, so there's nothing more for this call to do.
            log.info("Duplicate payment insert for provider_ref={} caught by DB constraint.", providerRef);
            return;
        }

        ticketService.activateTicketAfterPayment(ticketId);
    }
}
