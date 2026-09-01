package org.example.blog.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "blog_post")
public class BlogPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, columnDefinition = "text")
    private String body;
    @ManyToOne(optional = false) @JoinColumn(name = "author_id")
    private AppUser author;
    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private Instant createdAt;
}
