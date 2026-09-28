package com.bustransit.ticketing.entity;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passenger_id", nullable = false, length = 120)
    private String passengerId;

    @Column(name = "route_id", nullable = false)
    private Long routeId;

    @Column(name = "qr_token", columnDefinition = "TEXT")
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status = TicketStatus.PENDING_PAYMENT;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    protected Ticket() {
        // JPA
    }

    public Ticket(String passengerId, Long routeId) {
        this.passengerId = passengerId;
        this.routeId = routeId;
        this.status = TicketStatus.PENDING_PAYMENT;
    }

    public Long getId() {
        return id;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public String getQrToken() {
        return qrToken;
    }

    public void setQrToken(String qrToken) {
        this.qrToken = qrToken;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
