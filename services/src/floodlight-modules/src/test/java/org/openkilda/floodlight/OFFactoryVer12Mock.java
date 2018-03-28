package org.openkilda.floodlight;

import org.projectfloodlight.openflow.protocol.XidGenerator;
import org.projectfloodlight.openflow.protocol.XidGenerators;
import org.projectfloodlight.openflow.protocol.ver12.OFFactoryVer12;

public class OFFactoryVer12Mock extends OFFactoryVer12 {
    private final XidGenerator xidGenerator = XidGenerators.create();

    @Override
    public long nextXid() {
        return xidGenerator.nextXid();
    }
}
