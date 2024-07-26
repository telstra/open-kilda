/* Copyright 2024 Telstra Open Source
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

package org.openkilda.testing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicsConfig extends EnvironmentConfig {

    @Value("${kafka.topic.ctrl:kilda.ctrl}")
    private String ctrlTopic;

    @Value("${kafka.topic.northbound.flow.priv:kilda.northbound.flowhs.priv}")
    private String flowHsTopic;

    @Value("${kafka.topic.flow.status:kilda.flow.status}")
    private String flowStatusTopic;

    @Value("${kafka.topic.northbound:kilda.northbound.storm}")
    private String northboundTopic;

    @Value("${kafka.topic.northbound.region:kilda.northbound}")
    private String northboundRegionTopic;

    @Value("${kafka.topic.opentsdb:kilda.otsdb}")
    private String otsdbTopic;

    @Value("${kafka.topic.opentsdb.filtered:kilda.otsdb.filtered.priv}")
    private String otsdbFilteredTopic;

    @Value("${kafka.topic.speaker:kilda.speaker.storm}")
    private String speakerTopic;

    @Value("${kafka.topic.speaker.region:kilda.speaker}")
    private String speakerRegionTopic;

    @Value("${kafka.topic.speaker.disco:kilda.speaker.disco.storm}")
    private String speakerDiscoTopic;

    @Value("${kafka.topic.speaker.disco.region:kilda.speaker.disco}")
    private String speakerDiscoRegionTopic;

    @Value("${kafka.topic.speaker.flowhs:kilda.speaker.flowhs.storm}")
    private String speakerFlowHsTopic;

    @Value("${kafka.topic.speaker.flow.region:kilda.speaker.flow}")
    private String speakerFlowRegionTopic;

    @Value("${kafka.topic.speaker.flowhs.priv:kilda.speaker.flowhs.priv}")
    private String flowHsSpeakerTopic;

    @Value("${kafka.topic.speaker.flowhs.priv.region:kilda.speaker.flowhs.priv.region}")
    private String flowHsSpeakerRegionTopic;

    @Value("${kafka.topic.speaker.switch.manager:kilda.speaker.switch.manager.storm}")
    private String speakerSwitchManagerTopic;

    @Value("${kafka.topic.speaker.switch.manager.region:kilda.speaker.switch.manager}")
    private String speakerSwitchManagerRegionTopic;

    @Value("${kafka.topic.speaker.switch.manager.priv:kilda.speaker.switch.manager.priv}")
    private String switchManagerSpeakerTopic;

    @Value("${kafka.topic.speaker.switch.manager.priv.region:kilda.speaker.switch.manager.priv.region}")
    private String switchManagerSpeakerRegionTopic;

    @Value("${kafka.topic.speaker.network.control:kilda.network.control.storm}")
    private String networkControlTopic;

    @Value("${kafka.topic.speaker.network.region:kilda.network.control}")
    private String networkControlRegionTopic;

    @Value("${kafka.topic.speaker.network.control.response.priv:kilda.speaker.network.control.response.priv}")
    private String networkControlResponseTopic;

    @Value("${kafka.topic.speaker.network.priv.region:kilda.network.control.response.priv.region}")
    private String networkControlResponseRegionTopic;

    @Value("${kafka.topic.speaker.flow.ping:kilda.speaker.flow.ping.storm}")
    private String speakerFlowPingTopic;

    @Value("${kafka.topic.speaker.flow.ping.region:kilda.speaker.flow.ping}")
    private String speakerFlowPingRegionTopic;

    @Value("${kafka.topic.grpc.speaker:kilda.grpc.speaker}")
    private String grpcSpeakerTopic;

    @Value("${kafka.topic.ping:kilda.ping.storm}")
    private String pingTopic;

    @Value("${kafka.topic.ping.region:kilda.ping}")
    private String pingRegionTopic;

    @Value("${kafka.topic.stats:kilda.stats.storm}")
    private String statsTopic;

    @Value("${kafka.topic.stats.region:kilda.stats}")
    private String statsRegionTopic;

    @Value("${kafka.topic.topo.disco:kilda.topo.disco.storm}")
    private String topoDiscoTopic;

    @Value("${kafka.topic.topo.disco.region:kilda.topo.disco}")
    private String topoDiscoRegionTopic;

    @Value("${kafka.topic.topo.nbworker:kilda.topo.nb.storm}")
    private String topoNbTopic;

    @Value("${kafka.topic.topo.nbworker.region:kilda.topo.nb}")
    private String topoNbRegionTopic;

    @Value("${kafka.topic.topo.reroute:kilda.topo.reroute.storm}")
    private String topoRerouteTopic;

    @Value("${kafka.topic.topo.isl.status:kilda.network.isl.status.priv}")
    private String networkIslStatusTopic;

    @Value("${kafka.topic.topo.switch.manager.nb:kilda.nb.swmanager_request.priv}")
    private String topoSwitchManagerNbTopic;

    @Value("${kafka.topic.topo.switch.manager.network:kilda.network.swmanager_request.priv}")
    private String topoSwitchManagerNetworkTopic;

    @Value("${kafka.topic.topo.switch.manager.nbworker:kilda.nbworker.swmanager_request.priv}")
    private String topoSwitchManagerNbWorkerTopic;

    @Value("${kafka.topic.topo.switch.manager:kilda.topo.switch.manager.storm}")
    private String topoSwitchManagerTopic;

    @Value("${kafka.topic.topo.switch.manager.region:kilda.topo.switch.manager}")
    private String topoSwitchManagerRegionTopic;

    @Value("${kafka.topic.topo.isl.latency:kilda.topo.isl.latency.storm}")
    private String topoIslLatencyTopic;

    @Value("${kafka.topic.topo.isl.latency.region:kilda.topo.isl.latency}")
    private String topoIslLatencyRegionTopic;

    @Value("${kafka.topic.topo.router.connected.devices.storm:kilda.connected.devices.priv.storm}")
    private String topoConnectedDevicesTopic;

    @Value("${kafka.topic.topo.floodlight.connected.devices.region:kilda.floodlight.connected.devices.priv}")
    private String topoConnectedDevicesRegionTopic;

    @Value("${kafka.topic.topo.history.storm:kilda.topo.history.storm.priv}")
    private String topoHistoryTopic;

    // TODO(surabujin): check usage
    @Value("${kafka.topic.grpc.response:kilda.grpc.response.priv}")
    private String grpcResponseTopic;

    @Value("${kafka.topic.server42-stats.flowrtt:kilda.server42-stats.flowrtt.priv}")
    private String server42StatsFlowRttTopic;

    @Value("${kafka.topic.server42-stats.islrtt:kilda.server42-stats.islrtt.priv}")
    private String server42StatsIslRttTopic;

    @Value("${kafka.topic.server42-storm.commands:kilda.server42-storm.commands.priv}")
    private String server42StormCommandsTopic;

    @Value("${kafka.topic.server42-control.commands-reply:kilda.server42-control.commands-reply.priv}")
    private String server42ControlCommandsReplyTopic;

    @Value("${kafka.topic.topo.flowhs.server42-storm:kilda.flowhs.server42-storm-notify.priv}")
    private String flowHsServer42StormNotifyTopic;

    @Value("${kafka.topic.topo.nbworker.server42-storm:kilda.nbworker.server42-storm-notify.priv}")
    private String nbWorkerServer42StormNotifyTopic;

    @Value("${kafka.topic.topo.flowhs.flow.monitoring:kilda.flowhs.flowmonitoring-notify.priv}")
    private String flowHsFlowMonitoringNotifyTopic;

    @Value("${kafka.topic.topo.network.flow.monitoring:kilda.network.flowmonitoring-notify.priv}")
    private String networkFlowMonitoringNotifyTopic;

    @Value("${kafka.topic.stats.notify.priv:kilda.stats.notify.priv}")
    private String flowStatsNotifyTopic;

    public String getCtrlTopic() {
        return addPrefixAndGet(ctrlTopic);
    }

    public String getFlowHsTopic() {
        return addPrefixAndGet(flowHsTopic);
    }

    public String getFlowStatusTopic() {
        return addPrefixAndGet(flowStatusTopic);
    }

    public String getNorthboundTopic() {
        return addPrefixAndGet(northboundTopic);
    }

    public String getNorthboundRegionTopic() {
        return addPrefixAndGet(northboundRegionTopic);
    }

    public String getOtsdbTopic() {
        return addPrefixAndGet(otsdbTopic);
    }

    public String getOtsdbFilteredTopic() {
        return addPrefixAndGet(otsdbFilteredTopic);
    }

    public String getSpeakerTopic() {
        return addPrefixAndGet(speakerTopic);
    }

    public String getSpeakerRegionTopic() {
        return addPrefixAndGet(speakerRegionTopic);
    }

    public String getSpeakerDiscoTopic() {
        return addPrefixAndGet(speakerDiscoTopic);
    }

    public String getSpeakerDiscoRegionTopic() {
        return addPrefixAndGet(speakerDiscoRegionTopic);
    }

    public String getSpeakerFlowHsTopic() {
        return addPrefixAndGet(speakerFlowHsTopic);
    }

    public String getSpeakerFlowRegionTopic() {
        return addPrefixAndGet(speakerFlowRegionTopic);
    }

    public String getFlowHsSpeakerTopic() {
        return addPrefixAndGet(flowHsSpeakerTopic);
    }

    public String getFlowHsSpeakerRegionTopic() {
        return addPrefixAndGet(flowHsSpeakerRegionTopic);
    }

    public String getSpeakerSwitchManagerTopic() {
        return addPrefixAndGet(speakerSwitchManagerTopic);
    }

    public String getSpeakerSwitchManagerRegionTopic() {
        return addPrefixAndGet(speakerSwitchManagerRegionTopic);
    }

    public String getSwitchManagerSpeakerTopic() {
        return addPrefixAndGet(switchManagerSpeakerTopic);
    }

    public String getSwitchManagerSpeakerRegionTopic() {
        return addPrefixAndGet(switchManagerSpeakerRegionTopic);
    }

    public String getNetworkControlTopic() {
        return addPrefixAndGet(networkControlTopic);
    }

    public String getNetworkControlRegionTopic() {
        return addPrefixAndGet(networkControlRegionTopic);
    }

    public String getNetworkControlResponseTopic() {
        return addPrefixAndGet(networkControlResponseTopic);
    }

    public String getNetworkControlResponseRegionTopic() {
        return addPrefixAndGet(networkControlResponseRegionTopic);
    }

    public String getSpeakerFlowPingTopic() {
        return addPrefixAndGet(speakerFlowPingTopic);
    }

    public String getSpeakerFlowPingRegionTopic() {
        return addPrefixAndGet(speakerFlowPingRegionTopic);
    }

    public String getGrpcSpeakerTopic() {
        return addPrefixAndGet(grpcSpeakerTopic);
    }

    public String getPingTopic() {
        return addPrefixAndGet(pingTopic);
    }

    public String getPingRegionTopic() {
        return addPrefixAndGet(pingRegionTopic);
    }

    public String getStatsTopic() {
        return addPrefixAndGet(statsTopic);
    }

    public String getStatsRegionTopic() {
        return addPrefixAndGet(statsRegionTopic);
    }

    public String getTopoDiscoTopic() {
        return addPrefixAndGet(topoDiscoTopic);
    }

    public String getTopoDiscoRegionTopic() {
        return addPrefixAndGet(topoDiscoRegionTopic);
    }

    public String getTopoNbTopic() {
        return addPrefixAndGet(topoNbTopic);
    }

    public String getTopoNbRegionTopic() {
        return addPrefixAndGet(topoNbRegionTopic);
    }

    public String getTopoRerouteTopic() {
        return addPrefixAndGet(topoRerouteTopic);
    }

    public String getNetworkIslStatusTopic() {
        return addPrefixAndGet(networkIslStatusTopic);
    }

    public String getTopoSwitchManagerNbTopic() {
        return addPrefixAndGet(topoSwitchManagerNbTopic);
    }

    public String getTopoSwitchManagerNetworkTopic() {
        return addPrefixAndGet(topoSwitchManagerNetworkTopic);
    }

    public String getTopoSwitchManagerNbWorkerTopic() {
        return addPrefixAndGet(topoSwitchManagerNbWorkerTopic);
    }

    public String getTopoSwitchManagerTopic() {
        return addPrefixAndGet(topoSwitchManagerTopic);
    }

    public String getTopoSwitchManagerRegionTopic() {
        return addPrefixAndGet(topoSwitchManagerRegionTopic);
    }

    public String getTopoIslLatencyTopic() {
        return addPrefixAndGet(topoIslLatencyTopic);
    }

    public String getTopoIslLatencyRegionTopic() {
        return addPrefixAndGet(topoIslLatencyRegionTopic);
    }

    public String getTopoConnectedDevicesTopic() {
        return addPrefixAndGet(topoConnectedDevicesTopic);
    }

    public String getTopoConnectedDevicesRegionTopic() {
        return addPrefixAndGet(topoConnectedDevicesRegionTopic);
    }

    public String getTopoHistoryTopic() {
        return addPrefixAndGet(topoHistoryTopic);
    }

    public String getGrpcResponseTopic() {
        return addPrefixAndGet(grpcResponseTopic);
    }

    public String getServer42StatsFlowRttTopic() {
        return addPrefixAndGet(server42StatsFlowRttTopic);
    }

    public String getServer42StatsIslRttTopic() {
        return addPrefixAndGet(server42StatsIslRttTopic);
    }

    public String getServer42StormCommandsTopic() {
        return addPrefixAndGet(server42StormCommandsTopic);
    }

    public String getServer42ControlCommandsReplyTopic() {
        return addPrefixAndGet(server42ControlCommandsReplyTopic);
    }

    public String getFlowHsServer42StormNotifyTopic() {
        return addPrefixAndGet(flowHsServer42StormNotifyTopic);
    }

    public String getNbWorkerServer42StormNotifyTopic() {
        return addPrefixAndGet(nbWorkerServer42StormNotifyTopic);
    }

    public String getFlowHsFlowMonitoringNotifyTopic() {
        return addPrefixAndGet(flowHsFlowMonitoringNotifyTopic);
    }

    public String getNetworkFlowMonitoringNotifyTopic() {
        return addPrefixAndGet(networkFlowMonitoringNotifyTopic);
    }

    public String getFlowStatsNotifyTopic() {
        return addPrefixAndGet(flowStatsNotifyTopic);
    }
}
