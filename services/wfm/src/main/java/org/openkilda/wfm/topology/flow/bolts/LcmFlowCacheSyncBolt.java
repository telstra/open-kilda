/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.flow.bolts;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.FlowsSyncRequest;
import org.openkilda.messaging.info.InfoMessage;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LcmFlowCacheSyncBolt extends BaseRichBolt {
    private static final Logger logger = LoggerFactory.getLogger(LcmFlowCacheSyncBolt.class);

    public static final String FIELD_ID_MESSAGE = "message";
    public static final String FIELD_ID_JSON = "json";
    public static final String FIELD_ID_NETWORK_DUMP = "network-dump";

    public static final String STREAM_ID_TPE = "kafka_into_TE";
    public static final String STREAM_ID_SYNC_FLOW_CACHE = "sync.flow_cache";

    private TopologyContext context;
    private OutputCollector output;

    private final Map<String, LcmSyncTrace> pendingSyncRequests = new HashMap<>();
    private final List<String> managedSpouts;

    public LcmFlowCacheSyncBolt(String... managedSpouts) {
        this.managedSpouts = Arrays.asList(managedSpouts);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.context = context;
        this.output = collector;
    }

    @Override
    public void execute(Tuple input) {
        logger.debug("{} - execute", context.getThisComponentId());
        String componentId = input.getSourceComponent();

        try {
            if (managedSpouts.contains(componentId)) {
                handleManagedSpout(input);
            } else {
                handleInputData(input);
            }
        } catch (Exception e) {
            logger.error("{} - handler exception", context.getThisComponentId(), e);
            output.reportError(e);
            output.fail(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_ID_TPE, new Fields(FIELD_ID_MESSAGE));
        declarer.declareStream(STREAM_ID_SYNC_FLOW_CACHE, new Fields(FIELD_ID_JSON, FIELD_ID_NETWORK_DUMP));
    }

    private void handleManagedSpout(Tuple input) {
        logger.info("{} - handle managed spout", context.getThisComponentId());
        LcmSyncTrace syncTrace = new LcmSyncTrace(input);

        CommandMessage syncRequest = makeSyncRequest(syncTrace);
        String json;
        try {
            json = Utils.MAPPER.writeValueAsString(syncRequest);
        } catch (JsonProcessingException e) {
            logger.error("Can serialise sync request `{}` into JSON: {}", syncRequest, e);

            output.fail(input);
            return;
        }

        logger.info("{} - emit sync request: {}", context.getThisComponentId(), json);
        output.emit(STREAM_ID_TPE, input, new Values(json));
        pendingSyncRequests.put(input.getSourceComponent(), syncTrace);
    }

    private void handleInputData(Tuple input) {
        logger.info("{} - handle data stream", context.getThisComponentId());
        try {
            Message raw;
            String json;
            try {
                json = input.getString(0);
                raw = Utils.MAPPER.readValue(json, Message.class);
            } catch (IndexOutOfBoundsException | IOException e) {
                logger.debug("Skip non deserializable record {}: {}", input, e);
                return;
            }

            UUID correlationId;
            try {
                correlationId = UUID.fromString(raw.getCorrelationId());
            } catch (IllegalArgumentException e) {
                logger.debug("Skip record due to malformed correlation id: {}", raw.getCorrelationId());
                return;
            }

            LcmSyncTrace pendingSync = popPendingSyncRequest(correlationId);
            if (pendingSync != null) {
                Tuple syncRequest = pendingSync.getSyncRequest();
                logger.info("Got sync response for spout {}", syncRequest.getSourceComponent());

                InfoMessage envelope = (InfoMessage) raw;
                output.emit(STREAM_ID_SYNC_FLOW_CACHE, input, new Values(json, envelope.getData()));
                output.ack(syncRequest);
            } else {
                logger.debug("{} - there is no pending sync request with correlation-id: {}",
                        context.getThisComponentId(), correlationId);
            }
        } finally {
            output.ack(input);
        }
    }

    private LcmSyncTrace popPendingSyncRequest(UUID correlationId) {
        LcmSyncTrace match = null;

        for (String componentId : pendingSyncRequests.keySet()) {
            LcmSyncTrace trace = pendingSyncRequests.get(componentId);
            if (!correlationId.equals(trace.getCorrelationId())) {
                continue;
            }

            match = trace;
            pendingSyncRequests.remove(componentId);
            break;
        }

        return match;
    }

    private CommandMessage makeSyncRequest(LcmSyncTrace syncTrace) {
        String correlationId = syncTrace.getCorrelationId().toString();
        return new CommandMessage(new FlowsSyncRequest(),
                System.currentTimeMillis(), correlationId, Destination.TOPOLOGY_ENGINE);
    }
}
