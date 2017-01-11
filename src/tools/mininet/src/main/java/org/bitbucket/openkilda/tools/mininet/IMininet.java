package org.bitbucket.openkilda.tools.mininet;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
/**
 * The Interface IMininet.
 * 
 * <p>Provides a wrapper for the Mininet REST API which is part of OpenKilda
 */

public interface IMininet {

  /**
   * Builds the mininet class.
   *
   * @return the IMininet
   * @throws MininetException the mininet exception
   * @throws URISyntaxException the URI syntax exception
   */
  IMininet build() throws MininetException, URISyntaxException;

  /**
   * Adds the mininet server.
   *
   * @param ipAddress the ip address
   * @param port the port
   * @return the IMininet
   */
  IMininet addMininetServer(IPv4Address ipAddress, TransportPort port);

  /**
   * Adds the mininet server.
   *
   * @param hostname the hostname
   * @param port the port
   * @return the IMininet
   * @throws UnknownHostException the unknown host exception
   */
  IMininet addMininetServer(String hostname, int port) throws UnknownHostException;

  /**
   * Adds the controller.
   *
   * @param controller the controller
   * @return the IMininet
   */
  IMininet addController(IMininetController controller);

  /**
   * Adds the switch.
   *
   * @param name the name
   * @param dpid the dpid
   * @return the IMininet
   */
  IMininet addSwitch(String name, DatapathId dpid);

  /**
   * Adds the link.
   *
   * @param nodeA the node A
   * @param nodeB the node B
   * @return the IMininet
   */
  IMininet addLink(String nodeA, String nodeB);

  /**
   * Clears the mininet configuration (removes switches, links, controllers).
   *
   * @return the IMininet
   */
  IMininet clear();

  /**
   * Get all switches.
   *
   * @return the MininetSwitches
   */
  MininetSwitches switches();
  
  /**
   * Get specific switch.
   * 
   * @return the MininetSwitch
   */
  IMininetSwitch getSwitch(String name);

  /**
   * Get all links.
   *
   * @return the MininetLinks
   */
  MininetLinks links();

  /**
   * Checks if connected to Mininet Server.
   *
   * @return true, if connected
   */
  boolean isConnect();

  boolean isSwitchConnected(String name);

}
