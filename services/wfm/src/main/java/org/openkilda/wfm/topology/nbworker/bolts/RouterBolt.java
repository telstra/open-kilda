/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import com.google.common.collect.Lists;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.openkilda.messaging.Message;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.ReadDataRequest;
import org.openkilda.wfm.topology.nbworker.StreamType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class RouterBolt extends BaseRichBolt {

    private static final Logger LOGGER = LoggerFactory.getLogger(RouterBolt.class);

    private OutputCollector outputCollector;

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.outputCollector = collector;
    }

    @Override
    public void execute(Tuple input) {
        String request = input.getString(0);

        try {
            Message message = Utils.MAPPER.readValue(request, Message.class);
            if (message instanceof CommandMessage) {
                LOGGER.debug("Received command message {}", message);
                CommandMessage command = (CommandMessage) message;
                CommandData data = command.getData();

                if (data instanceof BaseRequest) {
                    LOGGER.debug("Processing request with correlationId {}", message.getCorrelationId());

                    BaseRequest baseRequest = (BaseRequest) data;
                    processRequest(baseRequest, message.getCorrelationId());
                }
            } else {
                LOGGER.debug("Received unexpected message with correlationId: {}", message.getCorrelationId());
            }

        } catch (Exception e) {
            LOGGER.error("Error during processing request", e);
        } finally {
            outputCollector.ack(input);
        }
    }

    private void processRequest(BaseRequest request, String correlationId) {
        if (request instanceof ReadDataRequest) {
            outputCollector.emit(StreamType.READ.toString(), Lists.newArrayList(request, correlationId));
        } else {
            LOGGER.warn("Received unsupported request type. CorrelationId: {}", correlationId);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(StreamType.READ.toString(), new Fields("request", "correlationId"));
    }
}
