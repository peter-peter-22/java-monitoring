package org.example.blog.repository;

import org.example.blog.model.Comment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @EntityGraph(attributePaths = "author")
    List<Comment> findTop10ByPostIdOrderByCreatedAtDesc(Long postId);
}
