package org.bitbucket.openkilda.floodlight.pathverification.type;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.types.OFPort;

public class SwitchPortTuple {
    private IOFSwitch sw;
    private OFPort port;

    public IOFSwitch getSwitch() {
        return sw;
    }

    public SwitchPortTuple setSwitch(IOFSwitch dpid) {
        this.sw = dpid;
        return this;
    }

    public OFPort getPort() {
        return port;
    }

    public SwitchPortTuple setPort(OFPort port) {
        this.port = port;
        return this;
    }
}
