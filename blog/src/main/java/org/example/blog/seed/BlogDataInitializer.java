package org.example.blog.seed;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BlogDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BlogDataInitializer.class);

    private final BlogDataConfiguration configuration;
    private final BlogDataGenerator generator;

    @Override
    public void run(ApplicationArguments args) {
        if (!configuration.enabled()) {
            log.info("Blog data initialization is disabled");
            return;
        }

        generator.generate(configuration);
        log.info("Generated {} users, {} posts and {} comments", configuration.users(), configuration.posts(),
                configuration.comments());
    }
}
