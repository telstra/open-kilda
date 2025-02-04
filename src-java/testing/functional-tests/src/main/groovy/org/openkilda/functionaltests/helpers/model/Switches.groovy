package org.openkilda.functionaltests.helpers.model

import static groovyx.gpars.GParsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.openkilda.functionaltests.helpers.model.SwitchExtended.isS42Supported
import static org.openkilda.functionaltests.model.switches.Manufacturer.CENTEC
import static org.openkilda.functionaltests.model.switches.Manufacturer.NOVIFLOW
import static org.openkilda.functionaltests.model.switches.Manufacturer.OVS
import static org.openkilda.functionaltests.model.switches.Manufacturer.WB5164
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.factory.SwitchFactory
import org.openkilda.functionaltests.model.switches.Manufacturer
import org.openkilda.model.SwitchId
import org.openkilda.northbound.dto.v1.switches.SwitchDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2
import org.openkilda.testing.service.northbound.payloads.SwitchSyncExtendedResult
import org.openkilda.testing.service.northbound.payloads.SwitchValidationV2ExtendedResult

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component
@Scope(SCOPE_PROTOTYPE)
class Switches {

    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    SwitchFactory switchFactory

    List<SwitchExtended> switches

    Switches all() {
        switches = collectSwitches()
        return this
    }

    Switches withManufacturer(Manufacturer type) {
        switch (type) {
            case WB5164:
                switches = switches.findAll { it.isWb5164() }
                break
            case NOVIFLOW:
                switches = switches.findAll { it.isNoviflow() && !it.isWb5164() }
                break
            case CENTEC:
                switches = switches.findAll { it.isCentec() }
                break
            case OVS:
                switches = switches.findAll { it.isVirtual() }
                break
        }
        return this
    }

    List<SwitchExtended> unique() {
        switches.findAll().unique { it.getDescription() }
    }

    List<SwitchExtended> uniqueByHw() {
        switches.findAll().unique { it.hwSwString()}
    }

    Switches withS42Support(){
        def swsProps =  northboundV2.getAllSwitchProperties().switchProperties
        switches = switches.findAll { sw -> isS42Supported(swsProps.find { it.switchId == sw.switchId}) }
        return this
    }

    SwitchExtended random() {
        assumeFalse(switches.isEmpty(), "No suiting switch found")
        switches.shuffled().first()
    }

    SwitchExtended first() {
        assumeFalse(switches.isEmpty(), "No suiting switch found")
        switches.first()
    }

    SwitchExtended findSpecific(SwitchId switchId) {
        def sw = switches.find { it.switchId == switchId }
        assert sw, "There is no switch with specified switchId $switchId, active switches ${switches*.switchId}"
        sw
    }

    List<SwitchExtended> findSpecific(List<SwitchId> switchIds) {
        def switchesToFind = switchIds.unique()
        def desiredSwitches = switches.findAll { it.switchId in switchesToFind }
        assert desiredSwitches.size() == switchesToFind.size()
        desiredSwitches
    }

    /**
     * Find all switches that are involved in a regular flow path
     * @param simpleFlowPath is a path of regular flow to collect switchesIds
     * @return list of SwitchExtended objects for further manipulation
     */
    List<SwitchExtended> findSwitchesInPath(FlowEntityPath simpleFlowPath) {
        def switchesToFind = simpleFlowPath.getInvolvedSwitches()
        def desiredSwitches = switches.findAll { it.switchId in switchesToFind }
        assert desiredSwitches.size() == switchesToFind.size()
        desiredSwitches
    }

    @Memoized
    private List<SwitchExtended> collectSwitches() {
        List<SwitchDto> switchesDetails = northbound.allSwitches
        switches = topology.activeSwitches.collect {
            def sw = switchFactory.get(it)
            sw.setNbDetails(switchesDetails.find { sw.switchId == it.switchId })
            return sw
        }
        return switches
    }

    /**
     * Synchronizes each switch from the list and returns a list of SwitchSyncExtendedResult that includes data
     *  of synchronization if there were entries which had to be fixed.
     * I.e. if all the switches were in expected state, then an empty list is returned. If there were only
     * two switches in unexpected state, than the resulting list will have only two items, etc.
     * @param switchesToSynchronize SwitchIds which should be synchronized
     * @return List of SwitchSyncExtendedResults for switches which weren't in expected state before
     * the synchronization
     */
    static List<SwitchSyncExtendedResult> synchronizeAndCollectFixedDiscrepancies(List<SwitchExtended> switchesToSynchronize) {
        return withPool {
            switchesToSynchronize.collectParallel { it.synchronizeAndCollectFixedDiscrepancies() }
                    .findAll{ it.isPresent() }*.get()
        }
    }

    /**
     * validates each switch from the list and returns list of SwitchValidationV2ExtendedResult,
     * I.e. if all the switches were in expected state, then empty list is returned. If there were only
     * two switches in unexpected state, than resulting list will have only two items, etc.
     * @param switchesToSynchronize SwitchIds which should be synchronized
     * @return List of SwitchValidationV2ExtendedResult for switches which weren't in expected state before
     * the validation
     */
    static List<SwitchValidationV2ExtendedResult> validateAndCollectFoundDiscrepancies(List<SwitchExtended> switchesToSynchronize) {
        return withPool {
            switchesToSynchronize.collectParallel { it.validateAndCollectFoundDiscrepancies() }
                    .findAll{ it.isPresent() }*.get()
        }
    }
}
