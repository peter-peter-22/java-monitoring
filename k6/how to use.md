# How to run

Test

```docker run --user "$(id -u):$(id -g)" --rm -v ${PWD}/k6:/app -w /app grafana/k6:2.2.0 run --summary-export=result.json test.js```

Blog

```docker run --user "$(id -u):$(id -g)" --rm --network=host -v ${PWD}/k6:/app -w /app grafana/k6:2.2.0 run --summary-export=result.json blog.js```

## Explanation:
`--user "$(id -u):$(id -g)"` 

The container runs with the permissions of the user who wrote uses the CLI.
Without this, the container can't write to the mounted directory.

`--rm`

The container is deleted at the end of the run.

`-v ${PWD}:/app`

Shorthand for `--mount type=bind ${PWD}/k6:/app`.

This mounts the k6 directory (relative to the current work directory) to the /app directory
of the container.

`-w /app`

Set the work directory of the container to /app.

`run --summary-export=result.json test.js`

Use k6 to run test.js and output the results to result.json.

`--network=host`

The container sees the network of the host, enabling simple access to localhost.

Normally, it sees an internal network where localhost belongs to the container.


