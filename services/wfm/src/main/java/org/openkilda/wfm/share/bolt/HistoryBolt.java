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

package org.openkilda.wfm.share.bolt;

import org.openkilda.messaging.Utils;
import org.openkilda.model.history.FlowDump;
import org.openkilda.model.history.FlowEvent;
import org.openkilda.model.history.FlowHistory;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.services.HistoryService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

public class HistoryBolt extends AbstractBolt {
    private final PersistenceManager persistenceManager;
    private transient HistoryService historyService;

    public static final Fields FIELDS_HISTORY = new Fields(Utils.PAYLOAD, FIELD_ID_CONTEXT);

    public HistoryBolt(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void init() {
        historyService = new HistoryService(persistenceManager);
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        Object payload = input.getValueByField(Utils.PAYLOAD);
        if (payload instanceof FlowEvent) {
            historyService.store((FlowEvent) payload);
        } else if (payload instanceof FlowHistory) {
            historyService.store((FlowHistory) payload);
        } else if (payload instanceof FlowDump) {
            historyService.store((FlowDump) payload);
        } else {
            log.error("Skip undefined payload: {}", payload);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }
}
