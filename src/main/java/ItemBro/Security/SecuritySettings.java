package ItemBro.Security;

import ItemBro.Exception.UserNotFoundException;
import ItemBro.User.User;
import ItemBro.User.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

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
        var contexts = new HttpSessionSecurityContextRepository();

        return http
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(rules -> rules
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/games").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/advices").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{username}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{username}/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/users/{username}/advices").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/{id}").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/me").authenticated()

                        .requestMatchers(HttpMethod.POST, "/api/user").anonymous()
                        .requestMatchers(HttpMethod.POST, "/api/advices").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/orders/").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/games").authenticated()

                        .requestMatchers(HttpMethod.DELETE, "/api/advices/{id}").authenticated()

                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/cancel").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/confirm").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/feedback").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/{id}/response").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/games/{id}/delete").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/admin/orders").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .securityContext(context -> context.securityContextRepository(contexts))
                .httpBasic(basic -> basic.securityContextRepository(contexts))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(204))
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .build();
    }
}
