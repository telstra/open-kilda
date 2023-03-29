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

package org.openkilda.wfm.topology.floodlightrouter;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.kafka.AbstractMessageSerializer;
import org.openkilda.wfm.kafka.MessageSerializer;
import org.openkilda.wfm.share.bolt.MonotonicClock;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.bolts.ControllerToSpeakerProxyBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.ControllerToSpeakerSharedProxyBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.MonotonicTick;
import org.openkilda.wfm.topology.floodlightrouter.bolts.RegionTrackerBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.SpeakerToControllerProxyBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.SpeakerToNetworkProxyBolt;
import org.openkilda.wfm.topology.floodlightrouter.bolts.SwitchMonitorBolt;

import com.google.common.base.Strings;
import lombok.Value;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Floodlight router topology.
 */
public class FloodlightRouterTopology extends AbstractTopology<FloodlightRouterTopologyConfig> {
    private final Set<String> regions;

    private final KafkaTopicsConfig kafkaTopics;
    private final PersistenceManager persistenceManager;

    public FloodlightRouterTopology(LaunchEnvironment env) throws ConfigurationException {
        super(env, "floodlightrouter-topology", FloodlightRouterTopologyConfig.class);

        regions = topologyConfig.getFloodlightRegions().stream()
                .filter(entry -> !Strings.isNullOrEmpty(entry))
                .collect(Collectors.toSet());
        if (regions.isEmpty()) {
            throw new ConfigurationException("regions list must not be empty");
        }

        kafkaTopics = topologyConfig.getKafkaTopics();
        persistenceManager = new PersistenceManager(configurationProvider);
    }

    @Override
    public StormTopology createTopology() {
        logger.info("Creating FloodlightRouter topology as {}", topologyName);

        TopologyBuilder builder = new TopologyBuilder();

        TopologyOutput output = kafkaOutput(builder);
        zkSpout(builder);
        zkBolt(builder);

        speakerToNetwork(builder, output);
        networkToSpeaker(builder, output);

        speakerToFlowHs(builder, output);
        flowHsToSpeaker(builder, output);

        speakerToPing(builder, output);
        pingToSpeaker(builder, output);

        speakerToStats(builder, output);
        speakerToIslLatency(builder, output);
        speakerToConnectedDevices(builder, output);
        speakerToSwitchManager(builder, output);
        speakerToNorthbound(builder, output);
        speakerToNbWorker(builder, output);

        controllerToSpeaker(builder, output);

        regionTracker(builder, output);
        switchMonitor(builder, output);

        clock(builder);

        return builder.createTopology();
    }

