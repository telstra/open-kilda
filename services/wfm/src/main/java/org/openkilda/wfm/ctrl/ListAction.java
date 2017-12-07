package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.openkilda.messaging.ctrl.ResponseData;
import org.openkilda.wfm.MessageFormatException;
import org.openkilda.wfm.UnsupportedActionException;

public class ListAction extends CtrlSubAction {
    public ListAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        emitResponse(new ResponseData(getBolt().getContext(), getMessage().getTopology()));
    }
}
