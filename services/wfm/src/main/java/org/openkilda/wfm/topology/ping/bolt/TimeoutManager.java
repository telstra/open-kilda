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
import org.openkilda.messaging.floodlight.request.PingRequest;
import org.openkilda.messaging.floodlight.response.PingResponse;
import org.openkilda.messaging.model.Ping.Errors;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.ExpirableMap;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.TimeoutDescriptor;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TimeoutManager extends Abstract {
    public static final String BOLT_ID = ComponentId.TIMEOUT_MANAGER.toString();

    public static final String FIELD_ID_FLOW_ID = Utils.FLOW_ID;
    public static final String FIELD_ID_PAYLOAD = SpeakerEncoder.FIELD_ID_PAYLOAD;

    public static final Fields STREAM_REQUEST_FIELDS = new Fields(FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);
    public static final String STREAM_REQUEST_ID = "request";

    public static final Fields STREAM_RESPONSE_FIELDS = new Fields(FIELD_ID_FLOW_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);
    public static final String STREAM_RESPONSE_ID = "response";

    private final long pingTimeout;

    private ExpirableMap<UUID, TimeoutDescriptor> pendingPings;

    public TimeoutManager(int pingTimeout) {
        this.pingTimeout = TimeUnit.SECONDS.toMillis(pingTimeout);
    }

    @Override
    protected void init() {
        super.init();

        pendingPings = new ExpirableMap<>();
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String component = input.getSourceComponent();

        if (PingRouter.BOLT_ID.equals(component)) {
            handleRouter(input);
        } else if (MonotonicTick.BOLT_ID.equals(component)) {
            handleTimeTick(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleRouter(Tuple input) throws PipelineException {
        String stream = input.getSourceStreamId();

        if (PingRouter.STREAM_REQUEST_ID.equals(stream)) {
            handleRequest(input);
        } else if (PingRouter.STREAM_RESPONSE_ID.equals(stream)) {
            handleResponse(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleTimeTick(Tuple input) {
        final long now = input.getLongByField(MonotonicTick.FIELD_ID_TIME_MILLIS);
        log.debug("Pending ping queue size: {}", pendingPings.size());
        for (TimeoutDescriptor descriptor : pendingPings.expire(now)) {
            emitTimeout(input, descriptor, now);
        }
    }

    private void handleRequest(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        CommandContext commandContext = pullContext(input);

        scheduleTimeout(pingContext, commandContext);
        emitRequest(input, pingContext, commandContext);
    }

    private void handleResponse(Tuple input) throws PipelineException {
        PingResponse response = pullPingResponse(input);
        log.debug("Got ping response pingId={}", response.getPingId());

        TimeoutDescriptor descriptor = pendingPings.remove(response.getPingId());
        if (descriptor == null) {
            log.warn("There is no pending request matching ping response {}", response.getPingId());
        } else {
            cancelTimeout(descriptor);
            emitResponse(input, descriptor, response);
        }
    }

    private void scheduleTimeout(PingContext pingContext, CommandContext commandContext) {
        long timeout = pingTimeout;
        if (pingContext.getTimeout() != null) {
            timeout = pingContext.getTimeout();
        }
        log.debug("Schedule timeout for {} in {} ms", pingContext, timeout);

        long expireAt = System.currentTimeMillis() + timeout;
        TimeoutDescriptor descriptor = new TimeoutDescriptor(expireAt, pingContext, commandContext);
        pendingPings.put(pingContext.getPingId(), descriptor);
    }

    private void cancelTimeout(TimeoutDescriptor descriptor) {
        descriptor.setActive(false);
    }

    private void emitRequest(Tuple input, PingContext pingContext, CommandContext commandContext) {
        final PingRequest request = new PingRequest(pingContext.getPing());
        log.debug("Emit {} ping request {}", pingContext.getKind(), request);

        Values output = new Values(request, commandContext);
        getOutput().emit(STREAM_REQUEST_ID, input, output);
    }

    private void emitResponse(Tuple input, TimeoutDescriptor descriptor, PingResponse response)
            throws PipelineException {
        descriptor.getCommandContext().merge(pullContext(input));

        PingContext pingContext = descriptor.getPingContext().toBuilder()
                .timestamp(response.getTimestamp())
                .error(response.getError())
                .meters(response.getMeters())
                .build();

        Values output = new Values(pingContext.getFlowId(), pingContext, descriptor.getCommandContext());
        getOutput().emit(STREAM_RESPONSE_ID, input, output);
    }

    private void emitTimeout(Tuple input, TimeoutDescriptor descriptor, long timestamp) {
        PingContext pingTimeout = descriptor.getPingContext().toBuilder()
                .timestamp(timestamp)
                .error(Errors.TIMEOUT)
                .build();
        log.debug("{} is timed out", pingTimeout);
        Values output = new Values(pingTimeout.getFlowId(), pingTimeout, descriptor.getCommandContext());
        getOutput().emit(STREAM_RESPONSE_ID, input, output);
    }

    private PingResponse pullPingResponse(Tuple input) throws PipelineException {
        return pullValue(input, PingRouter.FIELD_ID_RESPONSE, PingResponse.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declareStream(STREAM_REQUEST_ID, STREAM_REQUEST_FIELDS);
        outputManager.declareStream(STREAM_RESPONSE_ID, STREAM_RESPONSE_FIELDS);
    }
}
