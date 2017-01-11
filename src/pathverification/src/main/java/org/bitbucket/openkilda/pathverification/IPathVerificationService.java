package org.bitbucket.openkilda.pathverification;

import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import net.floodlightcontroller.core.IOFSwitch;

public interface IPathVerificationService {

  public OFPacketOut generateVerificationPacket(IOFSwitch sw, OFPort port);

  public void installVerificationRule(DatapathId switchId, boolean isBroadcast);
  
  public Match buildVerificationMatch(IOFSwitch sw, boolean isBroadcast);
  
  public List<OFAction> buildSendToControllerAction(IOFSwitch sw);
  
  public OFFlowMod buildFlowMod(IOFSwitch sw, Match match, List<OFAction> actionList);

}
