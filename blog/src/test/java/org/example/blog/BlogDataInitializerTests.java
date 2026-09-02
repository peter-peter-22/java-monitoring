package org.example.blog;

import org.example.blog.repository.BlogPostRepository;
import org.example.blog.repository.CommentRepository;
import org.example.blog.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog-seed;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "blog.data.enabled=true",
        "blog.data.users=3",
        "blog.data.posts=4",
        "blog.data.comments=5"
})
class BlogDataInitializerTests {
    @Autowired
    UserRepository users;

    @Autowired
    BlogPostRepository posts;

    @Autowired
    CommentRepository comments;

    @Test
    void generatesConfiguredNumberOfEntitiesOnStartup() {
        assertEquals(3, users.count());
        assertEquals(4, posts.count());
        assertEquals(5, comments.count());
    }
}
