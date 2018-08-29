package org.openkilda.floodlight.switchmanager;

import org.projectfloodlight.openflow.types.DatapathId;

public class SwitchNotFoundException extends SwitchOperationException {
    public SwitchNotFoundException(DatapathId dpId) {
        super(dpId, String.format("Switch %s was not found", dpId));
    }
}
