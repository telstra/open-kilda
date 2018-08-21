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

package org.openkilda.wfm.topology.utils;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.wfm.error.MessageException;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.io.IOException;
import java.util.Map;

public abstract class AbstractKafkaParserBolt extends BaseRichBolt {
    protected OutputCollector collector;

    protected String getJson(Tuple tuple) {
        return tuple.getString(0);
    }

    protected Message getMessage(String json) throws IOException {
        return Utils.MAPPER.readValue(json, Message.class);
    }

    protected InfoData getInfoData(Message message) throws MessageException {
        if (!(message instanceof InfoMessage)) {
            throw new MessageException(message.getClass().getName() + " is not an InfoMessage");
        }
        InfoData data = ((InfoMessage) message).getData();
        return data;
    }

    protected InfoData getInfoData(Tuple tuple) throws IOException, MessageException {
        return getInfoData(getMessage(getJson(tuple)));
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.collector = outputCollector;
    }
}
