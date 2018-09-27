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
    @Default("kilda.flow")
    String getFlowTopic();

    @Key("flow.status")
    @Default("kilda.flow.status")
    String getFlowStatusTopic();

    @Key("northbound")
    @FallbackKey("kafka.northbound.topic")
    @Default("kilda.northbound")
    String getNorthboundTopic();

    @Key("opentsdb")
    @Default("kilda.otsdb")
    String getOtsdbTopic();

    @Key("simulator")
    @Default("kilda.simulator")
    String getSimulatorTopic();

    @Key("speaker")
    @FallbackKey("kafka.speaker.topic")
    @Default("kilda.speaker")
    String getSpeakerTopic();

    @Key("speaker.disco")
    @FallbackKey("kafka.speaker.disco")
    @Default("kilda.speaker.disco")
    String getSpeakerDiscoTopic();

    @Key("speaker.flow")
    @FallbackKey("kafka.speaker.flow")
    @Default("kilda.speaker.flow")
    String getSpeakerFlowTopic();

    @Key("speaker.flow.ping")
    @FallbackKey("kafka.speaker.flow.ping")
    @Default("kilda.speaker.flow.ping")
    String getSpeakerFlowPingTopic();

    @Key("ping")
    @Default("kilda.ping")
    String getPingTopic();

    @Key("stats")
    @Default("kilda.stats")
    String getStatsTopic();

    @Key("topo.eng")
    @FallbackKey("kafka.topo.eng.topic")
    @Default("kilda.topo.eng")
    String getTopoEngTopic();

    @Key("topo.disco")
    @Default("kilda.topo.disco")
    String getTopoDiscoTopic();

    @Key("topo.cache")
    @Default("kilda.topo.cache")
    String getTopoCacheTopic();

    @Key("topo.nbworker")
    @FallbackKey("kafka.nbworker.topic")
    @Default("kilda.topo.nb")
    String getTopoNbTopic();
}
