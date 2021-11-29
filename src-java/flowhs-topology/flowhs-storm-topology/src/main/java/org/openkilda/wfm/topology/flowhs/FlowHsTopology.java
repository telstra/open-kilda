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
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.ROUTER_TO_YFLOW_CREATE_HUB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_CREATE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_CREATE_MIRROR_POINT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_DELETE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_DELETE_MIRROR_POINT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_PATH_SWAP;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_REROUTE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_UPDATE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SPEAKER_WORKER_TO_HUB_YFLOW_CREATE;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.SWAP_ENDPOINTS_HUB_TO_ROUTER_BOLT;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.UPDATE_HUB_TO_SWAP_ENDPOINTS_HUB;
import static org.openkilda.wfm.topology.flowhs.bolts.RouterBolt.FLOW_ID_FIELD;

import org.openkilda.messaging.AbstractMessage;
import org.openkilda.messaging.Message;
import org.openkilda.pce.PathComputerConfig;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.kafka.AbstractMessageSerializer;
import org.openkilda.wfm.share.flow.resources.FlowResourcesConfig;
import org.openkilda.wfm.share.history.bolt.HistoryBolt;
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
import org.openkilda.wfm.topology.flowhs.bolts.FlowUpdateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.FlowUpdateHubBolt.FlowUpdateConfig;
import org.openkilda.wfm.topology.flowhs.bolts.RouterBolt;
import org.openkilda.wfm.topology.flowhs.bolts.SpeakerWorkerBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowCreateHubBolt;
import org.openkilda.wfm.topology.flowhs.bolts.YFlowCreateHubBolt.YFlowCreateConfig;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.kafka.spout.KafkaSpoutConfig.ProcessingGuarantee;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

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
        flowSwapProtectedHub(tb, persistenceManager);
        flowSwapEndpointsHub(tb, persistenceManager);
        flowCreateMirrorPointHub(tb, persistenceManager);
        flowDeleteMirrorPointHub(tb, persistenceManager);
        yFlowCreateHub(tb, persistenceManager);

        speakerSpout(tb);
        flowCreateSpeakerWorker(tb);
        flowUpdateSpeakerWorker(tb);
        flowRerouteSpeakerWorker(tb);
        flowDeleteSpeakerWorker(tb);
        flowPathSwapSpeakerWorker(tb);
        flowCreateMirrorPointSpeakerWorker(tb);
        flowDeleteMirrorPointSpeakerWorker(tb);
        yFlowCreateSpeakerWorker(tb);
        speakerOutput(tb);

        coordinator(tb);

        northboundOutput(tb);
        rerouteTopologyOutput(tb);
        pingOutput(tb);
        server42ControlTopologyOutput(tb);
        flowMonitoringTopologyOutput(tb);
        statsTopologyOutput(tb);

        history(tb, persistenceManager);

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
                        ComponentId.FLOW_DELETE_HUB.name(), ComponentId.FLOW_PATH_SWAP_HUB.name(),
                        ComponentId.FLOW_REROUTE_HUB.name(), ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(),
                        ComponentId.FLOW_ROUTER_BOLT.name(), ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        ComponentId.YFLOW_CREATE_HUB.name()));
        declareBolt(topologyBuilder, zooKeeperBolt, ZooKeeperBolt.BOLT_ID)
                .allGrouping(ComponentId.FLOW_CREATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_UPDATE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_DELETE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_REROUTE_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), ZkStreams.ZK.toString())
                .allGrouping(ComponentId.YFLOW_CREATE_HUB.name(), ZkStreams.ZK.toString());
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
                .workerComponent(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowCreateHubBolt hubBolt = new FlowCreateHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_CREATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_CREATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_CREATE.name())
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
                .workerComponent(ComponentId.FLOW_UPDATE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowUpdateHubBolt hubBolt = new FlowUpdateHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_UPDATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_UPDATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_UPDATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_UPDATE.name())
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
                .workerComponent(ComponentId.FLOW_PATH_SWAP_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowPathSwapHubBolt hubBolt = new FlowPathSwapHubBolt(config, persistenceManager, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_PATH_SWAP_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_PATH_SWAP_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_PATH_SWAP_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_PATH_SWAP.name())
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
                .workerComponent(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowRerouteHubBolt hubBolt = new FlowRerouteHubBolt(config, persistenceManager, pathComputerConfig,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_REROUTE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_REROUTE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_REROUTE.name())
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
                .workerComponent(ComponentId.FLOW_DELETE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowDeleteHubBolt hubBolt = new FlowDeleteHubBolt(config, persistenceManager, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_DELETE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_FLOW_DELETE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_DELETE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_DELETE.name())
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
                .workerComponent(ComponentId.FLOW_CREATE_MIRROR_POINT_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowMirrorPointCreateHubBolt hubBolt = new FlowMirrorPointCreateHubBolt(config, persistenceManager,
                pathComputerConfig, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_CREATE_MIRROR_POINT.name())
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
                .workerComponent(ComponentId.FLOW_DELETE_MIRROR_POINT_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);
        FlowMirrorPointDeleteHubBolt hubBolt = new FlowMirrorPointDeleteHubBolt(config, persistenceManager,
                flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(),
                        ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_DELETE_MIRROR_POINT.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowCreateHub(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        int hubTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateHubTimeoutSeconds());

        FlowCreateConfig flowCreateConfig = FlowCreateConfig.flowCreateBuilder()
                .flowCreationRetriesLimit(topologyConfig.getCreateHubRetries())
                .pathAllocationRetriesLimit(topologyConfig.getPathAllocationRetriesLimit())
                .pathAllocationRetryDelay(topologyConfig.getPathAllocationRetryDelay())
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        YFlowCreateConfig yFlowCreateConfig = YFlowCreateConfig.builder()
                .speakerCommandRetriesLimit(topologyConfig.getCreateSpeakerCommandRetries())
                .autoAck(true)
                .timeoutMs(hubTimeout)
                .requestSenderComponent(ComponentId.FLOW_ROUTER_BOLT.name())
                .workerComponent(ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name())
                .lifeCycleEventComponent(ZooKeeperSpout.SPOUT_ID)
                .build();

        PathComputerConfig pathComputerConfig = configurationProvider.getConfiguration(PathComputerConfig.class);
        FlowResourcesConfig flowResourcesConfig = configurationProvider.getConfiguration(FlowResourcesConfig.class);

        YFlowCreateHubBolt hubBolt = new YFlowCreateHubBolt(yFlowCreateConfig, flowCreateConfig, persistenceManager,
                pathComputerConfig, flowResourcesConfig);
        declareBolt(topologyBuilder, hubBolt, ComponentId.YFLOW_CREATE_HUB.name())
                .fieldsGrouping(ComponentId.FLOW_ROUTER_BOLT.name(), ROUTER_TO_YFLOW_CREATE_HUB.name(), FLOW_FIELD)
                .directGrouping(ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_TO_HUB_YFLOW_CREATE.name())
                .allGrouping(ZooKeeperSpout.SPOUT_ID)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerSpout(TopologyBuilder topologyBuilder) {
        declareKafkaSpoutForAbstractMessage(topologyBuilder, getConfig().getKafkaFlowSpeakerWorkerTopic(),
                ComponentId.SPEAKER_WORKER_SPOUT.name());
    }

    private void flowCreateSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_CREATE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_CREATE.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_CREATE_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowUpdateSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getUpdateSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_UPDATE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_UPDATE.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_UPDATE_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowPathSwapSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getPathSwapSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_PATH_SWAP_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_PATH_SWAP.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_PATH_SWAP_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowRerouteSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getRerouteSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_REROUTE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_REROUTE.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowDeleteSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getDeleteSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_DELETE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_DELETE.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_DELETE_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowCreateMirrorPointSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout
                = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateMirrorPointSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_CREATE_MIRROR_POINT.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_CREATE_MIRROR_POINT_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void flowDeleteMirrorPointSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout
                = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getDeleteMirrorPointSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_DELETE_MIRROR_POINT.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.FLOW_DELETE_MIRROR_POINT_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void yFlowCreateSpeakerWorker(TopologyBuilder topologyBuilder) {
        int speakerTimeout = (int) TimeUnit.SECONDS.toMillis(topologyConfig.getCreateSpeakerTimeoutSeconds());
        SpeakerWorkerBolt speakerWorker = new SpeakerWorkerBolt(Config.builder()
                .autoAck(true)
                .defaultTimeout(speakerTimeout)
                .workerSpoutComponent(ComponentId.SPEAKER_WORKER_SPOUT.name())
                .hubComponent(ComponentId.YFLOW_CREATE_HUB.name())
                .streamToHub(SPEAKER_WORKER_TO_HUB_YFLOW_CREATE.name())
                .build());
        declareBolt(topologyBuilder, speakerWorker, ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name())
                .fieldsGrouping(ComponentId.SPEAKER_WORKER_SPOUT.name(), FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_SPEAKER_WORKER.name(),
                        FIELDS_KEY)
                .directGrouping(CoordinatorBolt.ID);
    }

    private void speakerOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt<String, AbstractMessage> flKafkaBolt = makeKafkaBolt(
                getConfig().getKafkaSpeakerFlowTopic(), AbstractMessageSerializer.class);
        declareBolt(topologyBuilder, flKafkaBolt, ComponentId.SPEAKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name(),
                        Stream.SPEAKER_WORKER_REQUEST_SENDER.name());
    }

    private void coordinator(TopologyBuilder topologyBuilder) {
        declareSpout(topologyBuilder, new CoordinatorSpout(), CoordinatorSpout.ID);
        declareBolt(topologyBuilder, new CoordinatorBolt(), CoordinatorBolt.ID)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(ComponentId.FLOW_CREATE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_CREATE_HUB.name(), CoordinatorBolt.INCOME_STREAM, FIELDS_KEY)
                .fieldsGrouping(ComponentId.YFLOW_CREATE_SPEAKER_WORKER.name(),
                        CoordinatorBolt.INCOME_STREAM, FIELDS_KEY);
    }

    private void northboundOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt nbKafkaBolt = buildKafkaBolt(getConfig().getKafkaNorthboundTopic());
        declareBolt(topologyBuilder, nbKafkaBolt, ComponentId.NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(),
                        Stream.HUB_TO_NB_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_NB_RESPONSE_SENDER.name());
    }

    private void rerouteTopologyOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt rerouteKafkaBolt = buildKafkaBolt(getConfig().getKafkaRerouteTopic());
        declareBolt(topologyBuilder, rerouteKafkaBolt, ComponentId.REROUTE_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_REROUTE_RESPONSE_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_REROUTE_RESPONSE_SENDER.name());
    }

    private void pingOutput(TopologyBuilder topologyBuilder) {
        KafkaBolt pingKafkaBolt = buildKafkaBolt(getConfig().getKafkaPingTopic());
        declareBolt(topologyBuilder, pingKafkaBolt, ComponentId.FLOW_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_PING_SENDER.name())
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_PING_SENDER.name());
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
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_FLOW_MONITORING_TOPOLOGY_SENDER.name());
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
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(),
                        Stream.HUB_TO_STATS_TOPOLOGY_SENDER.name());
    }

    private void history(TopologyBuilder topologyBuilder, PersistenceManager persistenceManager) {
        HistoryBolt historyBolt = new HistoryBolt(persistenceManager);
        Fields grouping = HistoryBolt.newInputGroupingFields();
        declareBolt(topologyBuilder, historyBolt, ComponentId.HISTORY_BOLT.name())
                .fieldsGrouping(ComponentId.FLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(ComponentId.FLOW_UPDATE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(ComponentId.FLOW_REROUTE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(ComponentId.FLOW_DELETE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(ComponentId.FLOW_PATH_SWAP_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(ComponentId.FLOW_SWAP_ENDPOINTS_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(
                        ComponentId.FLOW_CREATE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(
                        ComponentId.FLOW_DELETE_MIRROR_POINT_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping)
                .fieldsGrouping(
                        ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_HISTORY_BOLT.name(), grouping);
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
                .shuffleGrouping(ComponentId.YFLOW_CREATE_HUB.name(), Stream.HUB_TO_METRICS_BOLT.name());
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
        FLOW_SWAP_ENDPOINTS_HUB("flow.swap.endpoints.hub.bolt"),
        FLOW_CREATE_MIRROR_POINT_HUB("flow.create.mirror.point.hub.bolt"),
        FLOW_DELETE_MIRROR_POINT_HUB("flow.create.mirror.point.hub.bolt"),

        FLOW_CREATE_SPEAKER_WORKER("flow.create.worker.bolt"),
        FLOW_UPDATE_SPEAKER_WORKER("flow.update.worker.bolt"),
        FLOW_PATH_SWAP_SPEAKER_WORKER("flow.pathswap.worker.bolt"),
        FLOW_REROUTE_SPEAKER_WORKER("flow.reroute.worker.bolt"),
        FLOW_DELETE_SPEAKER_WORKER("flow.delete.worker.bolt"),
        FLOW_CREATE_MIRROR_POINT_WORKER("flow.create.mirror.point.worker.bolt"),
        FLOW_DELETE_MIRROR_POINT_WORKER("flow.create.mirror.point.worker.bolt"),

        YFLOW_CREATE_HUB("y_flow.create.hub.bolt"),

        YFLOW_CREATE_SPEAKER_WORKER("y_flow.create.worker.bolt"),

        NB_RESPONSE_SENDER("nb.kafka.bolt"),
        REROUTE_RESPONSE_SENDER("reroute.kafka.bolt"),
        RESPONSE_SENDER("response.kafka.bolt"),
        FLOW_PING_SENDER("ping.kafka.bolt"),
        SERVER42_CONTROL_TOPOLOGY_SENDER("server42.control.kafka.bolt"),
        FLOW_MONITORING_TOPOLOGY_SENDER("flow.monitoring.kafka.bolt"),
        STATS_TOPOLOGY_SENDER("stats.kafka.bolt"),

        SPEAKER_REQUEST_SENDER("speaker.kafka.bolt"),

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
        ROUTER_TO_FLOW_SWAP_ENDPOINTS_HUB,
        ROUTER_TO_FLOW_CREATE_MIRROR_POINT_HUB,
        ROUTER_TO_FLOW_DELETE_MIRROR_POINT_HUB,
        ROUTER_TO_YFLOW_CREATE_HUB,

        HUB_TO_SPEAKER_WORKER,
        HUB_TO_HISTORY_BOLT,
        HUB_TO_METRICS_BOLT,

        SPEAKER_WORKER_TO_HUB_CREATE,
        SPEAKER_WORKER_TO_HUB_UPDATE,
        SPEAKER_WORKER_TO_HUB_REROUTE,
        SPEAKER_WORKER_TO_HUB_DELETE,
        SPEAKER_WORKER_TO_HUB_PATH_SWAP,
        SPEAKER_WORKER_TO_HUB_CREATE_MIRROR_POINT,
        SPEAKER_WORKER_TO_HUB_DELETE_MIRROR_POINT,
        SPEAKER_WORKER_TO_HUB_YFLOW_CREATE,

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
