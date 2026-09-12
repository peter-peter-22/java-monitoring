package org.example.blog.repository;

import org.example.blog.model.BlogPost;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
    @EntityGraph(attributePaths = "author")
    List<BlogPost> findTop10ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "author")
    @Query("select post from BlogPost post where post.id = :id")
    Optional<BlogPost> findForDisplayById(@Param("id") Long id);
}
