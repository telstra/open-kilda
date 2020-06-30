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

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.messaging.AliveRequest;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.NetworkCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.UnmanagedSwitchNotification;
import org.openkilda.model.FeatureToggles;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.FeatureTogglesRepository;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;
import org.openkilda.wfm.topology.floodlightrouter.RegionAwareKafkaTopicSelector;
import org.openkilda.wfm.topology.floodlightrouter.Stream;
import org.openkilda.wfm.topology.floodlightrouter.service.FloodlightTracker;
import org.openkilda.wfm.topology.floodlightrouter.service.MessageSender;
import org.openkilda.wfm.topology.floodlightrouter.service.RouterService;
import org.openkilda.wfm.topology.floodlightrouter.service.SwitchMapping;
import org.openkilda.wfm.topology.utils.AbstractTickRichBolt;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

// FIXME(surabujin) must use AbstractBolt as base
@Slf4j
public class RegionTrackerBolt extends AbstractTickRichBolt implements MessageSender {
    public static final String BOLT_ID = ComponentType.KILDA_TOPO_DISCO_BOLT;

    public static final String STREAM_SPEAKER_ID = Stream.SPEAKER_DISCO;
    public static final String STREAM_NETWORK_ID = Stream.KILDA_TOPO_DISCO;

    public static final String STREAM_REGION_UPDATE_ID = Stream.REGION_NOTIFICATION;
    public static final Fields STREAM_REGION_UPDATE_FIELDS = new Fields(
            AbstractTopology.MESSAGE_FIELD, AbstractBolt.FIELD_ID_CONTEXT);

    private final String kafkaSpeakerTopic;
    private final String kafkaNetworkTopic;

    private final PersistenceManager persistenceManager;

    private final Set<String> floodlights;
    private final long floodlightAliveTimeout;
    private final long floodlightAliveInterval;
    private final long floodlightDumpInterval;
    private long lastNetworkDumpTimestamp;

    private transient FeatureTogglesRepository featureTogglesRepository;
    private transient RouterService routerService;
    private transient CommandContext commandContext;

    private Tuple currentTuple;

    public RegionTrackerBolt(
            KafkaTopicsConfig kafkaTopics, PersistenceManager persistenceManager, Set<String> floodlights,
            long floodlightAliveTimeout, long floodlightAliveInterval, long floodlightDumpInterval) {
        super();

        kafkaSpeakerTopic = kafkaTopics.getSpeakerDiscoRegionTopic();
        kafkaNetworkTopic = kafkaTopics.getTopoDiscoTopic();

        this.persistenceManager = persistenceManager;
        this.floodlights = floodlights;
        this.floodlightAliveTimeout = floodlightAliveTimeout;
        this.floodlightAliveInterval = floodlightAliveInterval;
        this.floodlightDumpInterval = TimeUnit.SECONDS.toMillis(floodlightDumpInterval);
    }

    @Override
    protected void doTick(Tuple tuple) {
        setupTuple(tuple);

        try {
            handleTick();
        } finally {
            cleanupTuple();
        }
    }

