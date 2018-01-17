package org.openkilda.wfm.ctrl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.annotations.VisibleForTesting;
import org.openkilda.wfm.MessageFormatException;
import org.openkilda.wfm.UnsupportedActionException;

@VisibleForTesting
class ClearStateAction extends CtrlEmbeddedAction {
    ClearStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        getMaster().getBolt().clearState();
    }
}
