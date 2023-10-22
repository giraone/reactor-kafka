package com.giraone.kafka.pipeline.config.properties;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Setter
@Getter
@NoArgsConstructor
@ToString
// exclude from test coverage
@Generated
public class KafkaConsumerProperties {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerProperties.class);

    public KafkaConsumerProperties(String groupId) {
        this.groupId = groupId;
    }

    /**
     * GroupId: pipe, consume, pipe-docker, consume-docker
     */
    private String groupId = "pipe-default";
    /**
     * Type of scheduler - either "parallel", "newParallel" or "newBoundedElastic".
     * Default is "newParallel".
     */
    private String schedulerType = "newParallel";
    /**
     * Concurrency (threads or pooled workers) of the scheduler, that is used for consuming events,
     * when the scheduler allows parallel work.
     * Default is 8.
     */
    private int concurrency = 8;
    /**
     * Flag, whether auto-commit is used - default=false. If true commitInterval/commitBatchSize are used..
     **/
    private boolean autoCommit = false;
    /**
     * Configures commit interval for automatic commits.
     * At least one commit operation is attempted within this interval if records are consumed and acknowledged.
     */
    private Duration commitInterval = Duration.ofSeconds(1L);
    /**
     * Configures commit batch size for automatic commits.
     * At least one commit operation is attempted when the number of acknowledged uncommitted offsets reaches this batch size.
     */
    private int commitBatchSize = 10;
    /**
     * Configures the retry commit interval for commits that fail with non-fatal RetriableCommitFailedException.
     */
    private Duration commitRetryInterval = null;
    /**
     * The maximum number of records returned in a single call to poll().
     * Kafka default = 500.
     * Note, that <code>max.poll.records</code> does not impact the underlying fetching behavior.
     * The consumer will cache the records from each fetch request and returns them incrementally from each poll.
     */
    private int maxPollRecords = 500; // Kafka default = 500
    /**
     * The maximum delay between invocations of poll() in seconds when using consumer group management.
     * Kafka default = 300000 milliseconds = 5 minutes.
     * This places an upper bound on the amount of time that the consumer can be idle before fetching more records.
     */
    private Duration maxPollInterval = Duration.ofMinutes(5L); // Kafka default = 300000 (= 5 minutes)
    /**
     * The maximum amount of data the server should return for a fetch request.
     * Kafka default = 50MByte.
     * Records are fetched in batches by the consumer, and if the first record batch in the first non-empty partition
     * of the fetch is larger than this value, the record batch will still be returned to ensure that the consumer
     * can make progress. As such, this is not a absolute maximum.
     * Note that the consumer performs multiple fetches in parallel.
     */
    private int fetchMaxBytes = 52428800; // Kafka default = 52428800
    /**
     * The maximum amount of data per-partition the server will return.
     * Kafka default = 1MByte.
     * Records are fetched in batches by the consumer.
     * If the first record batch in the first non-empty partition of the fetch is larger than this limit, the batch
     * will still be returned to ensure that the consumer can make progress.
     * See fetch.max.bytes for limiting the consumer request size.
     */
    private int maxPartitionFetchBytes = 1048576; // Kafka default = 1048576
    /**
     * The maximum amount of time the server will block before answering the fetch request if there isn’t sufficient
     * data to immediately satisfy the requirement given by fetch.min.bytes.
     * Kafka default = 500ms.
     */
    private Duration fetchMaxWaitMs = Duration.ofMillis(500); // Kafka default = 500ms
    /**
     * Retries, when inbound flux (consumer) fails.
     * Since in reactive streams an error represents a terminal signal, any error signal emitted in the inbound
     * Flux will cause the subscription to be cancelled and effectively cause the consumer to shut down.
     * This can be mitigated by using this retry.
     */
    private ApplicationProperties.RetrySpecificationKafka retrySpecification = new ApplicationProperties.RetrySpecificationKafka();

    /**
     * Build a scheduler. Scheduler type and concurrency are fetch from the corresponding properties.
     *
     * @return A newly created Scheduler ("newParallel", "newBoundedElastic") or a default one ("parallel").
     */
    public Scheduler buildScheduler() {

        final Scheduler ret;
        if ("newParallel".equalsIgnoreCase(schedulerType)) {
            ret = Schedulers.newParallel("newParallelConsumer", concurrency, false);
        } else if ("newBoundedElastic".equalsIgnoreCase(schedulerType)) {
            // number of queued task is 32 * threads
            ret = Schedulers.newBoundedElastic(concurrency, concurrency * 32, "newElasticConsumer");
        } else {
            ret = Schedulers.parallel();
        }
        LOGGER.info("Using {} as scheduler for Kafka consumer", ret);
        return ret;
    }
}
