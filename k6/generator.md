# How to use the generated data

The blog application generates database rows on startup. 

K6 uses the initialized data of the blog application that is defined in the application config.

## Configuration

The counts of the generated entities are defined in the spring external configuration.

Example:

`application.yaml`

```yaml
blog:
  data:
    comments: 100
    posts: 20
    users: 200
    enabled: true
```

The generated data must be at least as much as the k6 test demands.

## Users

The usernames are structured as `user-${i}` (eg., user-0) where "i" starts from 0 and steps 1 for each added user.

All passwords are "password".

Both the usernames and the passwords are knows so k6 can predict them.

## Posts

The posts have sequential ids.