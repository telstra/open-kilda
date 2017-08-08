package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.messaging.command.flow.BaseFlow;
import org.bitbucket.openkilda.messaging.command.flow.BaseInstallFlow;
import org.bitbucket.openkilda.messaging.command.flow.RemoveFlow;
import org.bitbucket.openkilda.messaging.payload.ResourcePool;
import org.bitbucket.openkilda.pce.model.Flow;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Utils class contains basic utilities and constants.
 */
final class Utils {
    private static final ResourcePool cookiePool = new ResourcePool(1, 65534);
    private static final ResourcePool transitVlanPool = new ResourcePool(1, 4094);

    static Integer allocateCookie() {
        return cookiePool.allocate();
    }

    static Integer allocateCookie(Integer cookie) {
        return cookiePool.allocate(cookie);
    }

    static void deallocateCookie(Integer cookie) {
        cookiePool.deallocate(cookie);
    }

    static Integer allocateTransitVlan() {
        return transitVlanPool.allocate();
    }

    static Integer allocateTransitVlan(Integer transitVlan) {
        return transitVlanPool.allocate(transitVlan);
    }

    static void deallocateTransitVlan(Integer transitVlan) {
        transitVlanPool.deallocate(transitVlan);
    }

    /**
     * Return timestamp.
     *
     * @return String timestamp
     */
    public static String getIsoTimestamp() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
    }

    Set<BaseInstallFlow> getInstallationCommands(Flow flow) {
        return null;
    }

    Set<RemoveFlow> getRemovalCommands(Flow flow) {
        return null;
    }

    Set<BaseFlow> getDeltaCommands(Flow oldFlow, Flow newFlow) {
        return null;
    }
}
