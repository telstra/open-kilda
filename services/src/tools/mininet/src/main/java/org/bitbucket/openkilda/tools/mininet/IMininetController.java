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

package org.bitbucket.openkilda.tools.mininet;

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
  public IMininetController setIP(String ip);
  
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
