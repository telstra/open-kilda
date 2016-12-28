package org.bitbucket.openkilda.tools.mininet;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "links"
})
public class MininetLinks {
  @JsonProperty("links")
  private List<MininetLink> links = null;
  
  public MininetLinks() {
    links = new ArrayList<MininetLink>();
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
  
  public MininetLinks addLink(String node1, String node2) {
    MininetLink link = new MininetLink(node1, node2);
    links.add(link);
    return this;
  }
}
