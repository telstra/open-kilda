package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.wfm.IKildaBolt;

public interface ICtrlBolt extends IKildaBolt {
    AbstractDumpState dumpState();

    String getCtrlStreamId();
}
