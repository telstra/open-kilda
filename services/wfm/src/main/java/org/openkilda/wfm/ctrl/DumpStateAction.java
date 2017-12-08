package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.wfm.MessageFormatException;
import org.openkilda.wfm.UnsupportedActionException;

public class DumpStateAction extends CtrlEmbeddedAction {
    public DumpStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        AbstractDumpState state = getMaster().getBolt().dumpState();
        emitResponse(new DumpStateResponseData(getBolt().getContext(), getMessage().getTopology(), state));
    }
}
