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

import org.openkilda.messaging.model.PingReport;
import org.openkilda.messaging.model.PingReport.State;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.ping.model.FlowObserver;
import org.openkilda.wfm.topology.ping.model.FlowRef;
import org.openkilda.wfm.topology.ping.model.PingContext;
import org.openkilda.wfm.topology.ping.model.PingObserver;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FailReporter extends Abstract {
    public static final String BOLT_ID = ComponentId.FAIL_REPORTER.toString();

    public static final String FIELD_ID_PING_REPORT = FlowStatusEncoder.FIELD_ID_PING_REPORT;

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_PING_REPORT, FIELD_ID_CONTEXT);

    private final long failDelay;
    private final long failReset;
    private HashMap<String, FlowObserver> flowsStatusMap;
    private PingObserver.PingObserverBuilder pingStatusBuilder;

    public FailReporter(int failDelay, int failReset) {
        this.failDelay = TimeUnit.SECONDS.toMillis(failDelay);
        this.failReset = TimeUnit.SECONDS.toMillis(failReset);
    }

    @Override
    protected void init() {
        super.init();

        pingStatusBuilder = PingObserver.builder()
                .failDelay(failDelay)
                .failReset(failReset);
        flowsStatusMap = new HashMap<>();
    }

    @Override
    protected void handleInput(Tuple input) throws Exception {
        String component = input.getSourceComponent();

        if (TickDeduplicator.BOLT_ID.equals(component)) {
            handleTick(input);
        } else if (FlowFetcher.BOLT_ID.equals(component)) {
            handleCacheExpiration(input);
        } else if (PeriodicResultManager.BOLT_ID.equals(component)) {
            handlePing(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleTick(Tuple input) throws PipelineException {
        final long now = input.getLongByField(MonotonicTick.FIELD_ID_TIME_MILLIS);

        for (Iterator<Entry<String, FlowObserver>> iterator = flowsStatusMap.entrySet().iterator();
                 iterator.hasNext(); ) {

            Entry<String, FlowObserver> entry = iterator.next();
            FlowObserver flowObserver = entry.getValue();

            final String flowId = entry.getKey();
            if (flowObserver.isGarbage()) {
                iterator.remove();
                log.info("Remove flow observer (flowId: {})", flowId);
                continue;
            }

            State state = flowObserver.timeTick(now);
            if (state != null) {
                report(input, flowId, flowObserver, state);
            }
        }
    }

    private void handleCacheExpiration(Tuple input) throws PipelineException {
        FlowRef ref = pullFlowRef(input);
        FlowObserver status = flowsStatusMap.get(ref.flowId);
        if (status != null) {
            status.remove(ref.cookie);
        }
    }

    private void handlePing(Tuple input) throws PipelineException {
        PingContext pingContext = pullPingContext(input);
        if (pingContext.isPermanentError()) {
            log.warn("Do not include permanent ping error in report ({})", pingContext);
            return;
        }

        FlowObserver flowObserver = this.flowsStatusMap.computeIfAbsent(
                pingContext.getFlowId(), k -> new FlowObserver(pingContext.getFlowId(), pingStatusBuilder));
        flowObserver.update(pingContext);
    }

    private void report(Tuple input, String flowId, FlowObserver flowObserver, State state)
            throws PipelineException {
        String logMessage = String.format("{FLOW-PING} Flow %s become %s", flowId, state);
        if (state != State.OPERATIONAL) {
            String cookies = flowObserver.getFlowTreadsInState(state).stream()
                    .map(cookie -> String.format("0x%016x", cookie))
                    .collect(Collectors.joining(", "));
            if (!cookies.isEmpty()) {
                logMessage += String.format("(%s)", cookies);
            }
        }

        log.info(logMessage);

        Values output = new Values(new PingReport(flowId, state), pullContext(input));
        getOutput().emit(input, output);
    }

    private FlowRef pullFlowRef(Tuple input) throws PipelineException {
        return pullValue(input, FlowFetcher.FIELD_FLOW_REF, FlowRef.class);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }
}
