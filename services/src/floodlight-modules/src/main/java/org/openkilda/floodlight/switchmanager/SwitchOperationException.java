package org.openkilda.floodlight.switchmanager;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchOperationException extends Exception {
    private final DatapathId dpId;

    public SwitchOperationException(DatapathId dpId) {
        this(dpId, "Switch manipulation has failed");
    }

    public SwitchOperationException(DatapathId dpId, String message) {
        super(message);
        this.dpId = dpId;
    }

    public DatapathId getDpId() {
        return dpId;
    }
}
