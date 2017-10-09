/* Copyright 2017 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.tools.mininet;

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
