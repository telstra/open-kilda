package org.bitbucket.openkilda.floodlight.message.command;

import java.io.Serializable;

import org.bitbucket.openkilda.floodlight.message.MessageData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "command",
  "destination"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY, 
              property = "command")

@JsonSubTypes({ 
  @Type(value = DefaultFlowsCommandData.class, name = "install_default_flows"), 
  @Type(value = DiscoverISLCommandData.class, name = "discover_isl"),
  @Type(value = DiscoverPathCommandData.class, name = "discover_path")
})

public abstract class CommandData extends MessageData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("destination")
  private Destination destination;
  @JsonProperty("command")
  private String command;
  
  public enum Destination {
    CONTROLLER,
    TOPOLOGY_ENGINE
  }
  
  @JsonProperty("destination")
  public Destination getDestination() {
    return destination;
  }
  
  @JsonProperty("destination")
  public void setDestination(Destination destination) {
    this.destination = destination;
  }
  
  public CommandData withDestination(Destination destination) {
    setDestination(destination);
    return this;
  }
  
  @JsonProperty("command")
  public String getCommand() {
    return command;
  }
}
