package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.wfm.IKildaBolt;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

public interface ICtrlBolt extends IKildaBolt {
    AbstractDumpState dumpState();

    String getCtrlStreamId();

    @VisibleForTesting
    default void clearState() {
    }

    AbstractDumpState dumpStateBySwitchId(SwitchId switchId);

    default Optional<AbstractDumpState> dumpResorceCacheState() {
        return Optional.empty();
    }
}
