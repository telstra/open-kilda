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

package org.openkilda.wfm.topology.network.storm.bolt.port;

import static org.openkilda.wfm.topology.utils.KafkaRecordTranslator.FIELD_ID_PAYLOAD;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.history.model.PortHistoryData;
import org.openkilda.wfm.share.history.service.HistoryService;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class PortHistory extends AbstractBolt {
    public static final String BOLT_ID = "port.history";

    private final PersistenceManager persistenceManager;
    private transient HistoryService historyService;

    public PortHistory(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        this.historyService = new HistoryService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        PortHistoryData portHistory = pullValue(input, FIELD_ID_PAYLOAD, PortHistoryData.class);

        log.debug("Saving port history update {}", portHistory);
        historyService.store(portHistory);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
    }
}
