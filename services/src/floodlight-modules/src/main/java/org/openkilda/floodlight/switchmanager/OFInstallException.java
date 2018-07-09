package org.openkilda.floodlight.switchmanager;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.types.DatapathId;

public class OFInstallException extends SwitchOperationException {
    private final OFMessage ofMessage;

    public OFInstallException(DatapathId dpId, OFMessage ofMessage) {
        super(dpId, String.format("Error during install OFRule into switch \"%s\"", dpId));
        this.ofMessage = ofMessage;
    }

    public OFMessage getOfMessage() {
        return ofMessage;
    }
}
