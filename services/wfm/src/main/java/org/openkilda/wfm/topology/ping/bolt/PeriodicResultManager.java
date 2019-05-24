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
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PeriodicResultManager extends ResultManager {
    public static final String BOLT_ID = ComponentId.PERIODIC_RESULT_MANAGER.toString();

    public static final String FIELD_ID_FLOW_ID = Utils.FLOW_ID;

    public static final Fields STREAM_STATS_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_STATS_ID = "stats";

    public static final Fields STREAM_FAIL_FIELDS = new Fields(FIELD_ID_FLOW_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_FAIL_ID = "fail";

    private static final Fields STREAM_BLACKLIST_FIELDS = new Fields(FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_BLACKLIST_ID = "blacklist";

    @Override
    protected void handle(Tuple input, PingContext pingContext) throws Exception {
        updateFailReporter(input, pingContext);
        super.handle(input, pingContext);
    }

    @Override
    protected void handleSuccess(Tuple input, PingContext pingContext) throws PipelineException {
        Values output = new Values(pingContext, pullContext(input));
        getOutput().emit(STREAM_STATS_ID, input, output);
    }

    @Override
    protected void handleError(Tuple input, PingContext pingContext) throws PipelineException {
        if (pingContext.isPermanentError()) {
            Values output = new Values(pingContext, pullContext(input));
            getOutput().emit(STREAM_BLACKLIST_ID, input, output);
        }
    }

    private void updateFailReporter(Tuple input, PingContext pingContext) throws PipelineException {
        Values output = new Values(pingContext.getFlowId(), pingContext, pullContext(input));
        getOutput().emit(STREAM_FAIL_ID, input, output);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_STATS_ID, STREAM_STATS_FIELDS);
        outputManager.declareStream(STREAM_FAIL_ID, STREAM_FAIL_FIELDS);
        outputManager.declareStream(STREAM_BLACKLIST_ID, STREAM_BLACKLIST_FIELDS);
    }
}
