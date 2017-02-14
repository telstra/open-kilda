package org.bitbucket.openkilda.floodlight.message.info;

import org.bitbucket.openkilda.floodlight.message.MessageData;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
  "message_type"
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
              include = JsonTypeInfo.As.PROPERTY, 
              property = "message_type")

@JsonSubTypes({ 
  @Type(value = PathInfoData.class, name = "path"), 
  @Type(value = IslInfoData.class, name = "isl"),
  @Type(value = SwitchInfoData.class, name = "switch"),
  @Type(value = PortInfoData.class, name = "port")
})

public abstract class InfoData extends MessageData {

  private static final long serialVersionUID = 1L;
  @JsonProperty("message_type")
  private MESSAGE_TYPE message_type;
  
  public enum MESSAGE_TYPE {
    PATH,
    ISL,
    SWITCH,
    PORT
  }
  
  public InfoData() {
    // no args init for serializer
  }
  
  @JsonProperty("message_type")
  public MESSAGE_TYPE getMessageType() {
    return message_type;
  }
}
