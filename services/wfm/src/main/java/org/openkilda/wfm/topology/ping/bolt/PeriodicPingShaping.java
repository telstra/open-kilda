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
import org.openkilda.wfm.topology.ping.model.PingContext.Kinds;
import org.openkilda.wfm.topology.ping.model.ShapingBurstCalculator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.LinkedList;

@Slf4j
public class PeriodicPingShaping extends Abstract {
    public static final String BOLT_ID = ComponentId.PERIODIC_PING_SHAPING.toString();

    public static final String FIELD_ID_FLOW_ID = FlowFetcher.FIELD_ID_FLOW_ID;
    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_FLOW_ID, FIELD_ID_PING, FIELD_ID_CONTEXT);

    private final int windowSize;
    private ShapingBurstCalculator burstCalculator = null;

    private long proxyCounter = 0;
    private long proxyAllowance = 0;
    private LinkedList<Values> backlog = null;

    public PeriodicPingShaping(int pingInterval) {
        int window = pingInterval * 5;
        window += pingInterval / 2;
        window += pingInterval % 2 > 0 ? 1 : 0;
        this.windowSize =  window;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();

        if (FlowFetcher.BOLT_ID.equals(source)) {
            handlePing(input);
        } else if (MonotonicTick.BOLT_ID.equals(source)) {
            handleMonotonicTick(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handlePing(Tuple input) throws PipelineException {
        Values output = extract(input);

        if (burstCalculator == null) {
            // silent proxy record, due to disabled shaping
            emit(input, output);
            return;
        }

        PingContext ping = pullPingContext(input);
        if (ping.getKind() != Kinds.PERIODIC) {
            emit(input, output);
        }

        burstCalculator.increment();
        proxy(input, output, ping);
        logStats();
    }

    private void handleMonotonicTick(Tuple input) {
        burstCalculator.slide();
        proxyAllowance = calcProxyAllowance();
        proxyCounter = 0;

        log.debug("Emit ping records from backlog");
        releaseBurst(input);
    }

    private void proxy(Tuple input, Values output, PingContext ping) {
        log.debug("Proxy(delay) ping record {}", ping.getFlowId());
        backlog.addLast(output);
        releaseBurst(input);
    }

    private void releaseBurst(Tuple input) {
        while (proxyCounter < proxyAllowance && !backlog.isEmpty()) {
            proxyCounter += 1;

            Values output = backlog.removeFirst();
            emit(input, output);
        }
        logStats();
    }

    private Values extract(Tuple input) {
        return new Values(
                input.getValueByField(FIELD_ID_FLOW_ID),
                input.getValueByField(FIELD_ID_PING),
                input.getValueByField(FIELD_ID_CONTEXT));
    }

    private void emit(Tuple input, Values output) {
        getOutput().emit(input, output);
    }

    private long calcProxyAllowance() {
        long allowance = burstCalculator.getBurstAmount();
        long reserve = (long) (allowance * .1);
        if (reserve == 0) {
            reserve = 1;
        }

        log.debug("Recalculate burst size, new value: {} + {}", allowance, reserve);
        return allowance + reserve;
    }

    private void logStats() {
        log.debug("Shaping stats: proxied {}, allowance {}, backlog {}", proxyCounter, proxyAllowance, backlog.size());
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputManager) {
        outputManager.declare(STREAM_FIELDS);
    }

    @Override
    protected void init() {
        super.init();

        if (0 < windowSize) {
            burstCalculator = new ShapingBurstCalculator(windowSize);
            backlog = new LinkedList<>();
        }
    }
}
