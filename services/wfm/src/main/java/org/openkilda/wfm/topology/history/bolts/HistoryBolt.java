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

package org.openkilda.wfm.topology.history.bolts;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.history.FlowEventData;
import org.openkilda.messaging.history.FlowHistoryData;
import org.openkilda.messaging.history.HistoryData;
import org.openkilda.messaging.history.HistoryMessage;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.history.service.HistoryService;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

public class HistoryBolt extends AbstractBolt {

    private transient HistoryService historyService;
    private PersistenceManager persistenceManager;

    public HistoryBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        super.prepare(stormConf, context, collector);
        TransactionManager transactionManager = persistenceManager.getTransactionManager();
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        historyService = new HistoryService(transactionManager, repositoryFactory);
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof HistoryMessage) {
            HistoryData historyData = ((HistoryMessage) message).getData();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            log.info("'{}' at {}. Details: {}", historyData.getAction(),
                    format.format(new Date(historyData.getTimestamp())), historyData.getDetails());

            if (historyData instanceof FlowEventData) {
                historyService.store((FlowEventData) historyData);
            } else if (historyData instanceof FlowHistoryData) {
                historyService.store((FlowHistoryData) historyData);
            }
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }
}
