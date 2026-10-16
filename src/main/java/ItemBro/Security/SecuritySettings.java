package ItemBro.Security;

import ItemBro.Exception.UserNotFoundException;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Optional;

@Configuration
public class SecuritySettings {

    private final UserRepository userRepository;
    public SecuritySettings(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        return username -> {
            Optional<User> u = userRepository.findByUsername(username);
            if (u.isPresent()) {
                User user = u.get();
                return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles(user.getRole().toString())
                        .build();
            } else {
                throw new UserNotFoundException("User not found");
            }
        };
    }

    @Bean
    SecurityFilterChain access(HttpSecurity http) throws Exception {
        return http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(rules -> rules
                        .requestMatchers(HttpMethod.GET, "/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/advices").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/user").anonymous()
                        .requestMatchers(HttpMethod.POST, "/api/advices").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/orders/").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/games").authenticated()

                        .requestMatchers(HttpMethod.DELETE, "/api/advices/{id}").authenticated()

                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/cancel").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/confirm").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/feedback").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/response").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
