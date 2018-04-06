package org.openkilda.wfm.ctrl;

import com.google.common.annotations.VisibleForTesting;
import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.wfm.IKildaBolt;

import java.util.Optional;

public interface ICtrlBolt extends IKildaBolt {
    AbstractDumpState dumpState();

    String getCtrlStreamId();

    @VisibleForTesting
    default void clearState() { }

    AbstractDumpState dumpStateBySwitchId(String switchId);

    default Optional<AbstractDumpState> dumpResorceCacheState()
    {
        return Optional.empty();
    }
}
