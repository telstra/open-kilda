package org.bitbucket.openkilda.tools.mininet;

import org.projectfloodlight.openflow.types.MacAddress;

public interface IMininetInterface {
  public String getName();
  public MacAddress getMac();
  public boolean isUp();
  public String getStatus();
}
