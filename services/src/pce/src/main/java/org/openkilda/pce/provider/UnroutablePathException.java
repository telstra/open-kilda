package org.openkilda.pce.provider;

import org.openkilda.messaging.model.Flow;

public class UnroutablePathException extends Exception {
    private final Flow flow;

    public UnroutablePathException(Flow flow) {
        super(String.format(
                "Can't make flow from %s to %s (bandwidth=%d%s)",
                flow.getSourceSwitch(), flow.getDestinationSwitch(), flow.getBandwidth(),
                flow.isIgnoreBandwidth() ? " ignored" : ""));

        this.flow = flow;
    }

    public Flow getFlow() {
        return flow;
    }
}
