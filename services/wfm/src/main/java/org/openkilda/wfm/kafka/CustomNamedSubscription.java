package org.openkilda.wfm.kafka;

import org.apache.commons.lang.StringUtils;
import org.apache.storm.kafka.spout.NamedSubscription;

import java.util.Collection;

/**
 * Custom implementation of storm's {@link NamedSubscription} with fix of incorrect topic names.
 */
public class CustomNamedSubscription extends NamedSubscription {

    public CustomNamedSubscription(Collection<String> topics) {
        super(topics);
    }

    public CustomNamedSubscription(String... topics) {
        super(topics);
    }

    @Override
    public String getTopicsString() {
        return StringUtils.join(this.topics, ",");
    }
}
