package org.openkilda.functionaltests.helpers.model

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.functionaltests.helpers.Wrappers.wait
import static org.openkilda.functionaltests.helpers.model.PortExtended.closeBlinker
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.CLEAN_LINK_DELAY
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISL_PARAMETERS
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.messaging.info.event.IslChangeType.MOVED
import static org.openkilda.testing.Constants.ISL_RECOVER_TIMEOUT
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.KildaProperties
import org.openkilda.functionaltests.helpers.thread.PortBlinker
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.BatchResults
import org.openkilda.northbound.dto.v1.links.LinkEnableBfdDto
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.northbound.dto.v1.links.LinkPropsDto
import org.openkilda.northbound.dto.v1.links.LinkUnderMaintenanceDto
import org.openkilda.northbound.dto.v2.links.BfdProperties
import org.openkilda.northbound.dto.v2.links.BfdPropertiesPayload
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.lockkeeper.LockKeeperService
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized

import java.time.Instant

@EqualsAndHashCode(excludes = 'northbound, northboundV2, database, lockKeeper, topology, cleanupManager')
class IslExtended {

    Isl isl
    @JsonIgnore
    NorthboundService northbound
    @JsonIgnore
    NorthboundServiceV2 northboundV2
    @JsonIgnore
    Database database
    @JsonIgnore
    LockKeeperService lockKeeper
    @JsonIgnore
    TopologyDefinition topology
    @JsonIgnore
    CleanupManager cleanupManager

    IslExtended(Isl isl,
                NorthboundService northbound,
                NorthboundServiceV2 northboundV2,
                Database database,
                LockKeeperService lockKeeper,
                TopologyDefinition topology,
                CleanupManager cleanupManager) {

        this.isl = isl
        this.northbound = northbound
        this.northboundV2 = northboundV2
        this.database = database
        this.lockKeeper = lockKeeper
        this.topology = topology
        this.cleanupManager = cleanupManager
    }

    @Override
    String toString() {
        isl.toString()
    }

    @Memoized
    PortExtended getSrcEndpoint() {
        return new PortExtended(isl.srcSwitch, isl.srcPort, northbound, northboundV2, cleanupManager)
    }

    @Memoized
    PortExtended getDstEndpoint() {
        return new PortExtended(isl.dstSwitch, isl.dstPort, northbound, northboundV2, cleanupManager)
    }

    @Memoized
    IslExtended getReversed(){
        new IslExtended(isl.reversed, northbound, northboundV2, database, lockKeeper, topology, cleanupManager)
    }

    List<SwitchId> getInvolvedSwIds() {
        [isl.srcSwitch.dpId, isl.dstSwitch.dpId]
    }

    SwitchId getDstSwId(){
        isl.dstSwitch.dpId
    }

    SwitchId getSrcSwId(){
        isl.srcSwitch.dpId
    }

    Switch getDstSw(){
        isl.dstSwitch
    }

    Switch getSrcSw(){
        isl.srcSwitch
    }

    Integer getSrcPort() {
        isl.srcPort
    }

    Integer getDstPort() {
        isl.dstPort
    }

    boolean hasASwitch() {
        isl.aswitch?.inPort && isl.aswitch?.outPort
    }

    ASwitchFlow getASwitch() {
        return isl.aswitch
    }

    def breakIt(CleanupAfter cleanupAfter = TEST) {
        cleanupManager.addAction(RESTORE_ISL,{restore()}, cleanupAfter)
        cleanupManager.addAction(RESET_ISLS_COST,{database.resetCosts(topology.isls)}, cleanupAfter)
        if (getInfo().state == DISCOVERED) {
            srcEndpoint.down(cleanupAfter, false)
        }
        waitForStatus(FAILED)
    }

    def restore() {
        if(getInfo()?.state != DISCOVERED) {
            withPool{
                [{srcEndpoint.up()}, {dstEndpoint.up()}].eachParallel{it()}
            }
        }
        waitForStatus(DISCOVERED)
    }

