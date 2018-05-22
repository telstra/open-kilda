package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.TopologyContext;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;

public class ListAction extends CtrlEmbeddedAction {

    public ListAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        TopologyContext context = getBolt().getContext();

        emitResponse(new ResponseData(context.getThisComponentId(),
                context.getThisTaskId(),
                getMessage().getTopology()));
    }
}
