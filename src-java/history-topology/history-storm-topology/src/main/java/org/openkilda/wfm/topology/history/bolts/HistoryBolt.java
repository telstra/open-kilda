/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.history.bolts;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.history.model.FlowHistoryHolder;
import org.openkilda.wfm.share.history.service.HistoryService;
import org.openkilda.wfm.share.zk.ZkStreams;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

public class HistoryBolt extends AbstractBolt {
    private transient HistoryService historyService;
    private static volatile PersistenceManager PERSISTENCE_MANAGER_INSTANCE;

    public HistoryBolt(PersistenceManager persistenceManager, String lifeCycleEventSourceComponent) {
        super(persistenceManager, lifeCycleEventSourceComponent);
    }


    /**
     * Called when a task for this component is initialized within a worker on the cluster.
     * It provides the bolt with the environment in which the bolt executes.
     * Static instance of PersistenceManager is initialized at this step.
     *
     * @param stormConf The Storm configuration for this bolt.
     * @param context   This object can be used to get information about this task's place within the topology.
     * @param collector The collector is used to emit tuples from this bolt.
     */
    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        if (PERSISTENCE_MANAGER_INSTANCE == null) {
            synchronized (HistoryBolt.class) {
                if (PERSISTENCE_MANAGER_INSTANCE == null) {
                    PERSISTENCE_MANAGER_INSTANCE = persistenceManager;
                    PERSISTENCE_MANAGER_INSTANCE.install();
                }
            }
        }
        persistenceManager = null;
        super.prepare(stormConf, context, collector);
    }

    @Override
    protected void init() {
        historyService = new HistoryService(PERSISTENCE_MANAGER_INSTANCE);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        if (active) {
            Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);
            if (message instanceof InfoMessage) {
                InfoData payload = ((InfoMessage) message).getData();
                if (payload instanceof FlowHistoryHolder) {
                    historyService.store((FlowHistoryHolder) payload);
                } else {
                    unhandledInput(input);
                }
            } else {
                unhandledInput(input);
            }
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(ZkStreams.ZK.toString(), new Fields(ZooKeeperBolt.FIELD_ID_STATE,
                ZooKeeperBolt.FIELD_ID_CONTEXT));
    }
}
