package org.openkilda.messaging.ctrl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaBreakTrigger {
    private static final Logger logger = LoggerFactory.getLogger(KafkaBreakTrigger.class);

    private final KafkaBreakTarget target;
    private boolean communicationEnabled = true;

    public KafkaBreakTrigger(KafkaBreakTarget target) {
        this.target = target;
    }

    public boolean handle(String target, String action) {
        if (target == null) {
            return false;
        }

        try {
            if (KafkaBreakTarget.valueOf(target) == this.target) {
                boolean origin = communicationEnabled;
                communicationEnabled = KafkaBreakerAction.valueOf(action) == KafkaBreakerAction.RESTORE;
                logger.debug("allow communication {} (was {})", communicationEnabled, origin);

                return true;
            }
        } catch (IllegalArgumentException e) { }

        return false;
    }

    public boolean isCommunicationEnabled() {
        return communicationEnabled;
    }
}
