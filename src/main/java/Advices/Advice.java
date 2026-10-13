package Advices;

import Games.Game;
import Users.User;
import jakarta.persistence.*;

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

    protected Advice() {}
    public Advice(String name, Game game, User seller) {
        this.name = name;
        this.game = game;
        this.seller = seller;
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
}
