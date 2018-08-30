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
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public abstract class ResultManager extends Abstract {
    public static final String FIELD_ID_GROUP_ID = "ping_group";

    public static final Fields STREAM_GROUP_FIELDS = new Fields(FIELD_ID_GROUP_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_GROUP_ID = "grouping";

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String component = input.getSourceComponent();
        if (GroupCollector.BOLT_ID.equals(component)) {
            handleGroup(input);
        } else {
            handle(input, pullPingContext(input));
        }
    }

    protected void handleGroup(Tuple input) throws PipelineException {}

    protected void handle(Tuple input, PingContext pingContext) throws AbstractException {
        if (pingContext.isError()) {
            handleError(input, pingContext);
        } else {
            handleSuccess(input, pingContext);
        }
    }

    protected void handleSuccess(Tuple input, PingContext pingContext) throws PipelineException {}

    protected void handleError(Tuple input, PingContext pingContext) throws PipelineException {}

    protected void collectGroup(Tuple input, PingContext pingContext) throws PipelineException {
        Values output = new Values(pingContext.getGroup(), pingContext, pullContext(input));
        getOutput().emit(STREAM_GROUP_ID, input, output);
    }

    protected void declareGroupStream(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_GROUP_ID, STREAM_GROUP_FIELDS);
    }
}
