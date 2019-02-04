package org.openkilda;

import lombok.Value;

import java.util.HashMap;

public class DecisionMakerService {

    private final IDecisionMakerCarrier carrier;
    private final int failTimeout;
    private final int awaitTime;
    private HashMap<Endpoint, Long> lastDiscovery = new HashMap<>();

    public DecisionMakerService(IDecisionMakerCarrier carrier, int failTimeout, int awaitTime) {
        this.carrier = carrier;
        this.failTimeout = failTimeout;
        this.awaitTime = awaitTime;
    }

    void discovered(SwitchId switchId, int portNo, SwitchId endSwitchId, int endPortNo, long currentTime) {
        carrier.discovered(switchId, portNo, endSwitchId, endPortNo, currentTime);
        lastDiscovery.put(Endpoint.of(switchId, portNo), currentTime);
    }

    void failed(SwitchId switchId, int portNo, long currentTime) {
        Endpoint endpoint = Endpoint.of(switchId, portNo);
        if (!lastDiscovery.containsKey(endpoint)) {
            lastDiscovery.put(endpoint, currentTime - awaitTime);
        }

        long timeWindow = lastDiscovery.get(endpoint) + failTimeout;

        if (currentTime >= timeWindow) {
            carrier.failed(switchId, portNo, currentTime);
        }
    }

    public HashMap<Endpoint, Long> getLastDiscovery() {
        return lastDiscovery;
    }

    public static @Value(staticConstructor = "of")
    class Endpoint {
        private final SwitchId switchId;
        private final int port;
    }
}
