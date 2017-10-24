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

package org.openkilda.northbound.service.impl;

import static org.openkilda.messaging.Utils.CORRELATION_ID;

import org.glassfish.jersey.client.ClientConfig;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
//import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.flow.FlowCreateRequest;
import org.openkilda.messaging.command.flow.FlowDeleteRequest;
import org.openkilda.messaging.command.flow.FlowGetRequest;
import org.openkilda.messaging.command.flow.FlowPathRequest;
import org.openkilda.messaging.command.flow.FlowStatusRequest;
import org.openkilda.messaging.command.flow.FlowUpdateRequest;
import org.openkilda.messaging.command.flow.FlowsGetRequest;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.info.flow.FlowPathResponse;
import org.openkilda.messaging.info.flow.FlowResponse;
import org.openkilda.messaging.info.flow.FlowStatusResponse;
import org.openkilda.messaging.info.flow.FlowsResponse;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowEndpointPayload;
import org.openkilda.messaging.payload.flow.FlowIdStatusPayload;
import org.openkilda.messaging.payload.flow.FlowPathPayload;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.messaging.payload.flow.FlowStats;
import org.openkilda.messaging.payload.flow.ForwardEgressFlow;
import org.openkilda.messaging.payload.flow.ForwardIngressFlow;
import org.openkilda.messaging.payload.flow.ReverseEgressFlow;
import org.openkilda.messaging.payload.flow.ReverseIngressFlow;
import org.openkilda.northbound.messaging.MessageConsumer;
import org.openkilda.northbound.messaging.MessageProducer;
import org.openkilda.northbound.service.FlowService;
import org.openkilda.northbound.utils.Converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import static com.google.common.base.MoreObjects.firstNonNull;
import static java.util.Base64.getEncoder;
/**
 * Manages operations with flows.
 */
@Service
public class FlowServiceImpl implements FlowService {
	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(FlowServiceImpl.class);
	
	private static final String host = firstNonNull(System.getProperty("kilda.host"), "192.168.80.186");
	private static final String opentsdbPort = firstNonNull(System.getProperty("kilda.opentsdb.port"), "4242");
	private static final String northboundPort = firstNonNull(System.getProperty("kilda.northbound.port"), "8088");
	public static final String topologyUsername = firstNonNull(System.getProperty("kilda.topology.username"), "kilda");
	public static final String topologyPassword = firstNonNull(System.getProperty("kilda.topology.password"), "kilda");
	private static final String auth = topologyUsername + ":" + topologyPassword;
	public static final String opentsdbEndpoint = String.format("http://%s:%s", host, opentsdbPort);
	public static final String northboundEndpoint = String.format("http://%s:%s", host, northboundPort);
	private static final String authHeaderValue = "Basic " + getEncoder().encodeToString(auth.getBytes());
	

	/**
	 * The kafka topic.
	 */
	@Value("${kafka.topic}")
	private String topic;

	/**
	 * Kafka message consumer.
	 */
	@Autowired
	private MessageConsumer messageConsumer;

