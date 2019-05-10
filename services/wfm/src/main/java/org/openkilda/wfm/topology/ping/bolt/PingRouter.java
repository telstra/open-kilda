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

import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class PingRouter extends Abstract {
    public static final String BOLT_ID = ComponentId.PING_ROUTER.toString();

    public static final String FIELD_ID_PING_ID = "ping.id";
    public static final String FIELD_ID_PING_MATCH = "ping.match";
    public static final String FIELD_ID_RESPONSE = "ping.response";

    public static final Fields STREAM_BLACKLIST_FILTER_FIELDS = new Fields(
            FIELD_ID_PING_MATCH, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_BLACKLIST_FILTER_ID = "blacklist.filter";

    public static final Fields STREAM_BLACKLIST_UPDATE_FIELDS = STREAM_BLACKLIST_FILTER_FIELDS;
    public static final String STREAM_BLACKLIST_UPDATE_ID = "blacklist.update";

    public static final Fields STREAM_REQUEST_FIELDS = new Fields(
            FIELD_ID_PING_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_REQUEST_ID = "request";

    public static final Fields STREAM_RESPONSE_FIELDS = new Fields(
            FIELD_ID_PING_ID, FIELD_ID_RESPONSE, FIELD_ID_CONTEXT);
    public static final String STREAM_RESPONSE_ID = "response";

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String component = input.getSourceComponent();
        if (PingProducer.BOLT_ID.equals(component)) {
            routePingProducer(input);
        } else if (Blacklist.BOLT_ID.equals(component)) {
            routeBlacklist(input);
        } else if (InputRouter.BOLT_ID.equals(component)) {
            routePingResponse(input);
        } else if (PeriodicResultManager.BOLT_ID.equals(component)) {
            routePeriodicResultManager(input);
        } else {
            unhandledInput(input);
        }
    }

    private void routePingProducer(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        if (pingContext.getKind() == Kinds.PERIODIC) {
            emitBalcklist(input, pingContext);
        } else {
            emitRequest(input, pingContext);
        }
    }

    private void routeBlacklist(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        emitRequest(input, pingContext);
    }

    private void routePingResponse(Tuple input) throws PipelineException {
        PingResponse response = pullPingResponse(input);
        Values output = new Values(response.getPingId(), response, pullContext(input));
        getOutput().emit(STREAM_RESPONSE_ID, input, output);
    }

    private void routePeriodicResultManager(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        Values output = new Values(pingContext.getPing(), pingContext, pullContext(input));
        getOutput().emit(STREAM_BLACKLIST_UPDATE_ID, input, output);
    }

    private void emitBalcklist(Tuple input, PingContext pingContext) throws PipelineException {
        Values output = new Values(pingContext.getPing(), pingContext, pullContext(input));
        getOutput().emit(STREAM_BLACKLIST_FILTER_ID, input, output);
    }

    private void emitRequest(Tuple input, PingContext pingContext) throws PipelineException {
        Values output = new Values(pingContext.getPingId(), pingContext, pullContext(input));
        getOutput().emit(STREAM_REQUEST_ID, input, output);
    }

    private PingResponse pullPingResponse(Tuple input) throws PipelineException {
        return pullValue(input, InputRouter.FIELD_ID_PING_RESPONSE, PingResponse.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_BLACKLIST_FILTER_ID, STREAM_BLACKLIST_FILTER_FIELDS);
        outputManager.declareStream(STREAM_BLACKLIST_UPDATE_ID, STREAM_BLACKLIST_UPDATE_FIELDS);
        outputManager.declareStream(STREAM_REQUEST_ID, STREAM_REQUEST_FIELDS);
        outputManager.declareStream(STREAM_RESPONSE_ID, STREAM_RESPONSE_FIELDS);
    }
}
