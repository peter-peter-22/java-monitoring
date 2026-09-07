package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.example.blog.model.BlogPost;
import org.example.blog.repository.BlogPostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Warning: this service bean is leaking JPA entities, this should not happen in production.
 */
@Service
@RequiredArgsConstructor
public class BlogPostService {
    private final BlogPostRepository posts;

    @Observed
    public List<BlogPost> findRecentPosts() {
        return posts.findTop10ByOrderByCreatedAtDesc();
    }

    @Observed
    public Optional<BlogPost> findById(Long id) {
        return posts.findById(id);
    }

    @Observed
    public void save(BlogPost blogPost) {
        posts.save(blogPost);
    }
}
