package org.bitbucket.openkilda.floodlight;

import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;

/**
 * Created by atopilin on 12/04/2017.
 */
public class OFFactoryMock extends OFFactoryVer13 {
    @Override
    public long nextXid() {
        return 0;
    }
}
