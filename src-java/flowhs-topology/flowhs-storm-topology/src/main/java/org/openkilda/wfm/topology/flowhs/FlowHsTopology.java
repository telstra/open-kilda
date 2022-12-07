/* Copyright 2021 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs;

import static org.openkilda.wfm.share.hubandspoke.CoordinatorBolt.FIELDS_KEY;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_DELETE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_PATH_SWAP_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_FLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_READ;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_REROUTE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_UPDATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_REQUEST_SENDER;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_VALIDATION;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_YFLOW_VALIDATION;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SWAP_ENDPOINTS_HUB_TO_ROUTER_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.UPDATE_HUB_TO_SWAP_ENDPOINTS_HUB;
import static org.openkilda.wfm.topology.flowhs.bolts.RouterBolt.FLOW_ID_FIELD;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.rulemanager.RuleManagerConfig;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.kafka.AbstractMessageSerializer;
import org.openkilda.wfm.kafka.MessageSerializer;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt.Config;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowCreateHubBolt.FlowCreateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowDeleteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowDeleteHubBolt.FlowDeleteConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowMirrorPointCreateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowMirrorPointCreateHubBolt.FlowMirrorPointCreateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowMirrorPointDeleteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowMirrorPointDeleteHubBolt.FlowMirrorPointDeleteConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowPathSwapHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowPathSwapHubBolt.FlowPathSwapConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowRerouteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowRerouteHubBolt.FlowRerouteConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowSwapEndpointsHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowSyncHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowUpdateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowUpdateHubBolt.FlowUpdateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.FlowValidationHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.RouterBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SpeakerWorkerForDumpsBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SyncHubBoltBase;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowCreateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowCreateHubBolt.YFlowCreateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowDeleteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowDeleteHubBolt.YFlowDeleteConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowPathSwapHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowPathSwapHubBolt.YFlowPathSwapConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowReadBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowReadBolt.YFlowReadConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowRerouteHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowRerouteHubBolt.YFlowRerouteConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowSyncHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowUpdateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowUpdateHubBolt.YFlowUpdateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowValidationHubBolt;
import org.openkilda.wfm.topology.utils.KafkaRecordTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.kafka.spout.KafkaSpoutConfig.ProcessingGuarantee;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class FlowHsTopology extends AbstractTopology<FlowHsTopologyConfig> {
    private static final Fields FLOW_FIELD = new Fields(FLOW_ID_FIELD);

    public FlowHsTopology(LaunchEnvironment env) {
        super(env, "flowhs-topology", FlowHsTopologyConfig.class);
    }

    @Override
    public StormTopology createTopology() {
        TopologyBuilder tb = new TopologyBuilder();

        inputSpout(tb);
        inputRouter(tb);

        PersistenceManager persistenceManager = new PersistenceManager(configurationProvider);

        flowCreateHub(tb, persistenceManager);
        flowUpdateHub(tb, persistenceManager);
        flowRerouteHub(tb, persistenceManager);
        flowDeleteHub(tb, persistenceManager);
        flowSyncHub(tb, persistenceManager);
        flowSwapProtectedHub(tb, persistenceManager);
        flowSwapEndpointsHub(tb, persistenceManager);
        flowCreateMirrorPointHub(tb, persistenceManager);
        flowDeleteMirrorPointHub(tb, persistenceManager);
        flowValidationHub(tb, persistenceManager);
        yFlowCreateHub(tb, persistenceManager);
        yFlowUpdateHub(tb, persistenceManager);
        yFlowRerouteHub(tb, persistenceManager);
        yFlowDeleteHub(tb, persistenceManager);
        yFlowSyncHub(tb, persistenceManager);
        yFlowReadBolt(tb, persistenceManager);
        yFlowValidationHub(tb, persistenceManager);
        yFlowPathSwapHub(tb, persistenceManager);

        speakerSpout(tb);
        speakerWorkers(tb);
        flowValidationSpeakerWorker(tb);
        yFlowValidationSpeakerWorker(tb);
        speakerOutput(tb);
        speakerOutputForDumps(tb);

        coordinator(tb);

        northboundOutput(tb);
        rerouteTopologyOutput(tb);
        pingOutput(tb);
        server42ControlTopologyOutput(tb);
        flowMonitoringTopologyOutput(tb);
        statsTopologyOutput(tb);

        history(tb);

        zkSpout(tb);
        zkBolt(tb);

        metrics(tb);

        return tb.createTopology();
    }

    private void zkSpout(TopologyBuilder topologyBuilder) {
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig());
        declareSpout(topologyBuilder, zooKeeperSpout, ZooKeeperSpout.SPOUT_ID);
    }

    private void zkBolt(TopologyBuilder topologyBuilder) {
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt(getConfig().getBlueGreenMode(), getZkTopoName(),
                getZookeeperConfig(),
                getBoltInstancesCount(ComponentId.FLOW_CREATE_HUB.name(), ComponentId.FLOW_UPDATE_HUB.name(),
                        ComponentId.FLOW_DELETE_HUB.name(), FlowSyncHubBolt.BOLT_ID,
                        ComponentId.FLOW_PATH_SWAP_HUB.name(),
                        ComponentId.FLOW_REROUTE_HUB.name(), ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(),
                        ComponentId.FLOW_ROUTER_BOLT.name(), ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        ComponentId.FLOW_VALIDATION_HUB.name(),
                        ComponentId.YFLOW_CREATE_HUB.name(),
                        ComponentId.YFLOW_UPDATE_HUB.name(),
                        ComponentId.YFLOW_REROUTE_HUB.name(),
                        YFlowSyncHubBolt.BOLT_ID,
                        ComponentId.YFLOW_DELETE_HUB.name(),
                        ComponentId.YFLOW_READ_BOLT.name(),
                        ComponentId.YFLOW_VALIDATION_HUB.name(),
                        ComponentId.YFLOW_PATH_SWAP_HUB.name()));
        declareBolt(topologyBuilder, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(ComponentId.FLOW_CREATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_UPDATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_DELETE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_ZK)
                .allGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_REROUTE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_VALIDATION_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_CREATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_UPDATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_REROUTE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_ZK)
                .allGrouping(ComponentId.YFLOW_DELETE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_READ_BOLT.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_VALIDATION_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_PATH_SWAP_HUB.name(), ZkStreams.ZK.toString());
    }

    private void inputSpout(TopologyBuilder topologyBuilder) {
        String spoutId = ComponentId.FLOW_SPOUT.name();
        KafkaSpoutConfig<String, Message> spoutConfig = getKafkaSpoutConfigBuilder(
                getConfig().getKafkaFlowHsTopic(), spoutId)
                .setProcessingGuarantee(ProcessingGuarantee.AT_MOST_ONCE)
                .setTupleTrackingEnforced(true)
                .build();
        declareKafkaSpout(topologyBuilder, spoutConfig, spoutId);
    }

    private void inputRouter(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder, new RouterBolt(ZooKeeperSpout.SPOUT_ID), ComponentId.FLOW_ROUTER_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_SPOUT.name())
                .shuffleGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), SWAP_ENDPOINTS_HUB_TO_ROUTER_BOLT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void flowCreateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateHubTimeoutSeconds());

        FlowCreateConfig config = FlowCreateConfig.flowCreateBuilder()
                .flowCreationRetriesLimit(topologyConfig.getCreateHubRetries())
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowCreateHubBolt hubBolt = new FlowCreateHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_CREATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_CREATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), Stream.SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowUpdateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getUpdateHubTimeoutSeconds());

        FlowUpdateConfig config = FlowUpdateConfig.flowUpdateBuilder()
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getUpdateSpeakerCommandRetries())
                .resourceAllocationRetriesLimit(topologyConfig.getResourceAllocationRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowUpdateHubBolt hubBolt = new FlowUpdateHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_UPDATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_UPDATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowSwapProtectedHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getPathSwapHubTimeoutSeconds());

        FlowPathSwapConfig config = FlowPathSwapConfig.flowPathSwapBuilder()
                .speakerCommandRetriesLimit(topologyConfig.getPathSwapSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        FlowPathSwapHubBolt hubBolt = new FlowPathSwapHubBolt(config, persistenceManager, flowResourcesConfig,
                ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_PATH_SWAP_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_PATH_SWAP_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowRerouteHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getRerouteHubTimeoutSeconds());

        FlowRerouteConfig config = FlowRerouteConfig.flowRerouteBuilder()
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getRerouteSpeakerCommandRetries())
                .resourceAllocationRetriesLimit(topologyConfig.getResourceAllocationRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowRerouteHubBolt hubBolt = new FlowRerouteHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_REROUTE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_REROUTE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowDeleteHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getDeleteHubTimeoutSeconds());

        FlowDeleteConfig config = FlowDeleteConfig.flowDeleteBuilder()
                .speakerCommandRetriesLimit(topologyConfig.getDeleteSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowDeleteHubBolt hubBolt = new FlowDeleteHubBolt(config, persistenceManager, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_DELETE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_DELETE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowSyncHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowSyncHubBolt hubBolt = new FlowSyncHubBolt(newSyncHubConfig(), persistenceManager, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, FlowSyncHubBolt.BOLT_ID)
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), Stream.ROUTER_TO_FLOW_SYNC_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), Stream.SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowSwapEndpointsHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getSwapEndpointsHubTimeoutSeconds());

        HubBolt.Config config = HubBolt.Config.builder()
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.FLOW_UPDATE_HUB.name())
                .timeoutMs(hubTimeout)
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowSwapEndpointsHubBolt hubBolt = new FlowSwapEndpointsHubBolt(config, persistenceManager);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(),
                        UPDATE_HUB_TO_SWAP_ENDPOINTS_HUB.name(), FIELDS_KEY)
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowCreateMirrorPointHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateMirrorPointHubTimeoutSeconds());

        FlowMirrorPointCreateConfig config = FlowMirrorPointCreateConfig.flowMirrorPointCreateBuilder()
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateMirrorPointSpeakerCommandRetries())
                .resourceAllocationRetriesLimit(topologyConfig.getResourceAllocationRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        FlowMirrorPointCreateHubBolt hubBolt = new FlowMirrorPointCreateHubBolt(config, persistenceManager,
                pathComputerConfig, flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowDeleteMirrorPointHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getDeleteMirrorPointHubTimeoutSeconds());

        FlowMirrorPointDeleteConfig config = FlowMirrorPointDeleteConfig.flowMirrorPointDeleteBuilder()
                .speakerCommandRetriesLimit(topologyConfig.getDeleteMirrorPointSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        FlowMirrorPointDeleteHubBolt hubBolt = new FlowMirrorPointDeleteHubBolt(config, persistenceManager,
                flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowValidationHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getValidationHubTimeoutSeconds());

        HubBolt.Config config = HubBolt.Config.builder()
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.FLOW_VALIDATION_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .timeoutMs(hubTimeout)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowValidationHubBolt hubBolt = new FlowValidationHubBolt(config, persistenceManager, flowResourcesConfig,
                topologyConfig.getFlowMeterMinBurstSizeInKbits(),
                topologyConfig.getFlowMeterBurstCoefficient());
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_VALIDATION_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        Stream.ROUTER_TO_FLOW_VALIDATION_HUB.name(), FIELDS_KEY)
                .directGrouping(ComponentId.FLOW_VALIDATION_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_VALIDATION.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowCreateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateHubTimeoutSeconds());

        FlowCreateConfig config = FlowCreateConfig.flowCreateBuilder()
                .flowCreationRetriesLimit(topologyConfig.getCreateHubRetries())
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        YFlowCreateConfig yFlowCreateConfig = YFlowCreateConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getYFlowCreateSpeakerCommandRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);

        YFlowCreateHubBolt hubBolt = new YFlowCreateHubBolt(yFlowCreateConfig, config, persistenceManager,
                pathComputerConfig, flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_CREATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        Stream.ROUTER_TO_YFLOW_CREATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowUpdateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getUpdateHubTimeoutSeconds());

        FlowUpdateConfig flowUpdateConfig = FlowUpdateConfig.flowUpdateBuilder()
                .resourceAllocationRetriesLimit(topologyConfig.getResourceAllocationRetriesLimit())
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        YFlowUpdateConfig yflowUpdateConfig = YFlowUpdateConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);

        YFlowUpdateHubBolt hubBolt = new YFlowUpdateHubBolt(yflowUpdateConfig, flowUpdateConfig, persistenceManager,
                pathComputerConfig, flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_UPDATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_YFLOW_UPDATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowRerouteHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getRerouteHubTimeoutSeconds());

        FlowRerouteConfig flowRerouteConfig = FlowRerouteConfig.flowRerouteBuilder()
                .resourceAllocationRetriesLimit(topologyConfig.getResourceAllocationRetriesLimit())
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        YFlowRerouteConfig yFlowRerouteConfig = YFlowRerouteConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);

        YFlowRerouteHubBolt hubBolt = new YFlowRerouteHubBolt(yFlowRerouteConfig, flowRerouteConfig, persistenceManager,
                pathComputerConfig, flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_REROUTE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_YFLOW_REROUTE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowDeleteHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getDeleteHubTimeoutSeconds());

        YFlowDeleteConfig config = YFlowDeleteConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getYFlowDeleteSpeakerCommandRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);

        YFlowDeleteHubBolt hubBolt = new YFlowDeleteHubBolt(config, persistenceManager, flowResourcesConfig,
                ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_DELETE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        Stream.ROUTER_TO_YFLOW_DELETE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowSyncHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);
        YFlowSyncHubBolt hubBolt = new YFlowSyncHubBolt(
                newSyncHubConfig(), persistenceManager, flowResourcesConfig, ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, YFlowSyncHubBolt.BOLT_ID)
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), Stream.ROUTER_TO_YFLOW_SYNC_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), Stream.SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowReadBolt(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        YFlowReadConfig yFlowReadConfig = YFlowReadConfig.builder()
                .readOperationRetriesLimit(topologyConfig.getYFlowReadRetriesLimit())
                .readOperationRetryDelay(Duration.ofMillis(topologyConfig.getYFlowReadRetryDelayMillis()))
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        YFlowReadBolt bolt = new YFlowReadBolt(yFlowReadConfig, persistenceManager);
        declareBolt(topologyBuilder, bolt, ComponentId.YFLOW_READ_BOLT.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_YFLOW_READ.name(), FIELDS_KEY)
                .allGrouping(ZooKeeperSpout.SPOUT_ID);
    }

    private void yFlowValidationHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getValidationHubTimeoutSeconds());

        HubBolt.Config config = HubBolt.Config.builder()
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.YFLOW_VALIDATION_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .timeoutMs(hubTimeout)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);

        YFlowValidationHubBolt hubBolt = new YFlowValidationHubBolt(config, persistenceManager, flowResourcesConfig,
                topologyConfig.getFlowMeterMinBurstSizeInKbits(),
                topologyConfig.getFlowMeterBurstCoefficient());
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_VALIDATION_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        Stream.ROUTER_TO_YFLOW_VALIDATION_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.YFLOW_VALIDATION_SPEAKER_WORKER.name(),
                        SPEAKER_WORKER_TO_HUB_YFLOW_VALIDATION.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowPathSwapHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getYFlowPathSwapHubTimeoutSeconds());

        YFlowPathSwapConfig config = YFlowPathSwapConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getYFlowPathSwapSpeakerCommandRetriesLimit())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        RuleManagerConfig ruleManagerConfig = configurationProvider.getConfiguration(RuleManagerConfig.class);

        YFlowPathSwapHubBolt hubBolt = new YFlowPathSwapHubBolt(config, persistenceManager, flowResourcesConfig,
                ruleManagerConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_PATH_SWAP_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        Stream.ROUTER_TO_YFLOW_PATH_SWAP_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.SPEAKER_WORKER.name(), SPEAKER_WORKER_TO_HUB.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerSpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpoutForAbstractMessage(topologyBuilder, getConfig().getKafkaFlowSpeakerWorkerTopic(),
                ComponentId.SPEAKER_WORKER_SPOUT.name());
    }

    private void speakerWorkers(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_CREATE_HUB.name())
                .hubComponent(ComponentId.FLOW_UPDATE_HUB.name())
                .hubComponent(ComponentId.FLOW_PATH_SWAP_HUB.name())
                .hubComponent(ComponentId.FLOW_REROUTE_HUB.name())
                .hubComponent(ComponentId.FLOW_DELETE_HUB.name())
                .hubComponent(FlowSyncHubBolt.BOLT_ID)
                .hubComponent(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name())
                .hubComponent(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name())
                .hubComponent(ComponentId.YFLOW_CREATE_HUB.name())
                .hubComponent(ComponentId.YFLOW_UPDATE_HUB.name())
                .hubComponent(ComponentId.YFLOW_REROUTE_HUB.name())
                .hubComponent(ComponentId.YFLOW_DELETE_HUB.name())
                .hubComponent(YFlowSyncHubBolt.BOLT_ID)
                .hubComponent(ComponentId.YFLOW_PATH_SWAP_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_WORKER, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_UPDATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_REROUTE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_WORKER, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_DELETE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowValidationSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getValidationSpeakerTimeoutSeconds());
        Config speakerWorkerConfig = Config.builder()
                .hubComponent(ComponentId.FLOW_VALIDATION_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_VALIDATION.name())
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .defaultTimeout(speakerTimeout)
                .build();
        SpeakerWorkerForDumpsBolt speakerWorker = new SpeakerWorkerForDumpsBolt(speakerWorkerConfig);
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_VALIDATION_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_VALIDATION_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(), FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowValidationSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getYFlowValidationSpeakerTimeoutSeconds());
        Config speakerWorkerConfig = Config.builder()
                .hubComponent(ComponentId.YFLOW_VALIDATION_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_YFLOW_VALIDATION.name())
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .defaultTimeout(speakerTimeout)
                .build();
        SpeakerWorkerForDumpsBolt speakerWorker = new SpeakerWorkerForDumpsBolt(speakerWorkerConfig);
        declareBolt(topologyBuilder, speakerWorker, ComponentId.YFLOW_VALIDATION_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_VALIDATION_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt<String, AbstractMessage> flKafkaBolt = makeKafkaBolt(
                getConfig().getKafkaSpeakerFlowTopic(), AbstractMessageSerializer.class);
        declareBolt(topologyBuilder, flKafkaBolt, ComponentId.SPEAKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name());
    }

    private void speakerOutputForDumps(TopologyBuilder topologyBuilder) {
        declareBolt(topologyBuilder, buildKafkaBolt(topologyConfig.getKafkaSpeakerTopic()),
                ComponentId.SPEAKER_DUMP_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_VALIDATION_SPEAKER_WORKER.name(),
                        SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_VALIDATION_SPEAKER_WORKER.name(),
                        SPEAKER_WORKER_REQUEST_SENDER.name());
    }

    private void coordinator(TopologyBuilder topologyBuilder) {
        declareSpout(topologyBuilder, new CoordinatorSpout(), CoordinatorSpout.ID);
        declareBolt(topologyBuilder, new CoordinatorBolt(), CoordinatorBolt.ID)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(ComponentId.SPEAKER_WORKER.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_VALIDATION_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(FlowSyncHubBolt.BOLT_ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_VALIDATION_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_UPDATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_DELETE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(YFlowSyncHubBolt.BOLT_ID, CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_VALIDATION_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_VALIDATION_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_PATH_SWAP_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);
    }

    private void northboundOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt nbKafkaBolt = buildKafkaBolt(getConfig().getKafkaNorthboundTopic());
        declareBolt(topologyBuilder, nbKafkaBolt, ComponentId.NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_NB_RESPONSE)
                .shuffleGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_VALIDATION_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_NB_RESPONSE)
                .shuffleGrouping(ComponentId.YFLOW_READ_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_VALIDATION_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name());
    }

    private void rerouteTopologyOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt rerouteKafkaBolt = buildKafkaBolt(getConfig().getKafkaRerouteTopic());
        declareBolt(topologyBuilder, rerouteKafkaBolt, ComponentId.REROUTE_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_REROUTE_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(), Stream.HUB_TO_REROUTE_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_REROUTE_RESPONSE_SENDER.name());
    }

    private void pingOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt pingKafkaBolt = buildKafkaBolt(getConfig().getKafkaPingTopic());
        declareBolt(topologyBuilder, pingKafkaBolt, ComponentId.FLOW_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_PING_REQUEST)
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_PING_REQUEST);
    }

    private void server42ControlTopologyOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt server42ControlKafkaBolt = buildKafkaBolt(getConfig().getKafkaFlowHsServer42StormNotifyTopic());
        declareBolt(topologyBuilder, server42ControlKafkaBolt, ComponentId.SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER.name());
    }

    private void flowMonitoringTopologyOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt flowMonitoringKafkaBolt = buildKafkaBolt(getConfig().getFlowHsFlowMonitoringNotifyTopic());
        declareBolt(topologyBuilder, flowMonitoringKafkaBolt, ComponentId.FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_FLOW_MONITORING)
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name())
                .shuffleGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_FLOW_MONITORING);
    }

    private void statsTopologyOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt statsNotifyKafkaBolt = buildKafkaBolt(getConfig().getFlowStatsNotifyTopic());
        declareBolt(topologyBuilder, statsNotifyKafkaBolt, ComponentId.STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_STATS)
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_STATS)
                .shuffleGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name());
    }

    private void history(TopologyBuilder topologyBuilder) {
        KafkaBolt<String, Message> kafkaBolt = makeKafkaBolt(
                topologyConfig.getKafkaHistoryTopic(), MessageSerializer.class);
        Fields grouping = new Fields(KafkaRecordTranslator.FIELD_ID_KEY);
        declareBolt(topologyBuilder, kafkaBolt, ComponentId.HISTORY_BOLT.name())
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(FlowSyncHubBolt.BOLT_ID, FlowSyncHubBolt.STREAM_HISTORY, grouping)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(),
                        Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping)
                .fieldsGrouping(
                        ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(
                        ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(),
                        grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_UPDATE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_REROUTE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_DELETE_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping)
                .fieldsGrouping(YFlowSyncHubBolt.BOLT_ID, YFlowSyncHubBolt.STREAM_HISTORY, grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_HISTORY_TOPOLOGY_SENDER.name(), grouping);
    }

    @Override
    protected String getZkTopoName() {
        return "flowhs";
    }

    private void metrics(TopologyBuilder topologyBuilder) {
        String openTsdbTopic = topologyConfig.getKafkaTopics().getOtsdbTopic();
        KafkaBolt kafkaBolt = createKafkaBolt(openTsdbTopic);
        declareBolt(topologyBuilder, kafkaBolt, ComponentId.METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.FLOW_VALIDATION_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_UPDATE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_REROUTE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_DELETE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_VALIDATION_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name())
                .shuffleGrouping(ComponentId.YFLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name());
    }

    private SyncHubBoltBase.SyncHubConfig newSyncHubConfig() {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getSyncHubTimeoutSeconds());

        return FlowSyncHubBolt.SyncHubConfig.syncHubConfigBuilder()
                .speakerCommandRetriesLimit(topologyConfig.getSyncSpeakerCommandRetries())
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .timeoutMs(hubTimeout)
                .autoAck(true)
                .build();
    }

    public enum ComponentId {
        FLOW_SPOUT("flow.spout"),
        SPEAKER_WORKER_SPOUT("fl.worker.spout"),

        FLOW_ROUTER_BOLT("flow.router.bolt"),

        FLOW_CREATE_HUB("flow.create.hub.bolt"),
        FLOW_UPDATE_HUB("flow.update.hub.bolt"),
        FLOW_PATH_SWAP_HUB("flow.pathswap.hub.bolt"),
        FLOW_REROUTE_HUB("flow.reroute.hub.bolt"),
        FLOW_DELETE_HUB("flow.delete.hub.bolt"),
        FLOW_SYNC_HUB("flow.sync.hub.bolt"),
        FLOW_SWAP_ENDPOINTS_HUB("flow.swap.endpoints.hub.bolt"),
        FLOW_CREATE_MIRROR_POINT_HUB("flow.create.mirror.point.hub.bolt"),
        FLOW_DELETE_MIRROR_POINT_HUB("flow.create.mirror.point.hub.bolt"),
        FLOW_VALIDATION_HUB("flow.validation.hub.bolt"),

        SPEAKER_WORKER("speaker.worker.bolt"),
        FLOW_VALIDATION_SPEAKER_WORKER("flow.validation.worker.bolt"),

        YFLOW_CREATE_HUB("y_flow.create.hub.bolt"),
        YFLOW_UPDATE_HUB("y_flow.update.hub.bolt"),
        YFLOW_REROUTE_HUB("y_flow.reroute.hub.bolt"),
        YFLOW_DELETE_HUB("y_flow.delete.hub.bolt"),
        YFLOW_SYNC_HUB("y_flow.sync.hub.bolt"),
        YFLOW_READ_BOLT("y_flow.read.bolt"),
        YFLOW_VALIDATION_HUB("y_flow.validation.hub.bolt"),
        YFLOW_PATH_SWAP_HUB("y_flow.pathswap.hub.bolt"),

        YFLOW_VALIDATION_SPEAKER_WORKER("y_flow.validation.worker.bolt"),

        NB_RESPONSE_SENDER("nb.kafka.bolt"),
        REROUTE_RESPONSE_SENDER("reroute.kafka.bolt"),
        RESPONSE_SENDER("response.kafka.bolt"),
        FLOW_PING_SENDER("ping.kafka.bolt"),
        SERVER42_CONTROL_TOPOLOGY_SENDER("server42.control.kafka.bolt"),
        FLOW_MONITORING_TOPOLOGY_SENDER("flow.monitoring.kafka.bolt"),
        STATS_TOPOLOGY_SENDER("stats.kafka.bolt"),

        SPEAKER_REQUEST_SENDER("speaker.kafka.bolt"),
        SPEAKER_DUMP_REQUEST_SENDER("speaker.dumps.kafka.bolt"),

        HISTORY_BOLT("flow.history.bolt"),

        METRICS_BOLT("metrics.bolt");

        private final String value;

        ComponentId(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

    }

    public enum Stream {
        ROUTER_TO_FLOW_CREATE_HUB,
        ROUTER_TO_FLOW_UPDATE_HUB,
        ROUTER_TO_FLOW_PATH_SWAP_HUB,
        ROUTER_TO_FLOW_REROUTE_HUB,
        ROUTER_TO_FLOW_DELETE_HUB,
        ROUTER_TO_FLOW_SYNC_HUB,
        ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB,
        ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB,
        ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB,
        ROUTER_TO_FLOW_VALIDATION_HUB,
        ROUTER_TO_YFLOW_CREATE_HUB,
        ROUTER_TO_YFLOW_UPDATE_HUB,
        ROUTER_TO_YFLOW_REROUTE_HUB,
        ROUTER_TO_YFLOW_DELETE_HUB,
        ROUTER_TO_YFLOW_SYNC_HUB,
        ROUTER_TO_YFLOW_READ,
        ROUTER_TO_YFLOW_VALIDATION_HUB,
        ROUTER_TO_YFLOW_PATH_SWAP_HUB,

        HUB_TO_SPEAKER_WORKER,
        HUB_TO_HISTORY_TOPOLOGY_SENDER,
        HUB_TO_METRICS_BOLT,

        SPEAKER_WORKER_TO_HUB,
        SPEAKER_WORKER_TO_HUB_VALIDATION,
        SPEAKER_WORKER_TO_HUB_YFLOW_VALIDATION,

        SWAP_ENDPOINTS_HUB_TO_ROUTER_BOLT,
        UPDATE_HUB_TO_SWAP_ENDPOINTS_HUB,

        SPEAKER_WORKER_REQUEST_SENDER,
        HUB_TO_NB_RESPONSE_SENDER,
        HUB_TO_REROUTE_RESPONSE_SENDER,
        HUB_TO_RESPONSE_SENDER,
        HUB_TO_PING_SENDER,
        HUB_TO_SERVER42_CONTROL_TOPOLOGY_SENDER,
        HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER,
        HUB_TO_STATS_TOPOLOGY_SENDER
    }

    /**
     * Launches and sets up the topology.
     *
     * @param args the command-line arguments.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            new FlowHsTopology(env).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
