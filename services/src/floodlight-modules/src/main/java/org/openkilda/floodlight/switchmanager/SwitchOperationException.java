package org.openkilda.floodlight.switchmanager;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchOperationException extends Exception {
    private final DatapathId dpId;

    public SwitchOperationException(DatapathId dpId) {
        this(dpId, "Switch manipulation has failed");
    }

    public SwitchOperationException(DatapathId dpId, String s) {
        super(s);
        this.dpId = dpId;
    }

    public DatapathId getDpId() {
        return dpId;
    }
}
