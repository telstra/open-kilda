package org.openkilda.performancetests.helpers

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.IslInfoData
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.northbound.dto.v1.links.LinkParametersDto
import org.openkilda.performancetests.model.CustomTopology
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.labservice.model.LabInstance
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component("performance")
class TopologyHelper extends org.openkilda.functionaltests.helpers.TopologyHelper {

    @Autowired
    NorthboundService northbound
    @Autowired
    LabService labService
    @Autowired
    IslUtils islUtils
    @Value('${discovery.timeout}')
    int discoveryTimeout
    
    @Value("#{'\${floodlight.regions}'.split(',')}")
    List<String> regions

    @Value("#{'\${floodlight.controllers.management}'.split(',')}")
    List<String> managementControllers

    @Value("#{'\${floodlight.controllers.stat}'.split(',')}")
    List<String> statControllers
    
    CustomTopology createRandomTopology(int switchesAmount, int islsAmount) {
        def topo = new CustomTopology()
        switchesAmount.times { i ->
            def region = i % regions.size()
            topo.addCasualSwitch("${managementControllers[region]} ${statControllers[region]}") 
        }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }

        def allSwitches = topo.getSwitches()
        def allLinks = topo.getIsls()
        for (def i = 0; i < allSwitches.size() - 1; i++) {
            if (!allLinks.find { it.srcSwitch == allSwitches[i] && it.dstSwitch == allSwitches[i+1] }) {
                topo.addIsl(allSwitches[i], allSwitches[i+1])
            }
        }

        topo.setControllers(managementControllers)
        labService.createLab(topo)
        Wrappers.wait(30 + switchesAmount * 3, 5) {
            verifyTopology(topo)
        }
        return topo
    }

    /**
     * Verify that current discovered topology matches the TopologyDefinition.
     */
    def verifyTopology(TopologyDefinition topo) {
        //Switches discovery
        def switches = northbound.getAllSwitches().findAll { it.switchId in topo.activeSwitches*.dpId }
        def activeSwitches = switches.findAll { it.state == SwitchChangeType.ACTIVATED }
        assert switches == activeSwitches
        assert switches.size() == topo.activeSwitches.size()

        //Isl discovery
        def isls = northbound.getAllLinks().findAll {
            it.source.switchId in topo.activeSwitches*.dpId ||
                    it.destination.switchId in topo.activeSwitches*.dpId
        }
        assert isls.size() == topo.isls.size() * 2
        //TODO(rtretiak): to verify actual src and dst of discovered ISLs
        isls.each {
            assert it.state == IslChangeType.DISCOVERED
            assert it.actualState == IslChangeType.DISCOVERED
        }
    }

    /**
     * Delete the lab. Wait until topology is failed and delete all ISLs and all switches related to given
     * topology definition.
     */
    def purgeTopology(TopologyDefinition topo, LabInstance lab = null) {
        lab ? labService.deleteLab(lab) : labService.deleteLab()
        List<IslInfoData> topoIsls
        Wrappers.wait(WAIT_OFFSET + discoveryTimeout) {
            topoIsls = northbound.getAllLinks().findAll {
                it.source.switchId in topo.activeSwitches*.dpId ||
                        it.destination.switchId in topo.activeSwitches*.dpId
            }.unique({ [it.source.switchId, it.source.portNo, it.destination.switchId, it.destination.portNo].sort() })
            topoIsls.each { assert it.state == IslChangeType.FAILED } || true
        }
        topoIsls.each {
            northbound.deleteLink(islUtils.toLinkParameters(it))
        }
        def topoSwitches = northbound.getAllSwitches().findAll { it.switchId in topo.switches*.dpId }
        topoSwitches.each {
            northbound.deleteSwitch(it.switchId, false)
        }
    }
}
