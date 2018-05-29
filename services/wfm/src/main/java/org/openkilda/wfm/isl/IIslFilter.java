package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryLink;

public interface IIslFilter {
    boolean isMatch(DiscoveryLink subject);
}