    void waitForStatus(IslChangeType expectedStatus, double timeout = ISL_RECOVER_TIMEOUT){
        wait(timeout) {
            List<IslInfoData> allLinksFromNb = northbound.getAllLinks()
            assert getInfo(allLinksFromNb, false)?.state == expectedStatus
            assert getInfo(allLinksFromNb, true)?.state == expectedStatus
        }
    }

    void waitForRoundTripStatus(IslChangeType forwardExpectedStatus, IslChangeType reversedExpectedStatus = forwardExpectedStatus,
                                double timeout = WAIT_OFFSET) {
        wait(timeout) {
            List<IslInfoData> allLinksFromNb = northbound.getAllLinks()
            assert getInfo(allLinksFromNb, false)?.roundTripStatus == forwardExpectedStatus
            assert getInfo(allLinksFromNb, true)?.roundTripStatus == reversedExpectedStatus
        }
    }

    /***
     * Finds certain ISL in list of 'IslInfoData' objects.
     * IslInfoData is returned from NB.
     * @param allLinksFromNb
     * @param isReversed is used to find reversed ISL
     * @return ISL details from NB
     */
    IslInfoData getInfo(List<IslInfoData> allLinksFromNb = northbound.getAllLinks(), boolean isReversed = false) {
        return allLinksFromNb.find { link ->
            def source = isReversed ? link.destination : link.source
            def destination = isReversed ? link.source : link.destination

            source.switchId == srcSwId && source.portNo == srcPort
                    && destination.switchId == dstSwId && destination.portNo
        }
    }

    boolean isIncludedInPath(List<IslExtended> path) {
        path.contains(this) || path.contains(reversed)
    }

    /***
     *
     * @return true if both forward and reverse ISL are present
     */
    boolean isPresent() {
        List<IslInfoData> allLinksFromNb = northbound.getAllLinks()
        getInfo(allLinksFromNb, false) && getInfo(allLinksFromNb, true)
    }

    void setDelay(String bridgeName, Integer delayMs) {
        cleanupManager.addAction(CLEAN_LINK_DELAY, {lockKeeper.cleanupLinkDelay(bridgeName)})
        lockKeeper.setLinkDelay(bridgeName, delayMs)
    }

    void setDelayOnBothInterfaces(Integer delayMs) {
        setDelay(isl.srcSwitch.name + "-" + isl.srcPort, delayMs)
        setDelay(isl.dstSwitch.name + "-" + isl.dstPort, delayMs)
    }

    IslInfoData getNbDetails() {
        northbound.getLink(isl)
    }

