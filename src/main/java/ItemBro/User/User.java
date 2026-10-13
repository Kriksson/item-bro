package ItemBro.User;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    protected User() {}
    public User(String username, String passwordHash, BigDecimal balance) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
