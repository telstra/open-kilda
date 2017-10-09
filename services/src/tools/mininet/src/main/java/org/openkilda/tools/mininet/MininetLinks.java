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

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "links"
    })

public class MininetLinks {
  @JsonProperty("links")
  private List<MininetLink> links = null;
  
  public MininetLinks() {
    links = new ArrayList<>();
  }
  
  public MininetLinks(List<MininetLink> links) {
    this.links = links;
  }
  
  @JsonProperty("links")
  public List<MininetLink> getLinks() {
    return links;
  }
  
  @JsonProperty("links")
  public MininetLinks setLinks(List<MininetLink> links) {
    this.links = links;
    return this;
  }
  
  /**
   * Add link.
   *
   * @param node1 the node 1
   * @param node2 the node 2
   * @return the MininetLinks
   */
  public MininetLinks addLink(String node1, String node2) {
    MininetLink link = new MininetLink(node1, node2);
    links.add(link);
    return this;
  }
}
