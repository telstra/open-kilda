package org.bitbucket.openkilda.tools.mininet;

import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;

public interface IMininetSwitch {
  public IMininetSwitch build();
  public IMininetSwitch setName(String name);
  public IMininetSwitch setDPID(DatapathId dpid);
  
  public String name();
  public DatapathId dpid();
  public List<IMininetInterface> interfaces();
}
