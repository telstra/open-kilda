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
import org.openkilda.messaging.history.HistoryData;
import org.openkilda.messaging.history.HistoryMessage;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.text.SimpleDateFormat;
import java.util.Date;

public class HistoryBolt extends AbstractBolt {

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        Message message = pullValue(input, FIELD_ID_PAYLOAD, Message.class);

        if (message instanceof HistoryMessage) {
            HistoryData historyData = ((HistoryMessage) message).getData();
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            log.info("'{}' did '{}' at {}. Details: {}", historyData.getActor(), historyData.getAction(),
                    format.format(new Date(historyData.getTimestamp())), historyData.getDetails());
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {

    }
}
