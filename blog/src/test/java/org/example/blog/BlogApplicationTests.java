package org.example.blog;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:blog;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class BlogApplicationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void invalidRegistrationShowsValidationErrors() throws Exception {
        mvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "ab")
                        .param("password", "password"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(Matchers.containsString("length must be between 3 and 50")));
    }

    @Test
    void userCanRegisterLoginCreatePostAndComment() throws Exception {
        var username = "user";
        var password = "password";

        mvc.perform(post("/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

        var login = mvc.perform(formLogin()
                        .user(username)
                        .password(password))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"))
                .andReturn().getRequest();
        var session = login.getSession();

        mvc.perform(post("/posts").session((MockHttpSession) session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "").param("body", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("new-post"));

        mvc.perform(post("/posts").session((MockHttpSession) session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("title", "Hello").param("body", "First post"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));

        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Hello")));

        mvc.perform(get("/posts/1/comments/new").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Comment on: Hello")));

        mvc.perform(post("/posts/1/comments").session((MockHttpSession) session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("body", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("new-comment"));

        mvc.perform(post("/posts/1/comments").session((MockHttpSession) session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("body", "Nice post"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/posts/1"));

        mvc.perform(get("/posts/1")).andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("Nice post")));
    }
}
