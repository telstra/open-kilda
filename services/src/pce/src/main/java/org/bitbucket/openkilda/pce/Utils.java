package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.command.flow.BaseInstallFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.pce.model.Flow;

import java.util.List;

/**
 * Utils class contains basic utilities and constants.
 */
final class Utils {
    List<BaseInstallFlow> getInstallationCommands(Flow flow) {
        return null;
    }

    List<RemoveFlow> getRemovalCommands(Flow flow) {
        return null;
    }
}
