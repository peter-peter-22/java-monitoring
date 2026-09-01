package org.example.blog.repository;

import org.example.blog.model.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
    List<BlogPost> findAllByOrderByCreatedAtDesc();
}
