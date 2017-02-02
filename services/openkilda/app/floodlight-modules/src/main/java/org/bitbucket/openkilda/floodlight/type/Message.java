package org.bitbucket.openkilda.floodlight.type;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "message-type",
  "timestamp",
  "controller",
  "data"
})
public class Message implements Serializable
{

  @JsonProperty("message-type")
  private MessageType messageType;
  @JsonProperty("timestamp")
  private Long timestamp;
  @JsonProperty("controller")
  private String controller;
  @JsonProperty("data")
  private MessageData data;
  @JsonIgnore
  private Map<String, Object> additionalProperties = new HashMap<String, Object>();
  private final static long serialVersionUID = -9111954211363472165L;
  private ObjectMapper mapper;

  /**
   * No args constructor for use in serialization
   * 
   */
  public Message() {
    mapper = new ObjectMapper();
  }

  /**
   * 
   * @param timestamp
   * @param data
   * @param controller
   * @param messageType
   */
  public Message(MessageType messageType, Long timestamp, String controller, MessageData data) {
    super();
    this.messageType = messageType;
    this.timestamp = timestamp;
    this.controller = controller;
    this.data = data;
  }

  @JsonProperty("message-type")
  public MessageType getMessageType() {
    return messageType;
  }

  @JsonProperty("message-type")
  public void setMessageType(MessageType messageType) {
    this.messageType = messageType;
  }

  public Message withMessageType(MessageType messageType) {
    this.messageType = messageType;
    return this;
  }

  @JsonProperty("timestamp")
  public Long getTimestamp() {
    return timestamp;
  }

  @JsonProperty("timestamp")
  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public Message withTimestamp(Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  @JsonProperty("controller")
  public String getController() {
    return controller;
  }

  @JsonProperty("controller")
  public void setController(String controller) {
    this.controller = controller;
  }

  public Message withController(String controller) {
    this.controller = controller;
    return this;
  }

  @JsonProperty("data")
  public MessageData getData() {
    return data;
  }

  @JsonProperty("data")
  public void setData(MessageData data) {
    this.data = data;
  }

  public Message withData(MessageData data) {
    this.data = data;
    return this;
  }

  @Override
  public String toString() {
    return ToStringBuilder.reflectionToString(this);
  }

  @JsonAnyGetter
  public Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }

  @JsonAnySetter
  public void setAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
  }

  public Message withAdditionalProperty(String name, Object value) {
    this.additionalProperties.put(name, value);
    return this;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(messageType).append(timestamp).
        append(controller).append(data).append(additionalProperties).toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof Message) == false) {
      return false;
    }
    Message rhs = (Message) other;
    return new EqualsBuilder().append(messageType, rhs.messageType).
        append(timestamp, rhs.timestamp).append(controller, rhs.controller).
        append(data, rhs.data).append(additionalProperties, rhs.additionalProperties).isEquals();
  }
  
  public String toJson() throws JsonProcessingException {
    return mapper.writeValueAsString(this);
  }

}
