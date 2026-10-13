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

    protected Advice() {}
    public Advice(String name, Game game, User seller, BigDecimal price) {
        this.name = name;
        this.game = game;
        this.seller = seller;
        this.price = price;
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
}
