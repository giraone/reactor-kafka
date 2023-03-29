package com.giraone.kafka.pipeline.config;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

public class SchedulerConfig {

    public static final Scheduler scheduler = Schedulers.newParallel("parallel",
            Runtime.getRuntime().availableProcessors() - 2);
    // public static final Scheduler scheduler = Schedulers.parallel();
}
