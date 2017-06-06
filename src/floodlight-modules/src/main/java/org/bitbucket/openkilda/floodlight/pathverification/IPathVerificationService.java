package org.bitbucket.openkilda.floodlight.pathverification;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public interface IPathVerificationService extends IFloodlightService {

    public boolean isAlive();

    public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port);

    public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port, DatapathId dstSwId);

}
