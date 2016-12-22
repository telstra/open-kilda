package org.bitbucket.openkilda.tools.mininet;

import java.util.Map;

public interface IMininetLink {
  public IMininetLink setNodes(Map<IMininetSwitch, IMininetSwitch> nodes);
  public IMininetLink build();
  
  public String getName();
  public boolean isUp();
  public String getStatus();
}