    private void zkSpout(TopologyBuilder topology) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(topologyConfig.getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(topology, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void zkBolt(TopologyBuilder topology) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(topologyConfig.getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(),
                getBoltInstancesCount(SpeakerToNetworkProxyBolt.BOLT_ID, ComponentType.SPEAKER_DISCO_REQUEST_BOLT,
                        ComponentType.KILDA_FLOW_HS_REPLY_BOLT, ComponentType.SPEAKER_FLOW_REQUEST_BOLT,
                        ComponentType.KILDA_PING_REPLY_BOLT, ComponentType.KILDA_STATS_REPLY_BOLT,
                        ComponentType.KILDA_ISL_LATENCY_REPLY_BOLT, ComponentType.KILDA_CONNECTED_DEVICES_REPLY_BOLT,
                        ComponentType.KILDA_SWITCH_MANAGER_REPLY_BOLT, ComponentType.NORTHBOUND_REPLY_BOLT,
                        ComponentType.KILDA_NB_WORKER_REPLY_BOLT, ComponentType.SPEAKER_REQUEST_BOLT,
                        RegionTrackerBolt.BOLT_ID, SwitchMonitorBolt.BOLT_ID));
        declareBolt(topology, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(SpeakerToNetworkProxyBolt.BOLT_ID, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.SPEAKER_DISCO_REQUEST_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_FLOW_HS_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.SPEAKER_FLOW_REQUEST_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_PING_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_STATS_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_ISL_LATENCY_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_CONNECTED_DEVICES_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_SWITCH_MANAGER_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.NORTHBOUND_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.KILDA_NB_WORKER_REPLY_BOLT, ZkStreams.ZK.toString())
                .allGrouping(ComponentType.SPEAKER_REQUEST_BOLT, ZkStreams.ZK.toString())
                .allGrouping(RegionTrackerBolt.BOLT_ID, ZkStreams.ZK.toString())
                .allGrouping(SwitchMonitorBolt.BOLT_ID, ZkStreams.ZK.toString());
    }

    private void speakerToNetwork(TopologyBuilder topology, TopologyOutput output) {
        declareKafkaSpout(topology,
                makeRegionTopics(kafkaTopics.getTopoDiscoRegionTopic()), ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT);
        SpeakerToNetworkProxyBolt proxy = new SpeakerToNetworkProxyBolt(
                kafkaTopics.getTopoDiscoTopic(), Duration.ofSeconds(topologyConfig.getSwitchMappingRemoveDelay()));
        declareBolt(topology, proxy, SpeakerToNetworkProxyBolt.BOLT_ID)
                .allGrouping(SwitchMonitorBolt.BOLT_ID, SwitchMonitorBolt.STREAM_REGION_MAPPING_ID)
                .shuffleGrouping(ComponentType.KILDA_TOPO_DISCO_KAFKA_SPOUT)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);

        output.getKafkaGenericOutput()
                .shuffleGrouping(SpeakerToNetworkProxyBolt.BOLT_ID);
    }

    private void networkToSpeaker(TopologyBuilder topology, TopologyOutput output) {
        declareKafkaSpoutForAbstractMessage(topology,
                kafkaTopics.getNetworkControlTopic(), ComponentType.SPEAKER_NETWORK_KAFKA_SPOUT);
        declareControllerToSpeakerProxy(
                topology, kafkaTopics.getNetworkControlRegionTopic(),
                ComponentType.SPEAKER_NETWORK_KAFKA_SPOUT, ComponentType.SPEAKER_NETWORK_REQUEST_BOLT,
                output.getKafkaHsOutput());

        declareKafkaSpoutForAbstractMessage(topology,
                makeRegionTopics(kafkaTopics.getNetworkControlResponseRegionTopic()),
                ComponentType.KILDA_NETWORK_REQUEST_KAFKA_SPOUT);
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getNetworkControlResponseTopic(),
                ComponentType.KILDA_NETWORK_REQUEST_KAFKA_SPOUT,
                ComponentType.KILDA_NETWORK_RESPONSE_REPLY_BOLT,
                output.getKafkaHsOutput());

        declareControllerToSpeakerProxy(
                topology, kafkaTopics.getSpeakerDiscoRegionTopic(), kafkaTopics.getSpeakerDiscoTopic(),
                ComponentType.SPEAKER_DISCO_KAFKA_SPOUT, ComponentType.SPEAKER_DISCO_REQUEST_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToFlowHs(TopologyBuilder topology, TopologyOutput output) {
        declareKafkaSpoutForAbstractMessage(topology,
                makeRegionTopics(kafkaTopics.getFlowHsSpeakerRegionTopic()), ComponentType.KILDA_FLOW_HS_KAFKA_SPOUT);
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getFlowHsSpeakerTopic(),
                ComponentType.KILDA_FLOW_HS_KAFKA_SPOUT, ComponentType.KILDA_FLOW_HS_REPLY_BOLT,
                output.getKafkaHsOutput());
    }

    private void flowHsToSpeaker(TopologyBuilder topology, TopologyOutput output) {
        declareKafkaSpoutForAbstractMessage(topology,
                kafkaTopics.getSpeakerFlowHsTopic(), ComponentType.SPEAKER_FLOW_HS_KAFKA_SPOUT);

        declareControllerToSpeakerProxy(
                topology, kafkaTopics.getSpeakerFlowRegionTopic(),
                ComponentType.SPEAKER_FLOW_HS_KAFKA_SPOUT, ComponentType.SPEAKER_FLOW_REQUEST_BOLT,
                output.getKafkaHsOutput());
    }

    private void speakerToPing(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getPingRegionTopic(), kafkaTopics.getPingTopic(),
                ComponentType.KILDA_PING_KAFKA_SPOUT, ComponentType.KILDA_PING_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void pingToSpeaker(TopologyBuilder topology, TopologyOutput output) {
        declareControllerToSpeakerProxy(
                topology, kafkaTopics.getSpeakerFlowPingRegionTopic(), kafkaTopics.getSpeakerFlowPingTopic(),
                ComponentType.SPEAKER_PING_KAFKA_SPOUT, Stream.SPEAKER_PING,
                output.getKafkaGenericOutput());
    }

    private void speakerToStats(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getStatsRegionTopic(), kafkaTopics.getStatsTopic(),
                ComponentType.KILDA_STATS_KAFKA_SPOUT, ComponentType.KILDA_STATS_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToIslLatency(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getTopoIslLatencyRegionTopic(), kafkaTopics.getTopoIslLatencyTopic(),
                ComponentType.KILDA_ISL_LATENCY_KAFKA_SPOUT, ComponentType.KILDA_ISL_LATENCY_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToConnectedDevices(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getTopoConnectedDevicesRegionTopic(), kafkaTopics.getTopoConnectedDevicesTopic(),
                ComponentType.KILDA_CONNECTED_DEVICES_KAFKA_SPOUT, ComponentType.KILDA_CONNECTED_DEVICES_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToSwitchManager(TopologyBuilder topology, TopologyOutput output) {
        declareKafkaSpoutForAbstractMessage(topology,
                kafkaTopics.getSpeakerSwitchManagerTopic(), ComponentType.SPEAKER_SWITCH_MANAGER_KAFKA_SPOUT);
        declareControllerToSpeakerProxy(
                topology, kafkaTopics.getSpeakerSwitchManagerRegionTopic(),
                ComponentType.SPEAKER_SWITCH_MANAGER_KAFKA_SPOUT, ComponentType.SPEAKER_SWITCH_MANAGER_REQUEST_BOLT,
                output.getKafkaHsOutput());

        declareKafkaSpoutForAbstractMessage(topology,
                makeRegionTopics(kafkaTopics.getSwitchManagerSpeakerRegionTopic()),
                ComponentType.KILDA_SWITCH_MANAGER_REQUEST_KAFKA_SPOUT);
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getSwitchManagerSpeakerTopic(),
                ComponentType.KILDA_SWITCH_MANAGER_REQUEST_KAFKA_SPOUT,
                ComponentType.KILDA_SWITCH_MANAGER_RESPONSE_REPLY_BOLT,
                output.getKafkaHsOutput());

        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getTopoSwitchManagerRegionTopic(), kafkaTopics.getTopoSwitchManagerTopic(),
                ComponentType.KILDA_SWITCH_MANAGER_KAFKA_SPOUT, ComponentType.KILDA_SWITCH_MANAGER_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToNorthbound(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getNorthboundRegionTopic(), kafkaTopics.getNorthboundTopic(),
                ComponentType.NORTHBOUND_REPLY_KAFKA_SPOUT, ComponentType.NORTHBOUND_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void speakerToNbWorker(TopologyBuilder topology, TopologyOutput output) {
        declareSpeakerToControllerProxy(
                topology, kafkaTopics.getTopoNbRegionTopic(), kafkaTopics.getTopoNbTopic(),
                ComponentType.KILDA_NB_WORKER_KAFKA_SPOUT, ComponentType.KILDA_NB_WORKER_REPLY_BOLT,
                output.getKafkaGenericOutput());
    }

    private void controllerToSpeaker(
            TopologyBuilder topology, TopologyOutput output) {
        BoltDeclarer kafkaProducer = output.getKafkaGenericOutput();

        declareKafkaSpout(topology, kafkaTopics.getSpeakerTopic(), ComponentType.SPEAKER_KAFKA_SPOUT);

        ControllerToSpeakerProxyBolt proxy = new ControllerToSpeakerSharedProxyBolt(
                kafkaTopics.getSpeakerRegionTopic(), regions, kafkaTopics,
                Duration.ofSeconds(topologyConfig.getSwitchMappingRemoveDelay()));
        declareBolt(topology, proxy, ComponentType.SPEAKER_REQUEST_BOLT)
                .shuffleGrouping(ComponentType.SPEAKER_KAFKA_SPOUT)
                .allGrouping(SwitchMonitorBolt.BOLT_ID, SwitchMonitorBolt.STREAM_REGION_MAPPING_ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);

        kafkaProducer
                .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT)
                .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT, Stream.KILDA_SWITCH_MANAGER)
                .shuffleGrouping(ComponentType.SPEAKER_REQUEST_BOLT, Stream.NORTHBOUND_REPLY);
    }

    private void regionTracker(TopologyBuilder topology, TopologyOutput output) {
        RegionTrackerBolt bolt = new RegionTrackerBolt(
                kafkaTopics.getSpeakerDiscoRegionTopic(), persistenceManager, regions,
                topologyConfig.getFloodlightAliveTimeout(), topologyConfig.getFloodlightAliveInterval()
        );
        declareBolt(topology, bolt, RegionTrackerBolt.BOLT_ID)
                .allGrouping(MonotonicTick.BOLT_ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .shuffleGrouping(SpeakerToNetworkProxyBolt.BOLT_ID, SpeakerToNetworkProxyBolt.STREAM_ALIVE_EVIDENCE_ID);

        output.getKafkaGenericOutput()
                .shuffleGrouping(RegionTrackerBolt.BOLT_ID, RegionTrackerBolt.STREAM_SPEAKER_ID);
    }

    private void switchMonitor(TopologyBuilder topology, TopologyOutput output) {
        Fields switchIdGrouping = new Fields(SpeakerToNetworkProxyBolt.FIELD_ID_SWITCH_ID);

        SwitchMonitorBolt bolt = new SwitchMonitorBolt(ZooKeeperSpout.SPOUT_ID, kafkaTopics.getTopoDiscoTopic());
        declareBolt(topology, bolt, SwitchMonitorBolt.BOLT_ID)
                .allGrouping(MonotonicTick.BOLT_ID)
                .allGrouping(RegionTrackerBolt.BOLT_ID, RegionTrackerBolt.STREAM_REGION_NOTIFICATION_ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .fieldsGrouping(
                        SpeakerToNetworkProxyBolt.BOLT_ID, SpeakerToNetworkProxyBolt.STREAM_CONNECT_NOTIFICATION_ID,
                        switchIdGrouping);

        Fields keyGrouping = new Fields(FieldNameBasedTupleToKafkaMapper.BOLT_KEY);
        output.getKafkaGenericOutput()
                .fieldsGrouping(SwitchMonitorBolt.BOLT_ID, SwitchMonitorBolt.STREAM_NETWORK_ID, keyGrouping);
    }

    private void clock(TopologyBuilder topology) {
        MonotonicClock.ClockConfig<TickId> config = new MonotonicClock.ClockConfig<>();
        config.addTickInterval(TickId.NETWORK_DUMP, topologyConfig.getFloodlightDumpInterval());
        declareBolt(topology, new MonotonicTick(config), MonotonicTick.BOLT_ID);
    }

    private TopologyOutput kafkaOutput(TopologyBuilder topology) {
        RegionAwareKafkaTopicSelector topicSelector = new RegionAwareKafkaTopicSelector();
        BoltDeclarer generic = declareBolt(topology,
                makeKafkaBolt(MessageSerializer.class)
                        .withTopicSelector(topicSelector),
                ComponentType.KAFKA_GENERIC_OUTPUT);
        BoltDeclarer hs = declareBolt(topology,
                makeKafkaBolt(AbstractMessageSerializer.class)
                        .withTopicSelector(topicSelector),
                ComponentType.KAFKA_HS_OUTPUT);

        return new TopologyOutput(generic, hs);
    }

    private void declareSpeakerToControllerProxy(
            TopologyBuilder topology, String speakerTopicsSeed, String controllerTopic, String spoutId,
            String proxyBoltId, BoltDeclarer output) {
        declareKafkaSpout(topology, makeRegionTopics(speakerTopicsSeed), spoutId);

        declareSpeakerToControllerProxy(topology, controllerTopic, spoutId, proxyBoltId, output);
    }

    private void declareSpeakerToControllerProxy(
            TopologyBuilder topology, String controllerTopic, String spoutId, String proxyBoltId, BoltDeclarer output) {
        SpeakerToControllerProxyBolt proxy = new SpeakerToControllerProxyBolt(
                controllerTopic, Duration.ofSeconds(topologyConfig.getSwitchMappingRemoveDelay()));
        declareBolt(topology, proxy, proxyBoltId)
                .allGrouping(SwitchMonitorBolt.BOLT_ID, SwitchMonitorBolt.STREAM_REGION_MAPPING_ID)
                .shuffleGrouping(spoutId)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);

        output.shuffleGrouping(proxyBoltId);
    }

    private void declareControllerToSpeakerProxy(
            TopologyBuilder topology, String speakerTopicsSeed, String controllerTopic, String spoutId,
            String proxyBoltId, BoltDeclarer output) {
        declareKafkaSpout(topology, controllerTopic, spoutId);

        declareControllerToSpeakerProxy(
                topology, speakerTopicsSeed, spoutId, proxyBoltId, output);
    }

    private void declareControllerToSpeakerProxy(
            TopologyBuilder topology, String speakerTopicsSeed, String spoutId, String proxyBoltId,
            BoltDeclarer output) {
        ControllerToSpeakerProxyBolt proxy = new ControllerToSpeakerProxyBolt(
                speakerTopicsSeed, regions, Duration.ofSeconds(topologyConfig.getSwitchMappingRemoveDelay()));
        declareBolt(topology, proxy, proxyBoltId)
                .shuffleGrouping(spoutId)
                .allGrouping(SwitchMonitorBolt.BOLT_ID, SwitchMonitorBolt.STREAM_REGION_MAPPING_ID)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);


        output.shuffleGrouping(proxyBoltId);
    }

    private List<String> makeRegionTopics(String topicSeed) {
        List<String> regionTopics = new ArrayList<>(regions.size());
        for (String entry : regions) {
            regionTopics.add(RegionAwareKafkaTopicSelector.formatTopicName(topicSeed, entry));
        }
        return regionTopics;
    }

    @Override
    protected String getZkTopoName() {
        return "floodlightrouter";
    }

    @Value
    private static class TopologyOutput {
        BoltDeclarer kafkaGenericOutput;
        BoltDeclarer kafkaHsOutput;
    }

    /**
     * Topology entry point.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new FloodlightRouterTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
