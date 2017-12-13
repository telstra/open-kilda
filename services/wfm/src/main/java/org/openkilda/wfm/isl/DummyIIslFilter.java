package org.openkilda.wfm.isl;

import java.util.HashSet;
import java.util.Set;

public class DummyIIslFilter implements IIslFilter {
    private final Set<DiscoveryNode> matchSet;

    public DummyIIslFilter() {
        this.matchSet = new HashSet<>();
    }

    public void add(String switchId, String portId) {
        DiscoveryNode match = new DiscoveryNode(switchId, portId);
        matchSet.add(match);
    }

    public void clear() {
        matchSet.clear();
    }

    @Override
    public boolean isMatch(DiscoveryNode subject) {
        return matchSet.contains(subject);
    }

    public Set<DiscoveryNode> getMatchSet() {
        return matchSet;
    }
}
