package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryNode;

public interface IIslFilter {
    boolean isMatch(DiscoveryNode subject);
}
