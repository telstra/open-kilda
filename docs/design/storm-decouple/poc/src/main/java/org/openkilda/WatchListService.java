package org.openkilda;

import lombok.Value;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class WatchListService {

    private final IWatchListServiceCarrier carrier;
    private final int tickPeriodMs;

    private Set<Endpoint> endpoints = new HashSet<>();
    private SortedMap<Long, Set<Endpoint>> timeouts = new TreeMap<>();

    public WatchListService(IWatchListServiceCarrier carrier, int tickPeriodMs) {
        this.carrier = carrier;
        this.tickPeriodMs = tickPeriodMs;
    }

    public Set<Endpoint> getEndpoints() {
        return endpoints;
    }

    public SortedMap<Long, Set<Endpoint>> getTimeouts() {
        return timeouts;
    }

    public void addWatch(SwitchId switchId, int portNo, long currentTime) {
        Endpoint endpoint = Endpoint.of(switchId, portNo);
        if (endpoints.add(endpoint)) {
            carrier.discoveryRequest(switchId, portNo, currentTime);
            timeouts.computeIfAbsent(currentTime + tickPeriodMs, mappingFunction -> new HashSet<>())
                    .add(endpoint);
        }
    }

    public void addWatch(SwitchId switchId, int portNo) {
        addWatch(switchId, portNo, System.currentTimeMillis());
    }

    public void removeWatch(SwitchId switchId, int portNo, long currentTime) {
        carrier.watchRemoved(switchId, portNo, currentTime);
        endpoints.remove(Endpoint.of(switchId, portNo));
    }

    public void tick(long tickTime) {
        // TODO: move to stream processing
        SortedMap<Long, Set<Endpoint>> range = timeouts.subMap(0L, tickTime + 1);
        if (!range.isEmpty()) {
            HashSet<Endpoint> newSet = new HashSet<>();
            for (Set<Endpoint> e : range.values()) {
                for (Endpoint ee : e) {
                    if (endpoints.contains(ee)) {
                        carrier.discoveryRequest(ee.switchId, ee.port, tickTime);
                        newSet.add(ee);
                    }
                }
            }
            range.clear();
            if (!newSet.isEmpty()) {
                timeouts.computeIfAbsent(tickTime + tickPeriodMs, mappingFunction -> new HashSet<>())
                        .addAll(newSet);
            }
        }
    }

    public static @Value(staticConstructor = "of")
    class Endpoint {
        private final SwitchId switchId;
        private final int port;
    }
}
