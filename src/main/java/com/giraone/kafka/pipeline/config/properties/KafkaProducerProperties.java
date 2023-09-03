package com.giraone.kafka.pipeline.config.properties;

import lombok.Generated;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@NoArgsConstructor
@ToString
// exclude from test coverage
@Generated
public class KafkaProducerProperties {
    /**
     * all = quorum (default), 1 = leader only, 0 = no ack
     */
    private String acks = "all";
    /**
     * For more performance increase to 100000–200000. Default = 16384.
     */
    private int batchSize = 16384;
}
