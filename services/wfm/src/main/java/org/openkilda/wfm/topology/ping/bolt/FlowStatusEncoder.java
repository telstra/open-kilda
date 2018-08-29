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

package org.openkilda.wfm.topology.ping.bolt;

import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.flow.FlowPingReport;
import org.openkilda.messaging.model.PingReport;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.JsonEncodeException;
import org.openkilda.wfm.error.PipelineException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class FlowStatusEncoder extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.FLOW_STATUS_ENCODER.toString();

    public static final String FIELD_ID_PING_REPORT = "flow_status";

    public static final Fields STREAM_FIELDS = new Fields(
            FieldNameBasedTupleToKafkaMapper.BOLT_KEY, FieldNameBasedTupleToKafkaMapper.BOLT_MESSAGE);

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        PingReport report = pullPingReport(input);
        InfoMessage message = wrap(input, report);
        String json = encode(message);

        Values output = new Values(null, json);
        getOutput().emit(input, output);
    }

    private InfoMessage wrap(Tuple input, PingReport pingReport) throws PipelineException {
        FlowPingReport payload = new FlowPingReport(pingReport);
        CommandContext commandContext = pullContext(input);
        return new InfoMessage(payload, System.currentTimeMillis(), commandContext.getCorrelationId());
    }

    private String encode(InfoMessage message) throws JsonEncodeException {
        String json;
        try {
            json = Utils.MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new JsonEncodeException(message, e);
        }
        return json;
    }

    private PingReport pullPingReport(Tuple input) throws PipelineException {
        return pullValue(input, FIELD_ID_PING_REPORT, PingReport.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
