package org.example.blog.security;

import io.micrometer.tracing.Tracer;
import org.example.blog.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, Tracer tracer) {
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
        http.addFilterAfter(new AuthenticatedUserMdcFilter(users, tracer), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