    @Override
    protected void doWork(Tuple input) {
        setupTuple(input);

        try {
            handleInput(input);
        } catch (Exception e) {
            log.error("Failed to process tuple {}", input, e);
        } finally {
            outputCollector.ack(input);
            cleanupTuple();
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        Fields fields = new Fields(
                FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE,
                RegionAwareKafkaTopicSelector.FIELD_ID_TOPIC, RegionAwareKafkaTopicSelector.FIELD_ID_REGION);
        outputManager.declareStream(STREAM_SPEAKER_ID, fields);
        outputManager.declareStream(STREAM_NETWORK_ID, fields);

        outputManager.declareStream(STREAM_REGION_UPDATE_ID, STREAM_REGION_UPDATE_FIELDS);
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        featureTogglesRepository = persistenceManager.getRepositoryFactory().createFeatureTogglesRepository();
        FloodlightTracker floodlightTracker = new FloodlightTracker(floodlights, floodlightAliveTimeout,
                floodlightAliveInterval);
        routerService = new RouterService(floodlightTracker);
        super.prepare(map, topologyContext, outputCollector);
    }

    private void handleTick() {
        routerService.doPeriodicProcessing(this);

        long now = System.currentTimeMillis();
        if (now >= lastNetworkDumpTimestamp + floodlightDumpInterval) {
            doNetworkDump();
            lastNetworkDumpTimestamp = now;
        }
    }

    private void handleInput(Tuple input) {
        String sourceComponent = input.getSourceComponent();
        Message message = (Message) input.getValueByField(AbstractTopology.MESSAGE_FIELD);

        // setup correct command context
        commandContext = new CommandContext(message);

        switch (sourceComponent) {
            case ComponentType.KILDA_TOPO_DISCO_REPLY_BOLT:
                routerService.processSpeakerDiscoResponse(this, message);
                break;
            default:
                log.error("Unknown input stream handled: {}", sourceComponent);
                break;
        }
    }

    private void setupTuple(Tuple input) {
        currentTuple = input;
        commandContext = new CommandContext();
    }

    private void cleanupTuple() {
        currentTuple = null;
        commandContext = null;
    }

    // MessageSender implementation

    @Override
    public void emitSpeakerAliveRequest(String region) {
        AliveRequest request = new AliveRequest();
        CommandMessage message = new CommandMessage(request, System.currentTimeMillis(),
                                                    commandContext.fork(String.format("alive-request(%s)", region))
                                                            .getCorrelationId());
        outputCollector.emit(
                STREAM_SPEAKER_ID, currentTuple, makeSpeakerTuple(pullKeyFromCurrentTuple(), message, region));
    }

    @Override
    public void emitSwitchUnmanagedNotification(SwitchId sw) {
        UnmanagedSwitchNotification notification = new UnmanagedSwitchNotification(sw);
        InfoMessage message = new InfoMessage(notification, System.currentTimeMillis(),
                                              commandContext.fork(String.format("unmanaged(%s)", sw.toOtsdFormat()))
                                                      .getCorrelationId());
        outputCollector.emit(
                STREAM_NETWORK_ID, currentTuple, makeNetworkTuple(sw.toString(), message));
    }

    /**
     * Send network dump requests for target region.
     */
    @Override
    public void emitNetworkDumpRequest(String region) {
        String correlationId = commandContext.fork(String.format("network-dump(%s)", region)).getCorrelationId();
        CommandMessage command = new CommandMessage(new NetworkCommandData(),
                                                    System.currentTimeMillis(), correlationId);

        log.info("Send network dump request (correlation-id: {})", correlationId);
        outputCollector.emit(
                STREAM_SPEAKER_ID, currentTuple, makeSpeakerTuple(correlationId, command, region));
    }

    @Override
    public void emitRegionNotification(SwitchMapping mapping) {
        outputCollector.emit(STREAM_REGION_UPDATE_ID, currentTuple, makeRegionUpdateTuple(mapping));
    }

    private String pullKeyFromCurrentTuple() {
        if (currentTuple.getFields().contains(AbstractTopology.KEY_FIELD)) {
            return currentTuple.getStringByField(AbstractTopology.KEY_FIELD);
        }
        return null;
    }

    private void doNetworkDump() {
        if (!queryPeriodicSyncFeatureToggle()) {
            log.warn("Skip periodic network sync (disabled by feature toggle)");
            return;
        }

        log.debug("Do periodic network dump request");
        for (String region : floodlights) {
            emitNetworkDumpRequest(region);
        }
    }

    private boolean queryPeriodicSyncFeatureToggle() {
        return featureTogglesRepository.find()
                .map(FeatureToggles::getFloodlightRoutePeriodicSync)
                .orElse(FeatureToggles.DEFAULTS.getFloodlightRoutePeriodicSync());
    }

    private Values makeSpeakerTuple(String key, Message payload, String region) {
        return makeGenericKafkaTuple(key, payload, kafkaSpeakerTopic, region);
    }

    private Values makeNetworkTuple(String key, Message payload) {
        return makeGenericKafkaTuple(key, payload, kafkaNetworkTopic);
    }

    private Values makeRegionUpdateTuple(SwitchMapping payload) {
        return new Values(payload, commandContext);
    }

    private Values makeGenericKafkaTuple(String key, Message payload, String topic) {
        return makeGenericKafkaTuple(key, payload, topic, null);
    }

    private Values makeGenericKafkaTuple(String key, Message payload, String topic, String region) {
        return new Values(key, payload, topic, region);
    }
}