    List<FlowPayload> getRelatedFlows() {
        northbound.getLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)
    }

    BfdPropertiesPayload getBfdDetails() {
        northboundV2.getLinkBfd(isl)
    }

    def delete(boolean isForce = false) {
        cleanupManager.addAction(RESTORE_ISL, {
            def links = northbound.getAllLinks()
            def forwardIsl = getInfo(links, false)
            def reverseIsl = getInfo(links, true)
            if (forwardIsl && reverseIsl && forwardIsl.state == DISCOVERED && reverseIsl.state == DISCOVERED) {
                srcEndpoint.down()
                dstEndpoint.down()
            }
            restore()
            database.resetCosts([isl, isl.reversed])
        })
        northbound.deleteLink(toLinkParameters(), isForce)
    }

    BfdPropertiesPayload setBfd(BfdProperties props = new BfdProperties(350L, (short) 3)) {
        cleanupManager.addAction(OTHER, {deleteBfd()})
        northboundV2.setLinkBfd(isl, props)
    }

    def setBfdFromApiV1(boolean isEnabled) {
        cleanupManager.addAction(OTHER, {deleteBfd()})
        northbound.setLinkBfd(toLinkEnableBfd(isEnabled))
    }

    void deleteBfd() {
        northboundV2.deleteLinkBfd(isl)
    }

    def setMaintenance(boolean isUnderMaintenance, boolean isEvacuate) {
        cleanupManager.addAction(OTHER, { unsetMaintenance() })
        northbound.setLinkMaintenance(toLinkUnderMaintenance(isUnderMaintenance, isEvacuate))
    }

    def unsetMaintenance() {
        northbound.setLinkMaintenance(toLinkUnderMaintenance(false, false))
    }

    BatchResults updateCost(Integer cost) {
        update(["cost": cost.toString()])
    }

    BatchResults update(HashMap params) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES, {northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
        cleanupManager.addAction(RESET_ISLS_COST,{ database.resetCosts(topology.isls) })
        northbound.updateLinkProps([toLinkProps(params)])
    }

    def updateMaxBandwidth(long newMaxBandwidth) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES, { northbound.deleteLinkProps(northbound.getLinkProps(topology.isls))})
        northbound.updateLinkMaxBandwidth(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId,
                isl.dstPort, newMaxBandwidth)
    }

    def deleteCostProp(Integer cost){
        deleteProps(["cost": cost.toString()])
    }

    def deleteProps(HashMap params) {
        northbound.deleteLinkProps([toLinkProps(params)])
    }

    def blinkSrcEndpoint(){
        northbound.portDown(srcSwId, srcPort)
        northbound.portUp(srcSwId, srcPort)
    }

    List<String> rerouteFlows() {
        northbound.rerouteLinkFlows(isl.srcSwitch.dpId, isl.srcPort, isl.dstSwitch.dpId, isl.dstPort)
    }

    def replugDestination(IslExtended plugIntoIsl, boolean plugIntoSource, boolean portDown,
                          IslChangeType expectedRepluggedIslState = DISCOVERED) {

        def newIsl = replug(false, plugIntoIsl, plugIntoSource, portDown)
        newIsl.waitForStatus(expectedRepluggedIslState, KildaProperties.DISCOVERY_EXHAUSTED_INTERVAL + WAIT_OFFSET)

        cleanupManager.addAction(RESTORE_ISL, {
            if(newIsl.getInfo()) {
                newIsl.replug(true, this, !plugIntoSource, portDown)
                waitForStatus(DISCOVERED)
                newIsl.waitForStatus(MOVED)
                northbound.deleteLink(newIsl.toLinkParameters())
                wait(WAIT_OFFSET) { assert !newIsl.isPresent()}
            }
            database.resetCosts(topology.isls)
        }
        )
        return newIsl
    }

    /***
     * Simulates a physical ISL replug from one switch-port to another switch-port. Uses a-switch.
     *
     * Replugging the current ISL(should go through a-switch)
     * @param replugSource replug source or destination end of the ISL
     * @param plugIntoIsl The destination 'isl'. Usually a free link, which is connected to a-switch at one end
     * @param plugIntoSource Whether to connect to src or dst end of the dstIsl. Usually src end for not-connected ISLs
     * @param portDown Whether to simulate a 'port down' event when unplugging
     * @return New ISL which is expected to be discovered after the replug
     */
    IslExtended replug(boolean replugSource, IslExtended plugIntoIsl, boolean plugIntoSource, boolean portDown) {
        ASwitchFlow srcASwitch = getASwitch()
        ASwitchFlow dstASwitch = plugIntoIsl.getASwitch()
        //unplug
        List<Integer> portsToUnplug = Collections.singletonList(
                replugSource ? srcASwitch.inPort : srcASwitch.outPort)
        if (portDown) {
            lockKeeper.portsDown(portsToUnplug)
        }

        //change flow on aSwitch
        //delete old flow
        if (srcASwitch.inPort != null && srcASwitch.outPort != null) {
            lockKeeper.removeFlows(Arrays.asList(srcASwitch, srcASwitch.getReversed()))
        }
        //create new flow
        ASwitchFlow aswFlowForward = new ASwitchFlow(replugSource ? srcASwitch.outPort : srcASwitch.inPort,
                plugIntoSource ? dstASwitch.inPort : dstASwitch.outPort)
        lockKeeper.addFlows(Arrays.asList(aswFlowForward, aswFlowForward.getReversed()))

        //plug back
        if (portDown) {
            lockKeeper.portsUp(portsToUnplug)
        }
        def isl = Isl.factory(
                replugSource ? (plugIntoSource ? plugIntoIsl.srcSw : plugIntoIsl.dstSw) : isl.srcSwitch,
                replugSource ? (plugIntoSource ? plugIntoIsl.srcPort : plugIntoIsl.dstPort) : isl.srcPort,
                replugSource ? isl.dstSwitch : (plugIntoSource ? plugIntoIsl.srcSw : plugIntoIsl.dstSw),
                replugSource ? isl.getDstPort() : (plugIntoSource ? plugIntoIsl.srcPort : plugIntoIsl.dstPort),
                0, plugIntoSource ? aswFlowForward.getReversed() : aswFlowForward)

        return new IslExtended(isl, northbound, northboundV2, database, lockKeeper, topology, cleanupManager)
    }

    def getPortBlinkerForSource(long interval) {
        def blinker = srcEndpoint.getBlinker(interval)
        cleanupManager.addAction(RESTORE_ISL, {
            closeBlinker(blinker)
            restore()
            waitForStatus(DISCOVERED)
        })
        return blinker
    }

    /***
     * Database interaction
     */

    void setAvailableAndMaxBandwidthInDb(long value) {
        cleanupManager.addAction(RESET_ISL_PARAMETERS, {database.resetIslBandwidth(isl)})
        database.updateIslAvailableBandwidth(isl,value)
        database.updateIslMaxBandwidth(isl, value)
    }

    int getCostFromDb() {
        database.getIslCost(isl)
    }

    void updateLatencyInDb(long value) {
        database.updateIslsLatency([isl, isl.reversed], value)
    }

    /***
     * Updating available and max bandwidth for both forward and reverse ISLs
     * @param availableValue
     * @param maxValue
     */
    void updateAvailableAndMaxBandwidthInDb(long availableValue, long maxValue = availableValue) {
        cleanupManager.addAction(RESET_ISL_PARAMETERS, { database.resetIslsBandwidth([isl]) })
        database.updateIslsAvailableAndMaxBandwidth([isl], availableValue, maxValue)

    }

    Instant getTimeUnstableFromDb() {
        database.getIslTimeUnstable(isl)
    }

    boolean updateTimeUnstable(Instant newTimeUnstable) {
        database.updateIslTimeUnstable(isl, newTimeUnstable)
    }

    /**
     * Converts the Isl object to LinkUnderMaintenanceDto object.
     *
     */
    LinkUnderMaintenanceDto toLinkUnderMaintenance(boolean underMaintenance, boolean evacuate) {
        new LinkUnderMaintenanceDto(isl.srcSwitch.dpId.toString(), isl.srcPort,
                isl.dstSwitch.dpId.toString(), isl.dstPort, underMaintenance, evacuate)
    }

    LinkEnableBfdDto toLinkEnableBfd(boolean bfd) {
        new LinkEnableBfdDto(isl.srcSwitch.dpId.toString(), isl.srcPort,
                isl.dstSwitch.dpId.toString(), isl.dstPort, bfd)
    }

    /**
     * Converts the Isl object to LinkParametersDto object.
     *
     */
    LinkParametersDto toLinkParameters() {
        new LinkParametersDto(isl.srcSwitch.dpId.toString(), isl.srcPort,
                isl.dstSwitch.dpId.toString(), isl.dstPort)
    }

    /**
     * Converts the Isl to LinkPropsDto object.
     *
     * @param props Isl props to set when creating LinkPropsDto
     */
    LinkPropsDto toLinkProps(HashMap props) {
        new LinkPropsDto(isl.srcSwitch.dpId.toString(), isl.srcPort,
                isl.dstSwitch.dpId.toString(), isl.dstPort, props)
    }
}
