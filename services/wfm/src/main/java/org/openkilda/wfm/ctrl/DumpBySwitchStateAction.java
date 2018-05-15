package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.TopologyContext;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.DumpStateBySwitchRequestData;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;

public class DumpBySwitchStateAction extends CtrlEmbeddedAction {

    private final DumpStateBySwitchRequestData payload;

    public DumpBySwitchStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
        payload = (DumpStateBySwitchRequestData)message.getPayload();
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        AbstractDumpState state = getMaster().getBolt().dumpStateBySwitchId(payload.getSwitchId());
        TopologyContext context = getBolt().getContext();
        emitResponse(new DumpStateResponseData(context.getThisComponentId(),
                context.getThisTaskId(), getMessage().getTopology(), state));
    }
}
