package org.openkilda.performancetests.helpers

import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.performancetests.model.CustomTopology
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.service.labservice.LabService
import org.openkilda.testing.service.labservice.model.LabInstance
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.tools.IslUtils

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException

import java.util.concurrent.TimeUnit

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

    @Value("#{'\${floodlight.controllers.management.openflow}'.split(',')}")
    List<String> managementControllers

    @Value("#{'\${floodlight.controllers.stat.openflow}'.split(',')}")
    List<String> statControllers

    CustomTopology createRandomTopology(int switchesAmount, int islsAmount) {
        if (islsAmount < (switchesAmount - 1)) {
            throw new RuntimeException("Not enough ISLs were requested. islsAmount should be >= (switchesAmount - 1)")
        }
        def topo = new CustomTopology()
        switchesAmount.times { i ->
            def region = i % regions.take(2).size() //TODO(rtretiak): to remove 'take' when stage env deals with '3' region
            topo.addCasualSwitch("${managementControllers[region]} ${statControllers[region]}")
        }
        //create links between neighbor switches
        def allSwitches = topo.getSwitches()
        for (def i = 0; i < allSwitches.size() - 1; i++) {
            topo.addIsl(allSwitches[i], allSwitches[i + 1])
        }
        //create links between random switches
        (islsAmount - topo.getIsls().size()).times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
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
        Wrappers.timedLoop(10) { //system should remain stable for 10s
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
            sleep(500)
        }
    }

    /**
     * Delete the lab. Wait until topology is failed and delete all ISLs and all switches related to given
     * topology definition.
     */
    def purgeTopology(TopologyDefinition topo, LabInstance lab = null) {
        if (lab) {
            labService.deleteLab(lab)
        } else {
            labService.flushLabs()
        }
        TimeUnit.SECONDS.sleep(discoveryTimeout + 3)
        northbound.getAllLinks().findAll { it.state == IslChangeType.FAILED }.each {
            try {
                northbound.deleteLink(islUtils.toLinkParameters(it))
            } catch (HttpClientErrorException e) {
                if (e.statusCode == HttpStatus.NOT_FOUND) {
                    //fine, it's not present, do nothing
                } else {
                    throw e
                }
            }
        }
        def topoSwitches = northbound.getAllSwitches().findAll { it.state == SwitchChangeType.DEACTIVATED }
        topoSwitches.each {
            northbound.deleteSwitch(it.switchId, false)
        }
    }

    def purgeTopology() {
        purgeTopology(readCurrentTopology())
    }
}
