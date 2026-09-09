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
        /*
        Beware of the naming conventions.
        "blog.posts.current" is displayed as gauge in the drilldown menu of the grafana UI
        while "blog.posts.total" is displayed as counter (rate).

        While the dashboard can define and save custom display formats, displaying
        the correct format in the drilldown menu is convenient.
        */
        Gauge.builder("blog.posts.current", () -> totalPosts)
                .description("Current number of blog posts")
                .register(registry);

        // posts created counter
        this.blogPostsCreatedCounter = Counter.builder("blog.posts.created")
                .description("Number of blog posts created")
                .register(registry);
    }

    /*
    The count is re-calculated independently of the actuator polling because
    expensive database queries should not depend on this polling rate.

    In distributed systems, this query would run for each instance.
    To avoid this, the simplest way is to use a separate server for the global gauges.

    Counting all rows periodically is not recommended for production.
     */
    @Scheduled(fixedDelay = 15_000)
    void updateTotalCount() {
        totalPosts = posts.count();
    }

    public void incrementBlogPostsCreatedCounter() {
        blogPostsCreatedCounter.increment();
    }
}
