package com.giraone.kafka.pipeline.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.internals.ConsumerFactory;
import reactor.kafka.sender.SenderOptions;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Base class for Kafka integration tested.
 * This is basically a simplified copy from the reactor-kafka code of
 * <a href="https://github.com/reactor/reactor-kafka/blob/main/src/test/java/reactor/kafka/AbstractKafkaTest.java">AbstractKafkaTest.java</a>.
 * The main differences are
 * <ul>
 *     <li>Message keys are String not Integer</li>
 * </ul>
 */
public abstract class AbstractKafkaIntTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractKafkaIntTest.class);

    private static final int DEFAULT_TEST_TIMEOUT_MS = 20_000;

    protected static final KafkaContainer KAFKA = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.4.1"))
        // DockerImageName.parse("confluentinc/cp-enterprise-kafka:7.4.1")
        //    .asCompatibleSubstituteFor("confluentinc/cp-kafka"))
        .withNetwork(null)
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1")
        .withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1")
        .withReuse(false);

    protected int partitions = 2;
    protected long receiveTimeoutMillis = DEFAULT_TEST_TIMEOUT_MS;
    protected long requestTimeoutMillis = 3000;
    protected long sessionTimeoutMillis = 12000;
    protected long heartbeatIntervalMillis = 3000;

    protected ReceiverOptions<String, String> receiverOptions;
    protected SenderOptions<String, String> senderOptions;

    protected final List<List<String>> receivedMessagesPerPartition = new ArrayList<>(partitions);
    protected final List<List<ConsumerRecord<String, String>>> receivedRecordsPerPartition = new ArrayList<>(partitions);

    protected abstract String getClientId();

    @BeforeEach
    public final void setUpAbstractKafkaTest() {
        waitForContainerStart();
        senderOptions = SenderOptions.create(producerProps(getClientId()));
        receiverOptions = createReceiverOptions(this.getClass().getSimpleName(), getClientId());
        resetMessages();
    }

    @DynamicPropertySource
    protected static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", () -> {
            waitForContainerStart();
            return KAFKA.getBootstrapServers();
        });
    }

    protected Map<String, Object> producerProps(String clientId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(requestTimeoutMillis));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "producer-tx-1");
        return props;
    }

    protected Map<String, Object> consumerProps(String groupId, String clientId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, String.valueOf(sessionTimeoutMillis));
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, String.valueOf(heartbeatIntervalMillis));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return props;
    }

    protected ReceiverOptions<String, String> createReceiverOptions(String groupId, String clientId) {
        Map<String, Object> props = consumerProps(groupId, clientId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "2");
        receiverOptions = ReceiverOptions.create(props);
        receiverOptions.commitInterval(Duration.ofMillis(50));
        receiverOptions.maxCommitAttempts(1);
        receiverOptions.addAssignListener(assignments -> LOGGER.info("Assigned: " + assignments));
        return receiverOptions;
    }

    protected Consumer<String, String> createConsumer(String topic, String groupId) {
        Map<String, Object> consumerProps = consumerProps(groupId, getClientId());
        Consumer<String, String> consumer = ConsumerFactory.INSTANCE.createConsumer(ReceiverOptions.create(consumerProps));
        consumer.subscribe(Collections.singletonList(topic));
        consumer.poll(Duration.ofMillis(requestTimeoutMillis));
        return consumer;
    }

    protected Consumer<String, String> createConsumer(String topic) {
        final String groupId = this.getClass().getSimpleName() + "-" + System.nanoTime();
        return createConsumer(topic, groupId);
    }

    protected void createNewTopic(String topic) {

        try (
            AdminClient adminClient = KafkaAdminClient.create(
                Collections.singletonMap(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers())
            )
        ) {
            adminClient.createTopics(List.of(new NewTopic(topic, partitions, (short) 1)))
                .all()
                .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            if (e.getCause() != null && e.getCause() instanceof TopicExistsException) {
                LOGGER.warn(e.getMessage() + " " + e.getClass());
            } else {
                throw new RuntimeException(e);
            }
        }
        waitForTopic(topic, true);
    }

    protected void resetMessages() {
        receivedMessagesPerPartition.clear();
        receivedRecordsPerPartition.clear();
        for (int i = 0; i < partitions; i++) {
            receivedMessagesPerPartition.add(new ArrayList<>());
        }
        for (int i = 0; i < partitions; i++) {
            this.receivedRecordsPerPartition.add(new ArrayList<>());
        }
    }

    protected void waitForTopic(String topic, boolean resetMessages) {
        waitForTopic(topic);
        if (resetMessages) {
            resetMessages();
        }
    }

    protected void onReceive(ConsumerRecord<String, String> record) {
        receivedMessagesPerPartition.get(record.partition()).add(record.key());
        receivedRecordsPerPartition.get(record.partition()).add(record);
    }

    protected void waitForMessages(Consumer<String, String> consumer, Integer expectedCount) {

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Wait for {} message(s) in \"{}\".", expectedCount == null ? "any number of" : expectedCount, consumer.assignment());
        }
        int readAsMany = expectedCount != null ? expectedCount : 1_000;
        int receivedCount = 0;
        long endTimeMillis = System.currentTimeMillis() + receiveTimeoutMillis;
        while (receivedCount < readAsMany && System.currentTimeMillis() < endTimeMillis) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
            records.forEach(this::onReceive);
            receivedCount += records.count();
        }

        if (expectedCount != null) {
            assertThat(receivedCount).isEqualTo(expectedCount);
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            assertThat(records.isEmpty()).isTrue();
        }
    }

    protected static String bootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    protected static void waitForBrokers() {
        int maxRetries = 50;
        for (int i = 0; i < maxRetries; i++) {
            try {
                bootstrapServers();
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    protected static void waitForContainerStart() {

        if (!KAFKA.isCreated() || !KAFKA.isRunning()) {
            LOGGER.info("STARTING Kafka broker");
            KAFKA.start();
            LOGGER.info("STARTED Kafka broker {}", KAFKA.getBootstrapServers());
        }
    }

    protected void send(String topic, Tuple2<String, String> message) {
        try (ReactiveKafkaProducerTemplate<String, String> template = new ReactiveKafkaProducerTemplate<>(senderOptions)) {
            template.send(topic, message.getT1(), message.getT2())
                .doOnSuccess(senderResult -> LOGGER.info("Sent message with key={} to topic {} with offset : {}",
                    message.getT1(), topic, senderResult.recordMetadata().offset()))
                .block();
        }
    }

    protected void sendMessagesAndAssertReceived(String topic, Consumer<String, String> consumer,
                                                 List<Tuple2<String, String>> messagesToSend,
                                                 List<Tuple2<String, String>> expectedMessages) throws Exception {

        for (Tuple2<String, String> message : messagesToSend) {
            try (ReactiveKafkaProducerTemplate<String, String> template = new ReactiveKafkaProducerTemplate<>(senderOptions)) {
                template.send(topic, message.getT1(), message.getT2())
                    .doOnSuccess(senderResult -> LOGGER.info("Sent message with key={} to topic {} with offset : {}",
                        message.getT1(), topic, senderResult.recordMetadata().offset()))
                    .block();
            }
        }
        // We have to wait some time. We use at least the producer request timeout.
        Thread.sleep(requestTimeoutMillis);

        assertReceived(consumer, expectedMessages);
    }

    protected void assertReceived(Consumer<String, String> consumer,
                                  List<Tuple2<String, String>> expectedMessages) {

        waitForMessages(consumer, expectedMessages.size());
        final AtomicInteger i = new AtomicInteger();
        final AtomicInteger partition = new AtomicInteger();
        receivedRecordsPerPartition.forEach(recordListOfPartition -> {
            recordListOfPartition.forEach(record -> {
                LOGGER.info("Partition={}: {} -> {}", partition.getAndIncrement(), record.key(), record.value());
                String expectedKey = expectedMessages.get(i.get()).getT1();
                String expectedBody = expectedMessages.get(i.getAndIncrement()).getT2();
                assertThat(record.key()).isEqualTo(expectedKey);
                assertThat(record.value()).isEqualTo(expectedBody);
            });
        });
    }

    protected List<ConsumerRecord<String, String>> getAllConsumerRecords() {

        return receivedRecordsPerPartition.stream().reduce((a,l) -> {
            a.addAll(l);
            return a;
        }).orElse(Collections.emptyList());
    }

    //------------------------------------------------------------------------------------------------------------------


    private void waitForTopic(String topic) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 1000);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            int maxRetries = 10;
            boolean done = false;
            for (int i = 0; i < maxRetries && !done; i++) {
                List<PartitionInfo> partitionInfo = producer.partitionsFor(topic);
                done = !partitionInfo.isEmpty();
                for (PartitionInfo info : partitionInfo) {
                    if (info.leader() == null || info.leader().id() < 0)
                        done = false;
                }
            }
            assertTrue("Timed out waiting for topic", done);
        }
    }
}
