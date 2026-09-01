package org.example.blog.security;

import lombok.RequiredArgsConstructor;
import org.example.blog.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @RequiredArgsConstructor
    static class DatabaseUserDetailsService implements UserDetailsService {
        private final UserRepository userRepository;

        @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
            var found = userRepository.findByUsername(username);
            if (found.isEmpty()) {
                throw new UsernameNotFoundException("User not found");
            }
            return User.withUsername(found.get().getUsername()).password(found.get().getPassword_hash()).build();
        }
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository userRepository) {
        return new DatabaseUserDetailsService(userRepository);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/posts/*", "/register", "/css/**", "/login", "/logout", "/error","/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .logoutUrl("/logout")
                        .permitAll()
                )
                .securityContext(context -> context
                        .requireExplicitSave(true)
                );
        return http.build();
    }
}
