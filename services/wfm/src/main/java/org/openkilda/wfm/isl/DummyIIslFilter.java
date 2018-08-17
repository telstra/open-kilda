package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.SwitchId;

import java.util.HashSet;
import java.util.Set;

public class DummyIIslFilter implements IIslFilter {
    private final Set<DiscoveryLink> matchSet;

    public DummyIIslFilter() {
        this.matchSet = new HashSet<>();
    }

    public void add(SwitchId switchId, int portId) {
        DiscoveryLink match = new DiscoveryLink(switchId, portId, 1, DiscoveryLink.ENDLESS_ATTEMPTS);
        matchSet.add(match);
    }

    public void clear() {
        matchSet.clear();
    }

    @Override
    public boolean isMatch(DiscoveryLink subject) {
        return matchSet.contains(subject);
    }

    public Set<DiscoveryLink> getMatchSet() {
        return matchSet;
    }
}
