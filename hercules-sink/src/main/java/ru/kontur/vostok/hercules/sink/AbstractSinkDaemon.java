package ru.kontur.vostok.hercules.sink;

import com.codahale.metrics.Meter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.configuration.Scopes;
import ru.kontur.vostok.hercules.configuration.util.ArgsParser;
import ru.kontur.vostok.hercules.configuration.util.PropertiesReader;
import ru.kontur.vostok.hercules.configuration.util.PropertiesUtil;
import ru.kontur.vostok.hercules.health.CommonMetrics;
import ru.kontur.vostok.hercules.health.MetricsCollector;
import ru.kontur.vostok.hercules.undertow.util.servers.ApplicationStatusHttpServer;
import ru.kontur.vostok.hercules.util.application.ApplicationContextHolder;
import ru.kontur.vostok.hercules.util.properties.PropertyDescription;
import ru.kontur.vostok.hercules.util.properties.PropertyDescriptions;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Gregory Koshelev
 */
public abstract class AbstractSinkDaemon {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSinkDaemon.class);

    private Sender sender;
    private SinkPool sinkPool;
    private ExecutorService executor;
    private ApplicationStatusHttpServer applicationStatusHttpServer;

    protected MetricsCollector metricsCollector;

    protected void run(String[] args) {
        Map<String, String> parameters = ArgsParser.parse(args);

        Properties properties = PropertiesReader.read(parameters.getOrDefault("application.properties", "applicationProperties"));

        Properties contextProperties = PropertiesUtil.ofScope(properties, Scopes.CONTEXT);
        Properties metricsProperties = PropertiesUtil.ofScope(properties, Scopes.METRICS);
        Properties httpServerProperties = PropertiesUtil.ofScope(properties, Scopes.HTTP_SERVER);
        Properties sinkProperties = PropertiesUtil.ofScope(properties, Scopes.SINK);

        Properties senderProperties = PropertiesUtil.ofScope(sinkProperties, Scopes.SENDER);

        String daemonId = getDaemonId();
        ApplicationContextHolder.init(getDaemonName(), getDaemonId(), contextProperties);

        metricsCollector = new MetricsCollector(metricsProperties);
        metricsCollector.start();
        CommonMetrics.registerCommonMetrics(
                metricsCollector,
                Props.THREAD_GROUP_REGEXP.extract(metricsProperties));

        applicationStatusHttpServer = new ApplicationStatusHttpServer(httpServerProperties);
        applicationStatusHttpServer.start();

        this.sender = createSender(senderProperties, metricsCollector);
        sender.start();

        int poolSize = Props.POOL_SIZE.extract(sinkProperties);
        this.executor = Executors.newFixedThreadPool(poolSize);

        Meter droppedEventsMeter = metricsCollector.meter("droppedEvents");
        Meter processedEventsMeter = metricsCollector.meter("processedEvents");
        Meter rejectedEventsMeter = metricsCollector.meter("rejectedEvents");
        Meter totalEventsMeter = metricsCollector.meter("totalEvents");

        this.sinkPool =
                new SinkPool(
                        poolSize,
                        () -> new SimpleSink(
                                executor,
                                daemonId,
                                sinkProperties,
                                sender,
                                droppedEventsMeter,
                                processedEventsMeter,
                                rejectedEventsMeter,
                                totalEventsMeter));
        sinkPool.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    protected abstract Sender createSender(Properties senderProperties, MetricsCollector metricsCollector);

    protected abstract String getDaemonId();

    protected abstract String getDaemonName();

    private void shutdown() {
        try {
            if (sinkPool != null) {
                sinkPool.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping Sink pool", t);
        }

        try {
            if (executor != null) {
                executor.shutdown();
                executor.awaitTermination(5_000L, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping sink thread executor", t);
        }

        try {
            if (sender != null) {
                sender.stop(5_000L, TimeUnit.MILLISECONDS);
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping sender", t);
        }

        try {
            if (applicationStatusHttpServer != null) {
                applicationStatusHttpServer.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping http status server", t);
        }

        try {
            if (metricsCollector != null) {
                metricsCollector.stop();
            }
        } catch (Throwable t) {
            LOGGER.error("Error on stopping metrics collector", t);
        }
    }

    private static class Props {
        static final PropertyDescription<Integer> POOL_SIZE =
                PropertyDescriptions.integerProperty("poolSize").withDefaultValue(1).build();

        static final PropertyDescription<String[]> THREAD_GROUP_REGEXP =
                PropertyDescriptions.arrayOfStringsProperty("thread.group.regexp").
                        withDefaultValue(new String[]{}).
                        build();
    }
}
