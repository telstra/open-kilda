package org.bitbucket.openkilda.tools.mininet;

import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

public interface IMininet {
  public IMininet build();
  public IMininet addSwitch(IMininetSwitch sw);
  public IMininet addSwitch(String name, DatapathId dpid);
  public IMininet addLink(IMininetLink link);
  public IMininet addLink(String nodea, String nodeb);
  public IMininet addController(IMininetController controller);
  public IMininet addMininetServer(IPv4Address ipAddress, TransportPort port);
  public IMininet addMininetServer(String hostname, int port);
  
  public List<IMininetSwitch> switches();
  public List<IMininetLink> links();
  public List<IMininetController> controllers();
  
  public IMininetSwitch getSwitch(String name);
}
