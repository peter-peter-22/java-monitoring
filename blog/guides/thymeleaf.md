# Thymeleaf guide

A spring-boot first HTML templating engine.

## CSRF

Spring security uses a CSRF token that must be added to every form submission.

Using ```th:action="@{/url}"``` instead of ```action="/url"``` automatically handles this.

## Links

- [Thymeleaf and spring security](https://www.thymeleaf.org/doc/articles/springsecurity.html)
- [Thymeleaf and forms](https://spring.io/guides/gs/handling-form-submission)