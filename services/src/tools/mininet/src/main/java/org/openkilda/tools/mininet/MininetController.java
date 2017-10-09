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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name",
    "ip",
    "port",
    "of-version"
    })

public class MininetController implements IMininetController {
  @JsonProperty("host")
  private String host;
  @JsonProperty("port")
  private Integer port;
  @JsonProperty("of-version")
  private String ofVersion;
  @JsonProperty("name")
  private String name;
  
  
  @Override
  @JsonProperty("host")
  public IMininetController setIP(String host) {
    this.host = host;
    return this;
  }
  
  @Override
  @JsonProperty("host")
  public String getIP() {
    return host;
  }

  @Override
  @JsonProperty("port")
  public IMininetController setPort(TransportPort port) {
    this.port = port.getPort();
    return this;
  }

  @Override
  @JsonProperty("port")
  public Integer getPort() {
    return port;
  }
  
  @Override
  @JsonProperty("of-version")
  public IMininetController setVersion(OFVersion version) {
    this.ofVersion = version.toString();
    return this;
  }
  
  @Override
  @JsonProperty("of-version")
  public String getOfVersion() {
    return ofVersion;
  }
  
  @Override
  @JsonProperty("name")
  public IMininetController setName(String name) {
    this.name = name;
    return this;
  }
  
  @Override
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @Override
  public IMininetController build() {
    return this;
  }

}
