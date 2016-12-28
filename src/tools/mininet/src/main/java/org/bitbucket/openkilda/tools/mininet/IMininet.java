package org.bitbucket.openkilda.tools.mininet;

import java.net.URISyntaxException;
import java.net.UnknownHostException;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

public interface IMininet {

  IMininet build() throws MininetException, URISyntaxException;

  IMininet addMininetServer(IPv4Address ipAddress, TransportPort port);

  IMininet addMininetServer(String hostname, int port) throws UnknownHostException;

  IMininet addController(IMininetController controller);

  IMininet addSwitch(String name, DatapathId dpid);

  IMininet addLink(String nodeA, String nodeB);

  IMininet clear();

  MininetSwitches switches();

  MininetLinks links();

  boolean isConnect();

}