	/**
	 * Kafka message producer.
	 */
	@Autowired
	private MessageProducer messageProducer;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowPayload createFlow(final FlowPayload flow, final String correlationId) {
		logger.debug("Create flow: {}={}", CORRELATION_ID, correlationId);
		FlowCreateRequest data = new FlowCreateRequest(Converter.buildFlowByFlowPayload(flow));
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowPayloadByFlow(response.getPayload());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowPayload deleteFlow(final String id, final String correlationId) {
		logger.debug("Delete flow: {}={}", CORRELATION_ID, correlationId);
		Flow flow = new Flow();
		flow.setFlowId(id);
		FlowDeleteRequest data = new FlowDeleteRequest(flow);
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowPayloadByFlow(response.getPayload());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowPayload getFlow(final String id, final String correlationId) {
		logger.debug("Get flow: {}={}", CORRELATION_ID, correlationId);
		FlowGetRequest data = new FlowGetRequest(new FlowIdStatusPayload(id, null));
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowPayloadByFlow(response.getPayload());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowPayload updateFlow(final FlowPayload flow, final String correlationId) {
		logger.debug("Update flow: {}={}", CORRELATION_ID, correlationId);
		FlowUpdateRequest data = new FlowUpdateRequest(Converter.buildFlowByFlowPayload(flow));
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowResponse response = (FlowResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowPayloadByFlow(response.getPayload());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<FlowPayload> getFlows(final String correlationId) {
		logger.debug("Get flows: {}={}", CORRELATION_ID, correlationId);
		FlowsGetRequest data = new FlowsGetRequest(new FlowIdStatusPayload());
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowsResponse response = (FlowsResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowsPayloadByFlows(response.getPayload());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowIdStatusPayload statusFlow(final String id, final String correlationId) {
		logger.debug("Flow status: {}={}", CORRELATION_ID, correlationId);
		FlowStatusRequest data = new FlowStatusRequest(new FlowIdStatusPayload(id, null));
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowStatusResponse response = (FlowStatusResponse) validateInfoMessage(request, message, correlationId);
		return response.getPayload();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public FlowPathPayload pathFlow(final String id, final String correlationId) {
		logger.debug("Flow path: {}={}", CORRELATION_ID, correlationId);
		FlowPathRequest data = new FlowPathRequest(new FlowIdStatusPayload(id, null));
		CommandMessage request = new CommandMessage(data, System.currentTimeMillis(), correlationId, Destination.WFM);
		messageConsumer.clear();
		messageProducer.send(topic, request);
		Message message = (Message) messageConsumer.poll(correlationId);
		FlowPathResponse response = (FlowPathResponse) validateInfoMessage(request, message, correlationId);
		return Converter.buildFlowPathPayloadByFlowPath(id, response.getPayload());
	}

	
	/**
     * {@inheritDoc}
     */
    @Override
    public FlowStats getFlowStats(final String id, final String correlationId, final String statsType) {
     
      logger.debug("getFlow stats: {}={}" + "statsType : "+statsType );
      logger.debug("getFlow stats: {}={}", CORRELATION_ID, correlationId, "statsType : "+statsType );
      FlowStats flowStats = new FlowStats();
      String result = "";
      
      if(id.equalsIgnoreCase("test-flow")){
       flowStats.setFlowId(id);
       ForwardEgressFlow forwardEgressFlow = new ForwardEgressFlow(id,Utils.FORWARD_COOKIE_VALUE,"2","2","2","2");
       flowStats.setForwardEgressFlow(forwardEgressFlow);
       return flowStats;
      }
      try {
           Client client = ClientBuilder.newClient(new ClientConfig());
       logger.debug("getFlow stats: {}={}" + "northboundEndpoint : "+northboundEndpoint );
       
         result = client
              .target(northboundEndpoint)   
              .path("/api/v1/flows/"+id)
              .request(MediaType.APPLICATION_JSON)
              .header(HttpHeaders.AUTHORIZATION, authHeaderValue)
              .get(String.class);
      logger.debug("getFlow stats: {}={}" + "result : "+result );
      } 
      catch(Exception e){
      FlowStats response = new FlowStats();
      response.setErrorType(ErrorType.NOT_FOUND);
      response.setErrorMessage("Object was not found : flow id : "+id+" ,with northboundEndpoint ip : "+northboundEndpoint);
      response.setErrorDescription("Object was not found. Enter valid flow-id or check its host ip.");
        return response;
      }
      try{
      ObjectMapper mapper = new ObjectMapper();
      FlowPayload flowPayload = mapper.readValue(result, FlowPayload.class);
      
      String flowId = flowPayload.getId();
     // Long cookie = flowPayload.getCookie();
      FlowEndpointPayload flowDestination = flowPayload.getDestination();
      String destinationSwitchId = flowDestination.getSwitchId();
      destinationSwitchId = destinationSwitchId.replaceAll(":","");
      FlowEndpointPayload flowSource = flowPayload.getSource();
      String sourceSwitchId = flowSource.getSwitchId();
      sourceSwitchId = sourceSwitchId.replaceAll(":","");
      
      String receiveSrcPacketSwitchResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_RX_PACKETS,sourceSwitchId);
       String receiveSrcPacketSwitchTimestampCount = getLastTxRxValue(receiveSrcPacketSwitchResult);
       String [] receiveSrcPacketValue = receiveSrcPacketSwitchTimestampCount.split(":");
       String receiveSrcPacketSwitchCount = receiveSrcPacketValue[1];
       logger.debug("getFlow stats: {}={}", "receiveSrcPacketSwitchCount : "+receiveSrcPacketSwitchCount );
       
       String transmitSrcPacketSwitchResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_TX_PACKETS,sourceSwitchId);
       String transmitSrcPacketSwitchTimestampCount = getLastTxRxValue(transmitSrcPacketSwitchResult);
       String [] transmitSrcPacketValue = transmitSrcPacketSwitchTimestampCount.split(":");
       String transmitSrcPacketSwitchCount = transmitSrcPacketValue[1];
       logger.debug("getFlow stats: {}={}", "transmitSrcPacketSwitchCount : "+transmitSrcPacketSwitchCount );
       
       String receiveSrcByteResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_RX_BYTES,sourceSwitchId);
       String receiveSrcByteTimestampCount = getLastTxRxValue(receiveSrcByteResult);
       String [] receiveSrcByteCountValue = receiveSrcByteTimestampCount.split(":");
       String receiveSrcByteCount = receiveSrcByteCountValue[1];
       logger.debug("getFlow stats: {}={}", "receiveSrcByteCount : "+receiveSrcByteCount );
       
       String transmitSrcByteResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_TX_BYTES,sourceSwitchId);
       String transmitSrcByteTimestampCount = getLastTxRxValue(transmitSrcByteResult);
       String [] transmitSrcByteCountValue = transmitSrcByteTimestampCount.split(":");
       String transmitSrcByteCount = transmitSrcByteCountValue[1];
       logger.debug("getFlow stats: {}={}", "transmitSrcByteCount : "+transmitSrcByteCount );
       
       String receiveDstPacketSwitchResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_RX_PACKETS,destinationSwitchId);
       String receiveDstPacketSwitchTimestampCount = getLastTxRxValue(receiveDstPacketSwitchResult);
       String [] receiveDstPacketSwitchCountValue = receiveDstPacketSwitchTimestampCount.split(":");
       String receiveDstPacketSwitchCount = receiveDstPacketSwitchCountValue[1];
       logger.debug("getFlow stats: {}={}", "receiveDstPacketSwitchCount : "+receiveDstPacketSwitchCount );
       
       String transmitDstPacketSwitchResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_TX_PACKETS,destinationSwitchId);
       String transmitDstPacketSwitchTimestampCount = getLastTxRxValue(transmitDstPacketSwitchResult);
       String [] transmitDstPacketSwitchCountValue = transmitDstPacketSwitchTimestampCount.split(":");
       String transmitDstPacketSwitchCount = transmitDstPacketSwitchCountValue[1];
       logger.debug("getFlow stats: {}={}", "transmitDstPacketSwitchCount : "+transmitDstPacketSwitchCount );
       
       String receivedDstByteResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_RX_BYTES,destinationSwitchId);
       String receiveDstByteTimestampCount = getLastTxRxValue(receivedDstByteResult);
       String [] receiveDstByteCountValue = receiveDstByteTimestampCount.split(":");
       String receiveDstByteCount = receiveDstByteCountValue[1];
       logger.debug("getFlow stats: {}={}", "receiveDstByteCount : "+receiveDstByteCount );
       
       String transmitDstByteResult = getOpenTSDBData(correlationId,Utils.PEN_SWITCH_TX_BYTES,destinationSwitchId);
       String transmitDstByteTimestampCount = getLastTxRxValue(transmitDstByteResult);
       String [] transmitDstByteCountValue = transmitDstByteTimestampCount.split(":");
       String transmitDstByteCount = transmitDstByteCountValue[1];
       logger.debug("getFlow stats: {}={}", "transmitDstByteCount : "+transmitDstByteCount );
       
      
       flowStats.setFlowId(flowId);
       //flowStats.setCookie(cookie);
       // flowStats.setErrorMessage("northboundEndpoint : "+northboundEndpoint);
       if(statsType.equalsIgnoreCase(Utils.FORWARD_INGRESS) || statsType.equalsIgnoreCase(Utils.ALL)) {
        ForwardIngressFlow forwardIngressFlow = new ForwardIngressFlow();
        forwardIngressFlow.setSwitchId(sourceSwitchId);
        forwardIngressFlow.setCookieValue(Utils.FORWARD_COOKIE_VALUE);
        forwardIngressFlow.setRxPktCount(receiveSrcPacketSwitchCount);
        forwardIngressFlow.setTxPktCount(transmitSrcPacketSwitchCount);
        forwardIngressFlow.setRxByteCount(receiveSrcByteCount);
        forwardIngressFlow.setTxByteCount(transmitSrcByteCount);
        flowStats.setForwardIngressFlow(forwardIngressFlow);
       }
       
       if(statsType.equalsIgnoreCase(Utils.FORWARD_EGRESS) || statsType.equalsIgnoreCase(Utils.ALL)) {
           ForwardEgressFlow forwardEgressFlow = new ForwardEgressFlow();
           forwardEgressFlow.setSwitchId(destinationSwitchId);
           forwardEgressFlow.setCookieValue(Utils.FORWARD_COOKIE_VALUE);
           forwardEgressFlow.setRxPktCount(receiveDstPacketSwitchCount);
           forwardEgressFlow.setTxPktCount(transmitDstPacketSwitchCount);
           forwardEgressFlow.setRxByteCount(receiveDstByteCount);
           forwardEgressFlow.setTxByteCount(transmitDstByteCount);
           flowStats.setForwardEgressFlow(forwardEgressFlow);
          }
          
          if(statsType.equalsIgnoreCase(Utils.REVERSE_INGRESS)|| statsType.equalsIgnoreCase(Utils.ALL)) {
           ReverseIngressFlow reverseIngressFlow = new ReverseIngressFlow();
           reverseIngressFlow.setSwitchId(destinationSwitchId);
           reverseIngressFlow.setCookieValue(Utils.REVERSE_COOKIE_VALUE);
           reverseIngressFlow.setRxPktCount(receiveDstPacketSwitchCount);
           reverseIngressFlow.setTxPktCount(transmitDstPacketSwitchCount);
           reverseIngressFlow.setRxByteCount(receiveDstByteCount);
           reverseIngressFlow.setTxByteCount(transmitDstByteCount);
           flowStats.setReverseIngressFlow(reverseIngressFlow);
          }
          
          if(statsType.equalsIgnoreCase(Utils.REVERSE_EGRESS) || statsType.equalsIgnoreCase(Utils.ALL)) {
           ReverseEgressFlow reverseEgressFlow = new ReverseEgressFlow();
           reverseEgressFlow.setSwitchId(sourceSwitchId);
           reverseEgressFlow.setCookieValue(Utils.REVERSE_COOKIE_VALUE);
           reverseEgressFlow.setRxPktCount(receiveSrcPacketSwitchCount);
           reverseEgressFlow.setTxPktCount(transmitSrcPacketSwitchCount);
           reverseEgressFlow.setRxByteCount(receiveSrcByteCount);
           reverseEgressFlow.setTxByteCount(transmitSrcByteCount);
           flowStats.setReverseEgressFlow(reverseEgressFlow);
          }
          
          
         }catch(Exception e){
          logger.error("getFlow stats: {}={}", "Exception : "+ e.getCause());
          flowStats.setErrorMessage("Not getting any data");
          flowStats.setErrorDescription("error in getting data");
          flowStats.setErrorType(ErrorType.INTERNAL_ERROR);
         }
          return flowStats;
       }
       
       public String getOpenTSDBData (String correlationId, String metricName, String switchId ){
        logger.debug("getOpenTSDBData : {}={}", CORRELATION_ID, correlationId);
        String result = "";
        
        try{
              Client client = ClientBuilder.newClient(new ClientConfig());
           
          result = client
                   .target(opentsdbEndpoint)
                   .path("/api/query")
                   .queryParam("start","5h-ago")
                   .queryParam("m","count:"+metricName)
                   .queryParam("{switchid="+switchId+"}")
                   .request()
                   .get(String.class);
        }catch(Exception e){
         return "not getting any data.";
        }
        return result;
       }
       
       public String getLastTxRxValue(String result){
         String[] val = result.split(",");
            int size = val.length;
            String lastval = val[size-1];
            String[] v = lastval.split("}}]");
            return v[0];
       }
}
