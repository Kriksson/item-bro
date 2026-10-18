package ItemBro.Order;

import ItemBro.User.User;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_messages")
public class OrderMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "text", nullable = false, length = 1000)
    private String text;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "client_message_id", nullable = false)
    private UUID clientMessageId;

    protected OrderMessage() {}
    public OrderMessage(Order order, User sender, String text, UUID clientMessageId) {
        this.order = order;
        this.sender = sender;
        this.text = text;
        this.sentAt = Instant.now();
        this.clientMessageId = clientMessageId;
    }

    public Long getId() {
        return id;
    }

    public Order getOrder() {
        return order;
    }

    public User getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }
}
