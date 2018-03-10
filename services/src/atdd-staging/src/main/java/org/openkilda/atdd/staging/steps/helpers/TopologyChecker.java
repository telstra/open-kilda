package org.openkilda.atdd.staging.steps.helpers;

import org.apache.commons.lang3.StringUtils;
import org.openkilda.atdd.staging.model.topology.TopologyDefinition;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.info.event.SwitchInfoData;

import java.util.List;
import java.util.Optional;

public final class TopologyChecker {

    /**
     * Check whether all expected switches match the actual.
     *
     * @param actualSwitches the list of switches to match
     * @param expectedSwitches the list of expected switches
     * @return whether the switch lists match
     */
    public static boolean matchSwitches(List<SwitchInfoData> actualSwitches,
            List<TopologyDefinition.Switch> expectedSwitches) {
        if (actualSwitches.size() != expectedSwitches.size()) {
            return false;
        }

        for (TopologyDefinition.Switch switchDef : expectedSwitches) {
            Optional<SwitchInfoData> switchInfoData = actualSwitches.stream()
                    .filter(switchInfo -> StringUtils.equalsIgnoreCase(switchInfo.getSwitchId(), switchDef.getDpId()))
                    .findFirst();
            if (!switchInfoData.isPresent()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check whether all expected links match the actual.
     *
     * @param actualLinks the list of links to match
     * @param expectedLinks the list of expected links
     * @return whether the link lists match
     */
    public static boolean matchLinks(List<IslInfoData> actualLinks, List<TopologyDefinition.Isl> expectedLinks) {
        for (TopologyDefinition.Isl linkDef : expectedLinks) {
            boolean isLinkExist = actualLinks.stream()
                    .anyMatch(isl -> isIslEqual(linkDef, isl));
            if (!isLinkExist) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check whether the link equals the link definition.
     *
     * @param linkDef the link definition
     * @param islInfoData the link to match
     * @return whether the link matches the definition
     */
    public static boolean isIslEqual(TopologyDefinition.Isl linkDef, IslInfoData islInfoData) {
        PathNode discoveredSrcNode = islInfoData.getPath().get(0);
        PathNode discoveredDstNode = islInfoData.getPath().get(1);
        return discoveredSrcNode.getSwitchId().equalsIgnoreCase(linkDef.getSrcSwitch().getDpId()) &&
                discoveredSrcNode.getPortNo() == linkDef.getSrcPort() &&
                discoveredDstNode.getSwitchId().equalsIgnoreCase(linkDef.getDstSwitch().getDpId()) &&
                discoveredDstNode.getPortNo() == linkDef.getDstPort();
    }

    private TopologyChecker() {
    }
}
