package org.example.blog.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.blog.dto.CommentRequest;
import org.example.blog.dto.PostRequest;
import org.example.blog.dto.RegisterRequest;
import org.example.blog.model.AppUser;
import org.example.blog.model.BlogPost;
import org.example.blog.model.Comment;
import org.example.blog.repository.BlogPostRepository;
import org.example.blog.repository.CommentRepository;
import org.example.blog.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequiredArgsConstructor
public class BlogController {
    private final BlogPostRepository posts;
    private final CommentRepository comments;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("posts", posts.findAllByOrderByCreatedAtDesc());
        return "home";
    }

    @GetMapping("/posts/{id}")
    String post(@PathVariable Long id, Model model) {
        BlogPost post = posts.findById(id).orElseThrow();
        model.addAttribute("post", post);
        model.addAttribute("comments", comments.findTop10ByPostIdOrderByCreatedAtDesc(id));
        return "post";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/register")
    String register(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest("", ""));
        return "register";
    }

    @PostMapping("/register")
    String register(@ModelAttribute("registerRequest") @Valid RegisterRequest body,
                    BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) return "register";

        var username = body.username().trim();
        var password = body.password().trim();

        if (users.existsByUsername(username)) {
            model.addAttribute("error", "Username is already taken.");
            return "register";
        }

        users.save(new AppUser(null, username, passwordEncoder.encode(password)));
        return "redirect:/login";
    }

    @GetMapping("/posts/new")
    String newPost(Model model) {
        model.addAttribute("postRequest", new PostRequest(null, null));
        return "new-post";
    }

    @GetMapping("/posts/{id}/comments/new")
    String newComment(@PathVariable Long id, Model model) {
        model.addAttribute("post", posts.findById(id).orElseThrow());
        model.addAttribute("commentRequest", new CommentRequest(null));
        return "new-comment";
    }

    @PostMapping("/posts")
    String createPost(@ModelAttribute("postRequest") @Valid PostRequest request,
                      BindingResult bindingResult, Authentication auth) {
        if (bindingResult.hasErrors()) return "new-post";

        AppUser user = users.findByUsername(auth.getName()).orElseThrow();
        posts.save(new BlogPost(null, request.title(), request.body(), user, null));
        return "redirect:/";
    }

    @PostMapping("/posts/{id}/comments")
    String comment(@PathVariable Long id,
                   @ModelAttribute("commentRequest") @Valid CommentRequest request,
                   BindingResult bindingResult, Model model, Authentication auth) {
        BlogPost post = posts.findById(id).orElseThrow();
        if (bindingResult.hasErrors()) {
            model.addAttribute("post", post);
            return "new-comment";
        }

        AppUser user = users.findByUsername(auth.getName()).orElseThrow();
        comments.save(new Comment(null, request.body().trim(), post, user, null));
        return "redirect:/posts/" + id;
    }
}
