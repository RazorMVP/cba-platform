package com.cba.social;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "sms_messages")
@Getter @Setter @NoArgsConstructor
public class SmsMessage {

    public enum DeliveryStatus { PENDING, SENT, FAILED, INVALID }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private SmsCampaign campaign;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "mobile_no", length = 30)
    private String mobileNo;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.PENDING;

    @Column(name = "submitted_on", nullable = false, updatable = false)
    private OffsetDateTime submittedOn = OffsetDateTime.now();

    @Column(name = "delivered_on")
    private OffsetDateTime deliveredOn;
}
