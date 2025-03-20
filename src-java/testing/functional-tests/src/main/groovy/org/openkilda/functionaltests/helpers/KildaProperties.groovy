package org.openkilda.functionaltests.helpers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class KildaProperties {

    public static int DISCOVERY_EXHAUSTED_INTERVAL
    public static int ANTIFLAP_MIN
    public static int ANTIFLAP_COOLDOWN
    public static int DISCOVERY_TIMEOUT
    public static double BURST_COEFFICIENT
    public static String TOPO_DISCO_TOPIC
    public static Properties PRODUCER_PROPS

    @Autowired
    KildaProperties( @Value('${discovery.exhausted.interval}') int discoveryExhaustedInterval,
                     @Value('${antiflap.min}') int antiflapMin,
                     @Value('${antiflap.cooldown}') int antiflapCooldown,
                     @Value('${discovery.timeout}') int discoveryTimeout,
                     @Value('${burst.coefficient}') double burstCoefficient,
                     @Autowired @Qualifier("kafkaProducerProperties") Properties producerProps,
                     @Value("#{kafkaTopicsConfig.getTopoDiscoTopic()}") String topoDiscoTopic) {

        DISCOVERY_EXHAUSTED_INTERVAL = discoveryExhaustedInterval
        ANTIFLAP_MIN = antiflapMin
        ANTIFLAP_COOLDOWN = antiflapCooldown
        DISCOVERY_TIMEOUT = discoveryTimeout
        BURST_COEFFICIENT = burstCoefficient
        TOPO_DISCO_TOPIC = topoDiscoTopic
        PRODUCER_PROPS = producerProps

    }
}
