package org.bitbucket.openkilda.floodlight;

import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;

public class OFFactoryMock extends OFFactoryVer13 {
    @Override
    public long nextXid() {
        return 0;
    }
}
