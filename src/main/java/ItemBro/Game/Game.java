package ItemBro.Game;

import jakarta.persistence.*;

@Entity
@Table(name = "games")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private GameStatus status = GameStatus.OPENED;

    @Column(name = "image_url")
    private String imageUrl = null;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    protected Game() {}
    public Game(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public GameStatus getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }
}
