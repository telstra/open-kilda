/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.hubandspoke;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt.CoordinatorCommand;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

/**
 * This class provides callbacks and timeout handlers for asynchronous operations.
 */
abstract class CoordinatedBolt extends AbstractBolt implements TimeoutCallback {
    static final String COMMAND_FIELD = "command";
    static final String TIMEOUT_FIELD = "timeout_ms";

    private final boolean autoAck;
    private final int defaultTimeout;

    CoordinatedBolt(boolean autoAck, int defaultTimeout) {
        this.autoAck = autoAck;
        this.defaultTimeout = defaultTimeout;
    }

    @Override
    protected void dispatch(Tuple input) throws Exception {
        if (CoordinatorBolt.ID.equals(input.getSourceComponent())) {
            String key = input.getStringByField(MessageTranslator.KEY_FIELD);
            onTimeout(key, input);
        } else {
            super.dispatch(input);
        }
    }

    @Override
    protected void ack(Tuple input) {
        if (autoAck) {
            super.ack(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(CoordinatorBolt.INCOME_STREAM, new Fields(MessageTranslator.KEY_FIELD,
                COMMAND_FIELD, TIMEOUT_FIELD, AbstractBolt.FIELD_ID_CONTEXT));
    }

    /**
     * Should be called once operation is finished and callback/timer should be cancelled.
     * @param key request's identifier.
     */
    protected void cancelCallback(String key, Tuple tuple) {
        emitWithContext(CoordinatorBolt.INCOME_STREAM, tuple,
                        new Values(key, CoordinatorCommand.CANCEL_CALLBACK, 0));
    }

    /**
     * Add callback for operation that will be called when executions of command finishes. Default timout value will be
     * used.
     * @param key operation identifier.
     */
    protected void registerCallback(String key, Tuple tuple) {
        registerCallback(key, defaultTimeout, tuple);
    }

    /**
     * Add callback with specified timeout value.
     * @param key operation identifier.
     * @param timeout how long coordinator waits for a response. If no response received - timeout error occurs.
     */
    protected void registerCallback(String key, int timeout, Tuple tuple) {
        emitWithContext(CoordinatorBolt.INCOME_STREAM, tuple,
                        new Values(key, CoordinatorCommand.REQUEST_CALLBACK, timeout));
    }
}
