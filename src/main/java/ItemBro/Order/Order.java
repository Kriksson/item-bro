package ItemBro.Order;

import ItemBro.Advice.Advice;
import ItemBro.Game.Game;
import ItemBro.User.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "advice_id")
    private Advice advice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "rating", nullable = false)
    private Integer rating = -1;

    @Column(name = "feedback", length = 300)
    private String feedback = null;

    @Column(name = "seller_response", length = 300)
    private String sellerResponse = null;

    protected Order() {}
    public Order(Advice advice, User buyer, User seller, BigDecimal price, OrderStatus status) {
        this.advice = advice;
        this.buyer = buyer;
        this.seller = seller;
        this.price = price;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Advice getAdvice() {
        return advice;
    }

    public User getBuyer() {
        return buyer;
    }

    public User getSeller() {
        return seller;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getSellerResponse() {
        return sellerResponse;
    }

    public Long getVersion() {
        return version;
    }

    public Integer getRating() {
        return rating;
    }

    public String getFeedback() {
        return feedback;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public void setSellerResponse(String sellerResponse) {
        this.sellerResponse = sellerResponse;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
