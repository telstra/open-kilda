/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;
import org.openkilda.model.FeatureToggles;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.FloodlightTracker;
import org.openkilda.wfm.topology.floodlightrouter.service.MessageSender;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterService;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.utils.AbstractTickRichBolt;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class DiscoveryBolt extends AbstractTickRichBolt implements MessageSender {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryBolt.class);

    private final PersistenceManager persistenceManager;

    private final Set<String> floodlights;
    private final long floodlightAliveTimeout;
    private final long floodlightAliveInterval;
    private final long floodlightDumpInterval;
    private long lastNetworkDumpTimestamp;

    private transient FeatureTogglesRepository featureTogglesRepository;
    private transient RouterService routerService;

    private Tuple currentTuple;

    public DiscoveryBolt(PersistenceManager persistenceManager, Set<String> floodlights, long floodlightAliveTimeout,
                         long floodlightAliveInterval, long floodlightDumpInterval) {
        this.persistenceManager = persistenceManager;
        this.floodlights = floodlights;
        this.floodlightAliveTimeout = floodlightAliveTimeout;
        this.floodlightAliveInterval = floodlightAliveInterval;
        this.floodlightDumpInterval = TimeUnit.SECONDS.toMillis(floodlightDumpInterval);
    }

    @Override
    protected void doTick(Tuple tuple) {
        currentTuple = tuple;
        routerService.doPeriodicProcessing(this);

        long now = System.currentTimeMillis();
        if (now >= lastNetworkDumpTimestamp + floodlightDumpInterval) {
            doNetworkDump();
            lastNetworkDumpTimestamp = now;
        }
    }

    @Override
    protected void doWork(Tuple input) {
        String sourceComponent = input.getSourceComponent();
        currentTuple = input;
        Message message = null;
        try {
            String json = input.getStringByField(AbstractTopology.MESSAGE_FIELD);
            message = MAPPER.readValue(json, Message.class);
            switch (sourceComponent) {
                case ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT:
                    routerService.processSpeakerDiscoResponse(this, message);
                    break;
                case ComponentType.SPEAKER_DISCO_KAFKA_SPOUT:
                    routerService.processDiscoSpeakerRequest(this, message);
                    break;
                default:
                    logger.error("Unknown input stream handled: {}", sourceComponent);
                    break;
            }
        } catch (Exception e) {
            logger.error("Failed to process message {}", message, e);
        } finally {
            outputCollector.ack(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        Fields kafkaFields = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY,
                                        FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);
        for (String region : floodlights) {
            outputFieldsDeclarer.declareStream(Stream.formatWithRegion(Stream.SPEAKER_DISCO, region), kafkaFields);
        }
        outputFieldsDeclarer.declareStream(Stream.KILDA_TOPO_DISCO, kafkaFields);
        outputFieldsDeclarer.declareStream(Stream.REGION_NOTIFICATION, new Fields(AbstractTopology.MESSAGE_FIELD));
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        FloodlightTracker floodlightTracker = new FloodlightTracker(floodlights, floodlightAliveTimeout,
                floodlightAliveInterval);
        routerService = new RouterService(floodlightTracker);
        super.prepare(map, topologyContext, outputCollector);
    }

    // MessageSender implementation

    @Override
    public void emitSpeakerMessage(Message message, String region) {
        emitSpeakerMessage(pullKeyFromCurrentTuple(), message, region);
    }

    @Override
    public void emitSpeakerMessage(String key, Message message, String region) {
        String stream = Stream.formatWithRegion(Stream.SPEAKER_DISCO, region);
        send(key, message, stream);
    }

    @Override
    public void emitControllerMessage(Message message) {
        emitControllerMessage(pullKeyFromCurrentTuple(), message);
    }

    @Override
    public void emitControllerMessage(String key, Message message) {
        send(key, message, Stream.KILDA_TOPO_DISCO);
    }

    @Override
    public void emitRegionNotification(SwitchMapping mapping) {
        outputCollector.emit(Stream.REGION_NOTIFICATION, currentTuple, new Values(mapping));
    }

    private void send(String key, Message message, String outputStream) {
        try {
            String json = MAPPER.writeValueAsString(message);
            Values values = new Values(key, json);
            outputCollector.emit(outputStream, currentTuple, values);
        } catch (JsonProcessingException e) {
            logger.error("failed to serialize message {}", message);
        }
    }

    private String pullKeyFromCurrentTuple() {
        String key = null;
        if (currentTuple.getFields().contains(AbstractTopology.KEY_FIELD)) {
            key = currentTuple.getStringByField(AbstractTopology.KEY_FIELD);
        }
        return key;
    }

    private void doNetworkDump() {
        if (!queryPeriodicSyncFeatureToggle()) {
            logger.warn("Skip periodic network sync (disabled by feature toggle)");
            return;
        }

        logger.debug("Do periodic network dump request");
        for (String region : floodlights) {
            routerService.sendNetworkRequest(this, region);
        }
    }

    private boolean queryPeriodicSyncFeatureToggle() {
        return featureTogglesRepository.find()
                .map(FeatureToggles::getFloodlightRoutePeriodicSync)
                .orElse(FeatureToggles.DEFAULTS.getFloodlightRoutePeriodicSync());
    }
}
