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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "name", 
    "dpid", 
    "interface", 
    "connected"
    })

public class MininetSwitch implements IMininetSwitch {
  @JsonProperty("name")
  private String name;
  @JsonProperty("dpid")
  private String dpid;
  @JsonProperty("interface")
  private List<MininetInterface> interfaces = null;
  @JsonProperty("connected")
  private Boolean connected;

  private static final Logger logger = LogManager.getLogger(MininetSwitch.class.getName());
  private ObjectMapper mapper;

  public class Serializer extends StdSerializer<MininetSwitch> {
    public Serializer() {
      this(null);
    }

    public Serializer(Class<MininetSwitch> t) {
      super(t);
    }

    public void serialize(MininetSwitch sw, JsonGenerator jgen, SerializerProvider provider)
        throws IOException {
      jgen.writeStartObject();
      jgen.writeStringField("name", sw.name);
      jgen.writeStringField("dpid", sw.dpid);
      jgen.writeEndObject();
    }

  }

  /**
   * Instantiates a new MininetSwitch.
   */
  public MininetSwitch() {
    interfaces = new ArrayList<MininetInterface>();
    mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(MininetSwitch.class, new Serializer());
    mapper.registerModule(module);
  }

  /**
   * Instantiates a new MininetSwitch.
   *
   * @param name the name
   * @param dpid the dpid
   * @param interfaces the interfaces
   * @param connected the connected
   */
  public MininetSwitch(String name, String dpid, List<MininetInterface> interfaces,
      Boolean connected) {
    logger.debug("creating siwtch " + name + " with DPID " + dpid);
    this.name = name;
    this.dpid = dpid;
    this.interfaces = interfaces;
    this.connected = connected;
  }

  @Override
  @JsonProperty("name")
  public String getName() {
    return name;
  }

  @Override
  @JsonProperty("name")
  public void setName(String name) {
    this.name = name;
  }

  @Override
  @JsonProperty("dpid")
  public String getDpid() {
    return dpid;
  }

  @Override
  @JsonProperty("dpid")
  public void setDpid(String dpid) {
    this.dpid = dpid;
  }

  @Override
  @JsonProperty("interface")
  public List<MininetInterface> getInterfaces() {
    return interfaces;
  }

  @Override
  @JsonProperty("interface")
  public void setInterfaces(List<MininetInterface> interfaces) {
    this.interfaces = interfaces;
  }

  @Override
  @JsonProperty("connected")
  public Boolean getConnected() {
    return connected;
  }

  @Override
  @JsonProperty("connected")
  public void setConnected(Boolean connected) {
    this.connected = connected;
  }

  @Override
  public void addInterface(MininetInterface intf) {
    interfaces.add(intf);
  }

  @Override
  public String toJson() throws JsonProcessingException {
    return mapper.writeValueAsString(this);
  }

  @Override
  public String toString() {
    return "MininetSwitch [name=" + name + ", dpid=" + dpid + "]";
  }
}
