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

package org.openkilda.config;

import static org.openkilda.config.KafkaTopicsConfig.KAFKA_TOPIC_MAPPING;

import org.openkilda.config.mapping.Mapping;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Default;
import com.sabre.oss.conf4j.annotation.FallbackKey;
import com.sabre.oss.conf4j.annotation.Key;

@Configuration
@Key("kafka.topic")
@Mapping(target = KAFKA_TOPIC_MAPPING)
public interface KafkaTopicsConfig {
    String KAFKA_TOPIC_MAPPING = "KAFKA_TOPIC";

    @Key("ctrl")
    @Default("kilda.ctrl")
    String getCtrlTopic();

    @Key("flow")
    @FallbackKey("kafka.flow.topic")
    @Default("kilda.flow.storm")
    String getFlowTopic();

    @Key("flow.region")
    @FallbackKey("kafka.flow.topic.region")
    @Default("kilda.flow")
    String getFlowRegionTopic();

    @Key("flowhs")
    @FallbackKey("kafka.flowhs.topic")
    @Default("kilda.flowhs.storm")
    String getFlowHsTopic();

    @Key("flow.status")
    @Default("kilda.flow.status")
    String getFlowStatusTopic();

    @Key("northbound")
    @FallbackKey("kafka.northbound.topic")
    @Default("kilda.northbound.storm")
    String getNorthboundTopic();

    @Key("northbound.region")
    @FallbackKey("kafka.northbound.topic.region")
    @Default("kilda.northbound")
    String getNorthboundRegionTopic();

    @Key("opentsdb")
    @Default("kilda.otsdb")
    String getOtsdbTopic();

    @Key("simulator")
    @Default("kilda.simulator")
    String getSimulatorTopic();

    @Key("speaker")
    @FallbackKey("kafka.speaker.topic")
    @Default("kilda.speaker.storm")
    String getSpeakerTopic();

    @Key("speaker.region")
    @FallbackKey("kafka.speaker.region.topic")
    @Default("kilda.speaker")
    String getSpeakerRegionTopic();

    @Key("stats.request.priv")
    @Default("kilda.stats.request.priv")
    String getStatsRequestPrivTopic();

    @Key("stats.request.priv.region")
    @Default("kilda.stats.request.priv.region")
    String getStatsRequestPrivRegionTopic();

    @Key("stats.stats-request.priv")
    @Default("kilda.stats.stats-request.priv")
    String getStatsStatsRequestPrivTopic();

    @Key("stats.stats-request.priv.region")
    @Default("kilda.stats.stats-request.priv.region")
    String getStatsStatsRequestPrivRegionTopic();

    @Key("fl-stats.switches.priv")
    @Default("kilda.fl-stats.switches.priv")
    String getFlStatsSwitchesPrivTopic();

    @Key("fl-stats.switches.priv.region")
    @Default("kilda.fl-stats.switches.priv.region")
    String getFlStatsSwitchesPrivRegionTopic();

    @Key("speaker.disco")
    @FallbackKey("kafka.speaker.disco")
    @Default("kilda.speaker.disco.storm")
    String getSpeakerDiscoTopic();

    @Key("speaker.disco.region")
    @FallbackKey("kafka.speaker.disco.region")
    @Default("kilda.speaker.disco")
    String getSpeakerDiscoRegionTopic();

    @Key("speaker.flow")
    @FallbackKey("kafka.speaker.flow")
    @Default("kilda.speaker.flow.storm")
    String getSpeakerFlowTopic();

    @Key("speaker.flow.region")
    @FallbackKey("kafka.speaker.flow.region")
    @Default("kilda.speaker.flow")
    String getSpeakerFlowRegionTopic();

    @Key("flowhs.speaker")
    @FallbackKey("kafka.flowhs.speaker.topic")
    @Default("kilda.speaker.flowhs.storm")
    String getFlowHsSpeakerTopic();

    @Key("flowhs.speaker.region")
    @FallbackKey("kafka.flowhs.speaker.topic.region")
    @Default("kilda.speaker.flowhs")
    String getFlowHsSpeakerRegionTopic();

    @Key("speaker.flow.ping")
    @FallbackKey("kafka.speaker.flow.ping")
    @Default("kilda.speaker.flow.ping.storm")
    String getSpeakerFlowPingTopic();

    @Key("speaker.flow.ping.region")
    @FallbackKey("kafka.speaker.flow.ping.region")
    @Default("kilda.speaker.flow.ping")
    String getSpeakerFlowPingRegionTopic();

    @Key("grpc.speaker")
    @Default("kilda.grpc.speaker")
    String getGrpcSpeakerTopic();

    @Key("ping")
    @Default("kilda.ping.storm")
    String getPingTopic();

    @Key("ping.region")
    @Default("kilda.ping")
    String getPingRegionTopic();

    @Key("stats")
    @Default("kilda.stats.storm")
    String getStatsTopic();

    @Key("stats.region")
    @Default("kilda.stats")
    String getStatsRegionTopic();

    @Key("topo.disco")
    @Default("kilda.topo.disco.storm")
    String getTopoDiscoTopic();

    @Key("topo.disco.region")
    @Default("kilda.topo.disco")
    String getTopoDiscoRegionTopic();

    @Key("topo.nbworker")
    @FallbackKey("kafka.nbworker.topic")
    @Default("kilda.topo.nb.storm")
    String getTopoNbTopic();

    @Key("topo.nbworker.region")
    @FallbackKey("kafka.nbworker.topic.region")
    @Default("kilda.topo.nb")
    String getTopoNbRegionTopic();

    @Key("topo.reroute")
    @Default("kilda.topo.reroute.storm")
    String getTopoRerouteTopic();

    @Key("topo.switch.manager")
    @Default("kilda.topo.switch.manager.storm")
    String getTopoSwitchManagerTopic();

    @Key("topo.switch.manager.region")
    @Default("kilda.topo.switch.manager")
    String getTopoSwitchManagerRegionTopic();
}
