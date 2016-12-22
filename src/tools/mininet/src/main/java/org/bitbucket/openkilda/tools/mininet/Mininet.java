package org.bitbucket.openkilda.tools.mininet;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

public class Mininet implements IMininet{

  @Override
  public IMininet build() {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addSwitch(IMininetSwitch sw) {
    // TODO Auto-generated method stub
    return this;
  }
  
  @Override
  public IMininet addSwitch(String name, DatapathId dpid) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addLink(String nodea, String nodeb) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addLink(IMininetLink link) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addController(IMininetController controller) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addMininetServer(IPv4Address ipAddress, TransportPort port) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public IMininet addMininetServer(String hostname, int port) {
    // TODO Auto-generated method stub
    return this;
  }

  @Override
  public List<IMininetSwitch> switches() {
    List<IMininetSwitch> switches = new ArrayList<>();
    return switches;
  }

  @Override
  public List<IMininetLink> links() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<IMininetController> controllers() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public IMininetSwitch getSwitch(String name) {
    // TODO Auto-generated method stub
    return null;
  }
}
