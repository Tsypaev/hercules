package ru.kontur.vostok.hercules.kafka.util.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.kontur.vostok.hercules.configuration.util.PropertiesUtil;
import ru.kontur.vostok.hercules.util.functional.Result;
import ru.kontur.vostok.hercules.util.properties.PropertiesExtractor;

import java.util.Properties;
import java.util.function.Supplier;

/**
 * BulkSenderPool
 *
 * @author Kirill Sulim
 */
public class BulkSenderPool<Key, Value> {

    private static final String SENDER_POOL_SCOPE = "senderPool";

    private static final String POOL_SIZE_PARAM = "size";
    private static final int POOL_SIZE_DEFAULT_VALUE = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkSenderPool.class);

    private final Thread[] threads;

    public BulkSenderPool(
            final Properties sinkProperties,
            final BulkQueue<Key, Value> queue,
            final Supplier<BulkSender<Value>> senderFactory,
            final CommonBulkSinkStatusFsm status
    ) {
        final Properties senderPoolProperties = PropertiesUtil.ofScope(sinkProperties, SENDER_POOL_SCOPE);

        final int poolSize = PropertiesExtractor.getAs(senderPoolProperties, POOL_SIZE_PARAM, Integer.class)
                .orElse(POOL_SIZE_DEFAULT_VALUE);

        threads = new Thread[poolSize];

        for (int i = 0; i < threads.length; ++i) {
            Thread thread = new Thread(() -> {
                BulkSender<Value> sender = senderFactory.get();
                while (status.isRunning()) {
                    try {
                        BulkQueue.RunUnit<Key, Value> take = queue.take();
                        try {
                            if (!take.getFuture().isCancelled()) {
                                BulkSenderStat stat = sender.process(take.getStorage().getRecords());
                                take.getFuture().complete(Result.ok(new BulkQueue.RunResult<>(take.getStorage(), stat)));
                            }
                        } catch (BackendServiceFailedException e) {
                            take.getFuture().complete(Result.error(e));
                        }
                    } catch (InterruptedException e) {
                        /*
                         * InterruptedException thrown from queue.take marks that processing stopped
                         */
                        break;
                    }
                }
            });
            thread.setName(String.format("sender-pool-%d", i));
            thread.setUncaughtExceptionHandler(ExitOnThrowableHandler.INSTANCE);
            threads[i] = thread;
        }
    }

    public void start() {
        for (Thread thread : threads) {
            thread.start();
        }
    }

    public void stop() throws InterruptedException {
        for (Thread thread : threads) {
            thread.interrupt();
        }
        for (Thread thread : threads) {
            thread.join();
        }
    }
}

