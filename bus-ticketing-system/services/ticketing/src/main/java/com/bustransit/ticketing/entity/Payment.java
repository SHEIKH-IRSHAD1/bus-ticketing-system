package com.bustransit.ticketing.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "provider_ref", nullable = false, length = 200, unique = true)
    private String providerRef;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Payment() {
        // JPA
    }

    public Payment(Long ticketId, String providerRef, long amountCents, String currency, String status) {
        this.ticketId = ticketId;
        this.providerRef = providerRef;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public String getProviderRef() {
        return providerRef;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }
}
