# Hercules Sentry Sink
Sentry Sink is used to move Log Event with exceptions from Kafka to Sentry.

## Command line
`java $JAVA_OPTS -jar hercules-sentry-sink.jar application.properties=file://path/to/properties/file`

### Initialization
Sentry Sink uses registry from ZooKeeper. Thus, ZK should be configured by [Hercules Init](../hercules-init/README.md). See Hercules Init for details.

Stream with log events should be predefined.
