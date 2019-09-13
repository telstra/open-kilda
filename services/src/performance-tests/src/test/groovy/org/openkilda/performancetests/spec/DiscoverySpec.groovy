package org.openkilda.performancetests.spec

import static org.hamcrest.CoreMatchers.equalTo
import static org.openkilda.testing.service.lockkeeper.LockKeeperVirtualImpl.DUMMY_CONTROLLER

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.performancetests.BaseSpecification
import org.openkilda.performancetests.model.CustomTopology

import groovy.util.logging.Slf4j
import org.junit.Assume

@Slf4j
class DiscoverySpec extends BaseSpecification {

    def "System is able to discover a huge topology at once"() {
        Assume.assumeThat(preset.debug, equalTo(debug))
        def islsAmount = preset.switchesAmount * 2

        setup: "Prepare potential topology"
        def topo = new CustomTopology()
        preset.switchesAmount.times { topo.addCasualSwitch("${managementControllers[0]} ${statControllers[0]}") }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }
        topo.setControllers(managementControllers)

        when: "Create the topology"
        def lab = labService.createLab(topo)

        then: "Topology is discovered in reasonable time"
        Wrappers.wait(preset.switchesAmount * 3, 5) {
            topoHelper.verifyTopology(topo)
        }

        cleanup: "purge topology"
        topoHelper.purgeTopology(topo, lab)

        where:
        preset << [
                //around 55 switches for local 32GB setup and ~110 switches for stage
                [
                        debug         : true,
                        switchesAmount: 30
                ],
                [
                        debug         : false,
                        switchesAmount: 60
                ]
        ]
    }

    /**
     * Push the system to its limits until it fails to discover new isls or switches. Measure system's capabilities
     */
    def "System is able to continuously discover new switches and ISLs"() {
        Assume.assumeThat(preset.debug, equalTo(debug))

        //unattainable amount that system won't be able to handle for sure
        def switchesAmount = preset.minimumSwitchesRequirement * 1.5
        def islsAmount = switchesAmount * 3
        def allowedDiscoveryTime = 60 //seconds

        setup: "Create topology not connected to controller"
        def topo = new CustomTopology()
        switchesAmount.times { topo.addCasualSwitch(DUMMY_CONTROLLER) }
        islsAmount.times {
            def src = topo.pickRandomSwitch()
            def dst = topo.pickRandomSwitch([src])
            topo.addIsl(src, dst)
        }
        topo.setControllers(managementControllers)
        def lab = labService.createLab(topo)
        sleep(5000) //TODO(rtretiak): make createLab request to be synchronous

        when: "Start connecting switches to Kilda controller one by one"
        def switchesCreated = 0
        topo.switches.eachWithIndex { sw, i ->
            log.debug("Adding sw #${switchesCreated + 1} with id $sw.dpId")
            def controller = managementControllers[i % regions.size()] //split load between all regions
            lockKeeper.setController(sw, controller)
            Wrappers.wait(allowedDiscoveryTime) {
                assert northbound.getSwitch(sw.dpId).state == SwitchChangeType.ACTIVATED
                Wrappers.timedLoop(3) { //verify that system remains stable for some time
                    assert northbound.getAllSwitches().findAll {
                        it.switchId in topo.switches*.dpId && it.state != SwitchChangeType.ACTIVATED
                    }.empty
                    assert northbound.getAllLinks().findAll { it.state != IslChangeType.DISCOVERED }.empty
                    sleep(200)
                }
            }
            switchesCreated++
        }

        then: "Amount of discovered switches within allowed time is acceptable"
        def waitFailure = thrown(WaitTimeoutException)
        log.info("Performance report: Kilda was able to discover $switchesCreated switches.\nFailed with $waitFailure")
        switchesCreated > preset.minimumSwitchesRequirement

        cleanup: "purge topology"
        topoHelper.purgeTopology(topo, lab)

        where:
        preset << [
                //around 55 switches for local 32GB setup and ~110 switches for stage
                [
                        debug                     : true,
                        minimumSwitchesRequirement: 50,
                        allowedDiscoveryTime      : 60
                ],
                [
                        debug                     : false,
                        minimumSwitchesRequirement: 100,
                        allowedDiscoveryTime      : 60
                ]
        ]
    }
}
