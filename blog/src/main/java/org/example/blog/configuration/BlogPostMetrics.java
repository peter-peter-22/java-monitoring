package org.example.blog.configuration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.blog.repository.BlogPostRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

@Repository
public class BlogPostMetrics {
    private final BlogPostRepository posts;
    private final Counter blogPostsCreatedCounter;
    private long totalPosts;

    public BlogPostMetrics(BlogPostRepository posts, MeterRegistry registry) {
        this.posts = posts;

        // total posts gauge
        Gauge.builder("blog.posts.current", () -> totalPosts)
                .description("Current number of blog posts")
                .register(registry);

        // posts created counter
        this.blogPostsCreatedCounter = Counter.builder("blog.posts.created")
                .description("Number of blog posts created")
                .register(registry);
    }
    @Scheduled(fixedDelay = 15_000)
    void updateTotalCount() {
        totalPosts = posts.count();
    }

    public void incrementBlogPostsCreatedCounter() {
        blogPostsCreatedCounter.increment();
    }
}
