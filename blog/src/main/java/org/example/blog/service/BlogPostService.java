package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.blog.configuration.BlogPostMetrics;
import org.example.blog.model.BlogPost;
import org.example.blog.repository.BlogPostRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Warning: this service bean is leaking JPA entities, this should not happen in production.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlogPostService {
    private final BlogPostRepository posts;
    private final BlogPostMetrics metrics;

    @Observed
    public List<BlogPost> findRecentPosts() {
        return posts.findTop10ByOrderByCreatedAtDesc();
    }

    @Observed
    public Optional<BlogPost> findById(Long id) {
        return posts.findById(id);
    }

    @Observed
    public Optional<BlogPost> findForDisplayById(Long id) {
        return posts.findForDisplayById(id);
    }

    @Observed
    public void save(BlogPost blogPost) {
        BlogPost saved = posts.save(blogPost);
        metrics.incrementBlogPostsCreatedCounter();
        log.info("Created blog post id={}", saved.getId());
    }
}
