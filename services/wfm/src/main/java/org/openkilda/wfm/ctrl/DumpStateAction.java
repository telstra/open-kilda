package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.TopologyContext;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;

public class DumpStateAction extends CtrlEmbeddedAction {

    public DumpStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        AbstractDumpState state = getMaster().getBolt().dumpState();
        TopologyContext context = getBolt().getContext();
        emitResponse(new DumpStateResponseData(context.getThisComponentId(),
                context.getThisTaskId(), getMessage().getTopology(), state));
    }
}
