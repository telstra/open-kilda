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

import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

@Slf4j
public class ResultDispatcher extends Abstract {
    public static final String BOLT_ID = ComponentId.RESULT_DISPATCHER.toString();

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_PERIODIC_ID = "periodic";
    public static final String STREAM_MANUAL_ID = "manual";

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        PingContext pingContext = pullPingContext(input);

        final String stream = dispatch(pingContext);
        if (stream == null) {
            log.error("There is no result manager for ping kind {}", pingContext.getKind());
        } else {
            Values output = new Values(pingContext, pullContext(input));
            getOutput().emit(stream, input, output);
        }
    }

    private String dispatch(PingContext pingContext) {
        String value;
        final Kinds kind = pingContext.getKind();
        switch (kind) {
            case PERIODIC:
                value = STREAM_PERIODIC_ID;
                break;
            case ON_DEMAND:
                value = STREAM_MANUAL_ID;
                break;

            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported value %s.%s", kind.getClass().getName(), kind));
        }
        return value;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_PERIODIC_ID, STREAM_FIELDS);
        outputManager.declareStream(STREAM_MANUAL_ID, STREAM_FIELDS);
    }
}
