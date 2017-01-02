package org.bitbucket.openkilda.tools.mininet;

<<<<<<< HEAD
=======
import org.projectfloodlight.openflow.types.IPv4Address;
>>>>>>> fd6adbbfcc4a5259b29e023345e9ed932566fa71
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.TransportPort;

/**
 * The Interface IMininetController.
 */
public interface IMininetController {
  
  /**
   * Sets the IP.
   *
   * @param ip the ip address
   * @return the IMininetController
   */
<<<<<<< HEAD
  public IMininetController setIP(String ip);
=======
  public IMininetController setIP(IPv4Address ip);
>>>>>>> fd6adbbfcc4a5259b29e023345e9ed932566fa71
  
  /**
   * Sets the port.
   *
   * @param port the port
   * @return the IMininetController
   */
  public IMininetController setPort(TransportPort port);
  
  /**
   * Sets the version.
   *
   * @param version the version
   * @return the IMininetController
   */
  public IMininetController setVersion(OFVersion version);
  
  /**
   * Builds the.
   *
   * @return the IMininetController
   */
  public IMininetController build();
  
  /**
   * Sets the name.
   *
   * @param name the name
   * @return the IMininetController
   */
  public IMininetController setName(String name);
  
  /**
   * Gets the ip.
   *
   * @return the ip
   */
  public String getIP();
  
  /**
   * Gets the port.
   *
   * @return the port
   */
  public Integer getPort();
  
  /**
   * Gets the openflow version.
   *
   * @return the openflow version
   */
  public String getOfVersion();
  
  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName();
}
