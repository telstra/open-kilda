package org.bitbucket.openkilda.tools.mininet;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;

/**
 * The Interface IMininetSwitch.
 */
public interface IMininetSwitch {

  /**
   * Gets the name.
   *
   * @return the name
   */
  String getName();

  /**
   * Sets the name.
   *
   * @param name the new name
   */
  void setName(String name);

  /**
   * Gets the dpid.
   *
   * @return the dpid
   */
  String getDpid();

  /**
   * Sets the dpid.
   *
   * @param dpid the new dpid
   */
  void setDpid(String dpid);

  /**
   * Gets the interface.
   *
   * @return the interface
   */
  List<MininetInterface> getInterfaces();

  /**
   * Sets the interface.
   *
   * @param interfaces the new interface
   */
  void setInterfaces(List<MininetInterface> interfaces);

  /**
   * Gets the connected.
   *
   * @return the connected
   */
  Boolean getConnected();

  /**
   * Sets the connected.
   *
   * @param connected the new connected
   */
  void setConnected(Boolean connected);

  /**
   * Adds the interface.
   *
   * @param intf the intf
   */
  void addInterface(MininetInterface intf);

  /**
   * To json.
   *
   * @return the string
   * @throws JsonProcessingException the json processing exception
   */
  String toJson() throws JsonProcessingException;

}
