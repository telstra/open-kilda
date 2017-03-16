package org.bitbucket.openkilda.floodlight.pathverification;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IPathVerificationService extends IFloodlightService {
  
  public boolean isAlive();

  public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port);
  
  public boolean sendDiscoveryMessage(DatapathId srcSwId, OFPort port, DatapathId dstSwId);
  
  public void installVerificationRule(DatapathId switchId, boolean isBroadcast);

  public void installVerificationRules(DatapathId switchId);

}
