package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import org.example.blog.model.Comment;
import org.example.blog.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;

    @Observed
    public List<Comment> findRecentByPostId(Long postId) {
        return comments.findTop10ByPostIdOrderByCreatedAtDesc(postId);
    }

    @Observed
    public void save(Comment comment) {
        comments.save(comment);
    }
}
