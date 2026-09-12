package org.example.blog.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.blog.dto.CommentRequest;
import org.example.blog.dto.PostRequest;
import org.example.blog.dto.RegisterRequest;
import org.example.blog.model.AppUser;
import org.example.blog.model.BlogPost;
import org.example.blog.model.Comment;
import org.example.blog.repository.CommentRepository;
import org.example.blog.repository.UserRepository;
import org.example.blog.service.BlogPostService;
import org.example.blog.service.CommentService;
import org.example.blog.service.UserService;
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
/*
Development only.
This class looks unmaintainable for multiple reasons.
Do not mix business logic and implementation details in real production code.
 */
public class BlogController {
    private final PasswordEncoder passwordEncoder;
    private final BlogPostService blogPostService;
    private final CommentService commentService;
    private final UserService userService;

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("posts", blogPostService.findRecentPosts());
        return "home";
    }

    @GetMapping("/posts/{id}")
    String post(@PathVariable Long id, Model model) {
        BlogPost post = blogPostService.findForDisplayById(id).orElseThrow();
        model.addAttribute("post", post);
        model.addAttribute("comments", commentService.findRecentByPostId(id));
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

        if (userService.existsByUsername(username)) {
            model.addAttribute("error", "Username is already taken.");
            return "register";
        }

        userService.save(new AppUser(null, username, passwordEncoder.encode(password)));
        return "redirect:/login";
    }

    @GetMapping("/posts/new")
    String newPost(Model model) {
        model.addAttribute("postRequest", new PostRequest(null, null));
        return "new-post";
    }

    @GetMapping("/posts/{id}/comments/new")
    String newComment(@PathVariable Long id, Model model) {
        model.addAttribute("post", blogPostService.findById(id).orElseThrow());
        model.addAttribute("commentRequest", new CommentRequest(null));
        return "new-comment";
    }

    @PostMapping("/posts")
    String createPost(@ModelAttribute("postRequest") @Valid PostRequest request,
                      BindingResult bindingResult, Authentication auth) {
        if (bindingResult.hasErrors()) return "new-post";

        AppUser user = userService.findByUsername(auth.getName()).orElseThrow();
        blogPostService.save(new BlogPost(null, request.title(), request.body(), user, null));
        return "redirect:/";
    }

    @PostMapping("/posts/{id}/comments")
    String comment(@PathVariable Long id,
                   @ModelAttribute("commentRequest") @Valid CommentRequest request,
                   BindingResult bindingResult, Model model, Authentication auth) {
        BlogPost post = blogPostService.findById(id).orElseThrow();
        if (bindingResult.hasErrors()) {
            model.addAttribute("post", post);
            return "new-comment";
        }

        AppUser user = userService.findByUsername(auth.getName()).orElseThrow();
        commentService.save(new Comment(null, request.body().trim(), post, user, null));
        return "redirect:/posts/" + id;
    }
}
