package org.example.blog.seed;

import lombok.RequiredArgsConstructor;
import org.example.blog.model.AppUser;
import org.example.blog.model.BlogPost;
import org.example.blog.model.Comment;
import org.example.blog.repository.BlogPostRepository;
import org.example.blog.repository.CommentRepository;
import org.example.blog.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class BlogDataGenerator {
    private static final String GENERATED_PASSWORD = "password";
    private static final String[] ADJECTIVES = {
            "curious", "brisk", "quiet", "bright", "playful", "thoughtful", "bold", "patient"
    };
    private static final String[] TOPICS = {
            "databases", "testing", "performance", "coffee", "design", "security", "spring", "metrics"
    };

    private final UserRepository users;
    private final BlogPostRepository posts;
    private final CommentRepository comments;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void generate(BlogDataConfiguration configuration) {
        if (!configuration.enabled()) {
            return;
        }
        validateRelationships(configuration);

        String passwordHash = passwordEncoder.encode(GENERATED_PASSWORD);
        List<AppUser> generatedUsers = generateUsers(configuration.users(), passwordHash);
        List<BlogPost> generatedPosts = generatePosts(configuration.posts(), generatedUsers);
        List<Comment> generatedComments = generateComments(configuration.comments(), generatedUsers, generatedPosts);

        users.saveAll(generatedUsers);
        posts.saveAll(generatedPosts);
        comments.saveAll(generatedComments);
    }

    private List<AppUser> generateUsers(int count, String passwordHash) {
        List<AppUser> generatedUsers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            generatedUsers.add(new AppUser(null, "user-" + i, passwordHash));
        }
        return generatedUsers;
    }

    private List<BlogPost> generatePosts(int count, List<AppUser> generatedUsers) {
        List<BlogPost> generatedPosts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            AppUser author = randomItem(generatedUsers);
            String topic = randomItem(TOPICS);
            generatedPosts.add(new BlogPost(
                    null,
                    capitalize(randomItem(ADJECTIVES)) + " thoughts on " + topic,
                    "This generated post explores " + topic + " from a " + randomItem(ADJECTIVES) + " perspective. "
                            + "Reference: " + token(),
                    author,
                    null));
        }
        return generatedPosts;
    }

    private List<Comment> generateComments(int count, List<AppUser> generatedUsers, List<BlogPost> generatedPosts) {
        List<Comment> generatedComments = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            generatedComments.add(new Comment(
                    null,
                    capitalize(randomItem(ADJECTIVES)) + " perspective on " + randomItem(TOPICS)
                            + ". Reference: " + token(),
                    randomItem(generatedPosts),
                    randomItem(generatedUsers),
                    null));
        }
        return generatedComments;
    }

    private void validateRelationships(BlogDataConfiguration configuration) {
        if (configuration.posts() > 0 && configuration.users() == 0) {
            throw new IllegalArgumentException("At least one user is required when generating posts");
        }
        if (configuration.comments() > 0 && configuration.posts() == 0) {
            throw new IllegalArgumentException("At least one post is required when generating comments");
        }
    }

    private String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private <T> T randomItem(List<T> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }

    private String randomItem(String[] values) {
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }
}
