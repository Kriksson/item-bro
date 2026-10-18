package ItemBro.Advice;

import ItemBro.Game.Game;
import ItemBro.User.User;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "advices")
public class Advice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch =  FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AdviceStatus status;

    @Column(name = "image_url")
    private String imageUrl = null;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected Advice() {}
    public Advice(String name, Game game, User seller, BigDecimal price, AdviceStatus status) {
        this.name = name;
        this.game = game;
        this.seller = seller;
        this.price = price;
        this.status = status;
    }

    public Long getId() {

        return id;
    }

    public String getName() {
        return name;
    }

    public Game getGame() {
        return game;
    }

    public User getSeller() {
        return seller;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public AdviceStatus getStatus() { return status; }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setStatus(AdviceStatus status) { this.status = status; }
}
