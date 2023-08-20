package com.giraone.kafka.pipeline.service;

import com.giraone.kafka.pipeline.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

public abstract class AbstractService implements CommandLineRunner {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractService.class);

    protected final ApplicationProperties applicationProperties;
    protected final CounterService counterService;

    protected final String topicInput;
    protected final String topicOutput;

    public AbstractService(ApplicationProperties applicationProperties,
                           CounterService counterService) {
        this.applicationProperties = applicationProperties;
        this.counterService = counterService;
        this.topicInput = applicationProperties.getTopicInput();
        this.topicOutput = applicationProperties.getTopicOutput();
    }

    protected abstract void start();

    @Override
    public void run(String... args) {

        if (!(applicationProperties.getMode() + "Service").equalsIgnoreCase(this.getClass().getSimpleName())) {
            return;
        }
        LOGGER.info("STARTING {}", this.getClass().getSimpleName());
        this.start();
    }
}
