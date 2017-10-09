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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.TransportPort;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mininet implements IMininet {

  private static final Logger logger = LogManager.getLogger(Mininet.class.getName());
  private IPv4Address mininetServerIP;
  private TransportPort mininetServerPort;
  private ObjectMapper mapper;
  
  private static final int CONNECTION_TIMEOUT_MS = 5000;
  
  /**
   * Instantiates a new mininet.
   */
  public Mininet() {
    mapper = new ObjectMapper();
    MininetSwitch mnSw = new MininetSwitch();  
    //TODO: Should we move all the serializers into a single class?
    SimpleModule switchModule = new SimpleModule();
    switchModule.addSerializer(MininetSwitch.class, mnSw.new Serializer());
    mapper.registerModule(switchModule);
  }
  
  @Override
  public IMininet build() throws MininetException, URISyntaxException {
    if (mininetServerIP == null) {
      throw new MininetException("Need to set Mininet Server IP before calling build.");
    }
    
    if (mininetServerPort == null) {
      throw new MininetException("Need to set Mininet Server Port before calling build.");
    }
    
    if (!this.isConnect()) {
      throw new MininetException("Could not connect to the Mininet Server.");
    }
    
    return this;
  }

  @Override
  public IMininet addMininetServer(IPv4Address ipAddress, TransportPort port) {
    mininetServerIP = ipAddress;
    mininetServerPort = port;
    return this;
  }

  @Override
  public IMininet addMininetServer(String hostname, int port) throws UnknownHostException {
    mininetServerIP = IPv4Address.of((Inet4Address) Inet4Address.getByName(hostname));
    mininetServerPort = TransportPort.of(port);
    return this;
  }
  
  @Override
  public IMininet addController(IMininetController controller) {
    try {
      Map<String, List<IMininetController>> jsonPojo = 
          new HashMap<>();
      List<IMininetController> controllers = new ArrayList<>();
      controllers.add(controller);
      jsonPojo.put("controllers", controllers);
      logger.debug("adding controller " + mapper.writeValueAsString(jsonPojo));
      simplePost("/controller", mapper.writeValueAsString(jsonPojo));
    } catch (URISyntaxException | IOException | MininetException e) {
      logger.error(e);
    }
    return this;
  }
  
  @Override
  public IMininet addSwitch(String name, DatapathId dpid) {
    MininetSwitch sw = new MininetSwitch(name, dpid.toString(), null, null);
    MininetSwitches switches = new MininetSwitches().addSwitch(sw);
    try {
      logger.debug("sending " + mapper.writeValueAsString(switches));
      simplePost("/switch", mapper.writeValueAsString(switches));
    } catch (URISyntaxException | IOException | MininetException e) {
      logger.error(e);
    }
    return this;
  }
  
  @Override
  public IMininet addLink(String nodeA, String nodeB) {
    MininetLinks links = new MininetLinks().addLink(nodeA, nodeB);
    try {
      logger.debug("sending " + mapper.writeValueAsString(links));
      simplePost("/links", mapper.writeValueAsString(links));
    } catch (URISyntaxException | IOException | MininetException e) {
      logger.error(e);
    }
    return this;
  }

  @Override
  public IMininet clear() {
    try {
      simplePost("/cleanup", "");
    } catch (URISyntaxException | IOException | MininetException e) {
      logger.error(e);
    }
    return this;
  }
  
  @Override
  public MininetSwitches switches() {
    MininetSwitches switches = null;
    CloseableHttpResponse response;
    try {
      response = simpleGet("/switch");
      switches = mapper.readValue(response.getEntity().getContent(), MininetSwitches.class);
    } catch (Exception e) {
      logger.error(e);
    }
    return switches;
  }
  
  @Override
  public MininetSwitch getSwitch(String name) {
    MininetSwitch sw = null;
    CloseableHttpResponse response;
    try {
      response = simpleGet(String.format("/switch/%s", name));
      sw = mapper.readValue(response.getEntity().getContent(), MininetSwitch.class);
    } catch (Exception e) {
      logger.error(e);
    }
    return sw;
  }
  
  @Override
  public boolean isSwitchConnected(String name) {
    MininetSwitch sw = getSwitch(name);
    return sw.getConnected();
  }
  
  @Override
  public MininetLinks links() {
    MininetLinks links = null;
    CloseableHttpResponse response;
    try {
      response = simpleGet("/links");
      links = mapper.readValue(response.getEntity().getContent(), MininetLinks.class);
    } catch (Exception e) {
      logger.error(e);
    }
    return links;
  }

  @Override
  public boolean isConnect() {
    HttpResponse response;
    boolean results = false;
    try {
      response = simpleGet("/status");
      MininetStatus status = mapper.readValue(response.getEntity()
          .getContent(), MininetStatus.class);
      results = status.isConnected();
    } catch (Exception e) {
      logger.error(e);
    }
    return results;
  }
  
  /**
   * Simple Http Get.
   *
   * @param path the path
   * @return the CloseableHttpResponse
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws MininetException the MininetException
   */
  public CloseableHttpResponse simpleGet(String path) 
      throws URISyntaxException, IOException, MininetException {
    URI uri = new URIBuilder()
        .setScheme("http")
        .setHost(mininetServerIP.toString())
        .setPort(mininetServerPort.getPort())
        .setPath(path)
        .build();
    CloseableHttpClient client = HttpClientBuilder.create().build();
    RequestConfig config = RequestConfig
        .custom()
        .setConnectTimeout(CONNECTION_TIMEOUT_MS)
        .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
        .setSocketTimeout(CONNECTION_TIMEOUT_MS)
        .build();
    HttpGet request = new HttpGet(uri);
    request.setConfig(config);
    request.addHeader("Content-Type", "application/json");
    CloseableHttpResponse response = client.execute(request);
    if (response.getStatusLine().getStatusCode() >= 300) {
      throw new MininetException(String.format("failure - received a %d for %s.", 
          response.getStatusLine().getStatusCode(), request.getURI().toString()));
    }
    return response;
  }
  
  /**
   * Simple Http Post.
   *
   * @param path the path
   * @param payload the payload
   * @return the closeable http response
   * @throws URISyntaxException the URI syntax exception
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws MininetException the MininetException
   */
  public CloseableHttpResponse simplePost(String path, String payload) 
      throws URISyntaxException, IOException, MininetException {
    URI uri = new URIBuilder()
        .setScheme("http")
        .setHost(mininetServerIP.toString())
        .setPort(mininetServerPort.getPort())
        .setPath(path)
        .build();
    CloseableHttpClient client = HttpClientBuilder.create().build();
    RequestConfig config = RequestConfig
        .custom()
        .setConnectTimeout(CONNECTION_TIMEOUT_MS)
        .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MS)
        .setSocketTimeout(CONNECTION_TIMEOUT_MS)
        .build();
    HttpPost request = new HttpPost(uri);
    request.setConfig(config);
    request.addHeader("Content-Type", "application/json");
    request.setEntity(new StringEntity(payload));
    CloseableHttpResponse response = client.execute(request);
    if (response.getStatusLine().getStatusCode() >= 300) {
      throw new MininetException(String.format("failure - received a %d for %s.", 
          response.getStatusLine().getStatusCode(), request.getURI().toString()));
    }
    return response;
  }
}
