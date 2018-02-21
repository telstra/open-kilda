package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import org.apache.storm.task.TopologyContext;
import org.openkilda.messaging.ctrl.ResponseData;

@VisibleForTesting
class ClearStateAction extends CtrlEmbeddedAction {
    ClearStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle() throws JsonProcessingException {
        getMaster().getBolt().clearState();

        TopologyContext context = getBolt().getContext();
        emitResponse(new ResponseData(context.getThisComponentId(),
                context.getThisTaskId(), getMessage().getTopology()));

    }
}
