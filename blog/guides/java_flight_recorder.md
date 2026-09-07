## Java flight recorder guide

JVM event profiler built into the JDK.

This tool collects the performance metrics of function calls.

It can be started with JVM arguments and the `jcmd` CLI tool.

The standard way is to give the JVM arguments directly from the CLI.

### Example usage

```
./mvnw spring-boot:run \
    -Dspring-boot.run.jvmArguments='-XX:StartFlightRecording:settings=default,disk=true,maxage=1h,maxsize=256m,name=blog'
```

Here, `settings=default` sets the recording profile.
- default: Low impact on performance, suitable for production.
- profile: More tracked metrics, higher performance costs. Development only.

The logs can be dumped into a `.jfr` file with the jcmd CLI.

```jcmd BlogApplication JFR.dump name=blog filename=./metrics.jfr```

The CLI can refer to the main class name, or the PID.

An example JFR dump command is logged when the java application starts with JFR enabled.

The created jfr file can be viewed with the intellij IDE or the [java mission control GUI](https://adoptium.net/jmc).

### Links

- [JFR basic usage on java 8](https://www.baeldung.com/java-flight-recorder-monitoring)