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

package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.Destination;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.wfm.AbstractEmbeddedAction;
import org.openkilda.wfm.protocol.KafkaMessage;

import com.fasterxml.jackson.core.JsonProcessingException;

public abstract class CtrlEmbeddedAction extends AbstractEmbeddedAction {
    private final CtrlAction master;
    private final RouteMessage message;

    public CtrlEmbeddedAction(CtrlAction master, RouteMessage message) {
        super(master.getBolt(), master.getTuple());
        this.master = master;
        this.message = message;
    }

    protected void emitResponse(ResponseData payload) throws JsonProcessingException {
        CtrlResponse response = new CtrlResponse(
                payload, System.currentTimeMillis(),
                getMessage().getCorrelationId(), Destination.CTRL_CLIENT);
        KafkaMessage message = new KafkaMessage(response);

        getOutputCollector().emit(getMaster().getStreamId(), getTuple(), message.pack());
    }

    protected CtrlAction getMaster() {
        return master;
    }

    protected RouteMessage getMessage() {
        return message;
    }
}
