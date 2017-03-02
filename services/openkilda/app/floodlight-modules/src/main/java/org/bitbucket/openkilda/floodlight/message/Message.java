package org.bitbucket.openkilda.floodlight.message;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "type",
  "timestamp",
  "data"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY, 
              property = "type")
@JsonSubTypes({ @Type(value = CommandMessage.class, name = "COMMAND"),
                @Type(value = InfoMessage.class, name = "INFO")})

public abstract class Message implements Serializable {

  private static final long serialVersionUID = 1L;
  @JsonProperty("type")
  private Type type;
  @JsonProperty("timestamp")
  private long timestamp;
//  @JsonProperty("data")
//  private MessageData data;
  private ObjectMapper mapper;
  
  public enum Type {
    COMMAND,
    INFO
  }
  
  public Message() {
    mapper = new ObjectMapper();
  }

  @JsonProperty("type")
  public Type getType() {
    return type;
  }
  
  @JsonProperty("timestamp")
  public long getTimestamp() {
    return timestamp;
  }
  
  @JsonProperty("timestamp")
  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }
  
  public Message withTimestamp(long timestamp) {
    setTimestamp(timestamp);
    return this;
  }
  
//  @JsonProperty("data")
//  public MessageData getData() {
//    return data;
//  }
//  
//  @JsonProperty("data")
//  public void setData(MessageData data) {
//    this.data = data;
//  }
//  
//  public Message withData(MessageData data) {
//    setData(data);
//    return this;
//  }
  public String toJson() throws JsonProcessingException {
    return mapper.writeValueAsString(this);
  }
}
