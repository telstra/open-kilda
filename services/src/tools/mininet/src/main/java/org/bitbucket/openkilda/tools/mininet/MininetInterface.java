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

import com.fasterxml.jackson.annotation.JsonProperty;

public class MininetInterface {
  @JsonProperty("status")
  private String status;
  @JsonProperty("mac")
  private String mac;
  @JsonProperty("name")
  private String name;

  public MininetInterface() {
    // Needed for Jackson
  }

  /**
   * Instantiates a new MininetInterface.
   *
   * @param status the status
   * @param mac the mac
   * @param name the name
   */
  public MininetInterface(String status, String mac, String name) {
    this.status = status;
    this.mac = mac;
    this.name = name;
  }

  @JsonProperty("status")
  public String getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(String status) {
    this.status = status;
  }

  @JsonProperty("mac")
  public String getMac() {
    return mac;
  }

  @JsonProperty("mac")
  public void setMac(String mac) {
    this.mac = mac;
  }

  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }
}
