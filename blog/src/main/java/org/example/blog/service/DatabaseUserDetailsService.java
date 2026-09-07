package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    @Observed
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var found = userService.findByUsername(username);
        if (found.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }
        return User.withUsername(found.get().getUsername()).password(found.get().getPassword_hash()).build();
    }
}
