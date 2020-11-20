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

package org.openkilda.wfm;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_VERSION;

import org.openkilda.bluegreen.LifecycleEvent;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;

import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.Objects;

public class VersioningSpoutBolt extends AbstractBolt {
    private final KafkaSpout<?, ?> kafkaSpout;
    private final String kafkaSpoutId;

    private String currentVersion = null;

    public VersioningSpoutBolt(KafkaSpout<?, ?> kafkaSpout, String kafkaSpoutId) {
        this.kafkaSpout = kafkaSpout;
        this.kafkaSpoutId = kafkaSpoutId;
    }


    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (ZooKeeperSpout.BOLT_ID.equals(input.getSourceComponent())) {
            handleZooKeeperEvent(input);
        } else if (kafkaSpoutId.equals(input.getSourceComponent())) {
            handleKafkaSpout(input);
        } else {
            unhandledInput(input);
        }

    }

    private void handleKafkaSpout(Tuple input) throws org.openkilda.wfm.error.PipelineException {
        String version = pullValue(input, FIELD_ID_VERSION, String.class);
        if (currentVersion == null) {
            //TODO justpass the message?
        }

        if (Objects.equals(version, currentVersion)) {
            emit(input, input.getValues());
        }
    }

    private void handleZooKeeperEvent(Tuple input) {
        LifecycleEvent event = (LifecycleEvent) input.getValueByField(ZooKeeperSpout.FIELD_ID_LIFECYCLE_EVENT);
        if (event.getBuildVersion() != null) {
            currentVersion = event.getBuildVersion();
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        kafkaSpout.declareOutputFields(declarer);
    }
}
