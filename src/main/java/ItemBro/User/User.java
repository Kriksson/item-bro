package ItemBro.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role = UserRole.USER;

    @Column(name = "avatar_url")
    private String avatarUrl = null;

    @Column(name = "age")
    private Integer age = null;

    @Column(name = "city", length = 100)
    private String city = null;

    @Column(name = "about", length = 1000)
    private String about = null;


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

    public UserRole getRole() {
        return role;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public Integer getAge() {
        return age;
    }

    public String getCity() {
        return city;
    }

    public String getAbout() {
        return about;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return getId() != null && getId().equals(user.getId());
    }

    @Override
    public int hashCode() {
        return User.class.hashCode();
    }
}
