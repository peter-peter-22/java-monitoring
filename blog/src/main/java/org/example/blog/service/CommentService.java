package org.example.blog.service;

import io.micrometer.observation.annotation.Observed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.blog.model.Comment;
import org.example.blog.repository.CommentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository comments;

    @Observed
    public List<Comment> findRecentByPostId(Long postId) {
        return comments.findTop10ByPostIdOrderByCreatedAtDesc(postId);
    }

    @Observed
    public void save(Comment comment) {
        Comment saved = comments.save(comment);
        log.info("Created comment id={} for post id={}", saved.getId(), saved.getPost().getId());
    }
}
