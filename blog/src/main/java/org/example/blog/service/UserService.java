package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.example.blog.model.AppUser;
import org.example.blog.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;

    @Observed
    public Optional<AppUser> findByUsername(String username) {
        return users.findByUsername(username);
    }

    @Observed
    public boolean existsByUsername(String username) {
        return users.existsByUsername(username);
    }

    @Observed
    public void save(AppUser appUser) {
        users.save(appUser);
    }
}
