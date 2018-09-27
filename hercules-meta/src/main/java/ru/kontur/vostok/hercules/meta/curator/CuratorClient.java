package ru.kontur.vostok.hercules.meta.curator;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.util.properties.PropertiesExtractor;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * @author Gregory Koshelev
 */
public class CuratorClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CuratorClient.class);

    private final CuratorFramework curatorFramework;

    public CuratorClient(Properties properties) {
        this.curatorFramework = build(properties);
    }

    public void start() {
        curatorFramework.start();
    }

    public void stop() {
        curatorFramework.close();
    }

    public Optional<byte[]> read(String path) throws Exception {
        try {
            Stat stat = curatorFramework.checkExists().forPath(path);
            if (stat == null) {
                return Optional.empty();
            }
            byte[] bytes = curatorFramework.getData().forPath(path);
            return bytes != null ? Optional.of(bytes) : Optional.empty();
        } catch (KeeperException.NoNodeException ex) {
            return Optional.empty();
        }
    }

    public List<String> children(String path) throws Exception {
        List<String> children = curatorFramework.getChildren().forPath(path);
        return children;
    }

    public List<String> children(String path, CuratorWatcher watcher) throws Exception {
        return curatorFramework.getChildren().usingWatcher(watcher).forPath(path);
    }

    public void createIfAbsent(String path) throws Exception {
        try {
            curatorFramework.create().forPath(path);
        } catch (KeeperException.NodeExistsException ex) {
            return;//TODO: node already exists
        }
    }

    public CreationResult create(String path, byte[] data) throws Exception {
        try {
            curatorFramework.create().forPath(path, data);
            return CreationResult.ok();
        } catch (KeeperException.NodeExistsException ex) {
            return CreationResult.alreadyExist();
        } catch (Exception ex) {
            LOGGER.error("Error on creating path", ex);
            return CreationResult.unknown();
        }
    }

    public DeletionResult delete(String path) throws Exception {
        try {
            curatorFramework.delete().forPath(path);
            return DeletionResult.ok();
        } catch (KeeperException.NoNodeException ex) {
            return DeletionResult.notExist();
        } catch (Exception ex) {
            LOGGER.error("Error on deleting path", ex);
            return DeletionResult.unknown();
        }
    }

    public void createPath(String path) throws Exception {
        List<String> segments = Arrays.stream(path.split("/"))
                .filter(s -> Objects.nonNull(s) && !s.isEmpty())
                .collect(Collectors.toList());

        StringBuilder builder = new StringBuilder(path.length());
        for (String segment : segments) {
            builder.append('/').append(segment);
            String partialPath = builder.toString();
            Stat stat = curatorFramework.checkExists().forPath(partialPath);
            if (Objects.isNull(stat)) {
                curatorFramework.create().forPath(partialPath);
            }
        }
    }

    private static CuratorFramework build(Properties properties) {
        String connectString = properties.getProperty("connectString", "localhost:2181");
        int connectionTimeout = PropertiesExtractor.get(properties, "connectionTimeout", 10_000);
        int sessionTimeout = PropertiesExtractor.get(properties, "sessionTimeout", 30_000);
        int baseSleepTime = PropertiesExtractor.get(properties, "retryPolicy.baseSleepTime", 1_000);
        int maxRetries = PropertiesExtractor.get(properties, "retryPolicy.maxRetries", 5);
        int maxSleepTime = PropertiesExtractor.get(properties, "retryPolicy.maxSleepTime", 8_000);

        ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(baseSleepTime, maxRetries, maxSleepTime);

        CuratorFramework curatorFramework = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .connectionTimeoutMs(connectionTimeout)
                .sessionTimeoutMs(sessionTimeout)
                .retryPolicy(retryPolicy)
                .build();
        return curatorFramework;
    }
}
