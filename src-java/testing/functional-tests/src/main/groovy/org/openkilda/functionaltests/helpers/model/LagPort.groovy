package org.openkilda.functionaltests.helpers.model

import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_LAG_LOGICAL_PORT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v2.switches.LagPortRequest
import org.openkilda.northbound.dto.v2.switches.LagPortResponse
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j

@Slf4j
@EqualsAndHashCode(excludes = 'northboundV2, cleanupManager')
@ToString(includeNames = true, excludes = 'northboundV2, cleanupManager')
class LagPort {

    SwitchId switchId
    Set<Integer> portNumbers
    int logicalPortNumber
    boolean lacpReply

    @JsonIgnore
    NorthboundServiceV2 northboundV2
    @JsonIgnore
    CleanupManager cleanupManager

    LagPort(SwitchId switchId,
            Set<Integer> portNumbers,
            NorthboundServiceV2 northboundV2,
            CleanupManager cleanupManager) {
        this.switchId = switchId
        this.portNumbers = portNumbers
        this.northboundV2 = northboundV2
        this.cleanupManager = cleanupManager
    }

    LagPort(SwitchId switchId,
            int logicalPortNumber,
            List<Integer> portNumbers,
            boolean lacpReply,
            NorthboundServiceV2 northboundV2,
            CleanupManager cleanupManager) {
        this.switchId = switchId
        this.logicalPortNumber = logicalPortNumber
        this.lacpReply = lacpReply
        this.portNumbers = portNumbers
        this.northboundV2 = northboundV2
        this.cleanupManager = cleanupManager
    }


    LagPort create(boolean lacpReply = null) {
        def lagDetails = northboundV2.createLagLogicalPort(switchId, new LagPortRequest(portNumbers , lacpReply))
        cleanupManager.addAction(DELETE_LAG_LOGICAL_PORT, { northboundV2.deleteLagLogicalPort(switchId, lagDetails.logicalPortNumber) })
        new LagPort(switchId, lagDetails.logicalPortNumber, lagDetails.portNumbers, lagDetails.lacpReply, northboundV2, cleanupManager)
    }

    LagPort update(LagPortRequest updateRequest) {
        def lagDetails = northboundV2.updateLagLogicalPort(switchId, logicalPortNumber, updateRequest)
        new LagPort(switchId, lagDetails.logicalPortNumber, lagDetails.portNumbers, lagDetails.lacpReply, northboundV2, cleanupManager)
    }

    LagPortResponse sendDeleteRequest() {
        northboundV2.deleteLagLogicalPort(switchId, logicalPortNumber)
    }

    LagPortResponse delete() {
        def lagDetails = sendDeleteRequest()
        wait(WAIT_OFFSET) {
            assert !northboundV2.getLagLogicalPort(switchId).logicalPortNumber.contains(logicalPortNumber)
        }
        return lagDetails
    }
}
