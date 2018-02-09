package org.openkilda.floodlight.kafka;

import org.openkilda.messaging.ctrl.KafkaBreakTrigger;
import org.openkilda.messaging.ctrl.KafkaBreakTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestAwareProducer extends Producer {
    private static final Logger logger = LoggerFactory.getLogger(TestAwareProducer.class);

    private KafkaBreakTrigger breakTrigger;

    public TestAwareProducer(Context context) {
        super(context);

        breakTrigger = new KafkaBreakTrigger(KafkaBreakTarget.FLOODLIGHT_PRODUCER);
    }

    @Override
    protected void send(String topic, String jsonPayload) {
        if (! breakTrigger.isCommunicationEnabled()) {
            logger.info("Suppress record : {} <= {}", topic, jsonPayload);
            return;
        }

        super.send(topic, jsonPayload);
    }

    public KafkaBreakTrigger getBreakTrigger() {
        return breakTrigger;
    }
}
