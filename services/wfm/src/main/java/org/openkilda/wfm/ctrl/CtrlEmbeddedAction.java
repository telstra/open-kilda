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
