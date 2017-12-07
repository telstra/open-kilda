package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.ctrl.CtrlResponse;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.wfm.AbstractAction;
import org.openkilda.wfm.protocol.KafkaMessage;

public abstract class CtrlSubAction extends AbstractAction {
    private final CtrlAction master;
    private final RouteMessage message;

    public CtrlSubAction(CtrlAction master, RouteMessage message) {
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
