package org.openkilda.functionaltests.spec.links

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.BFD_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.functionaltests.model.switches.Manufacturer.NOVIFLOW
import static org.openkilda.messaging.info.event.IslChangeType.DISCOVERED
import static org.openkilda.messaging.info.event.IslChangeType.FAILED
import static org.openkilda.testing.Constants.WAIT_OFFSET

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.link.LinkBfdNotSetExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.model.SwitchExtended
import org.openkilda.northbound.dto.v2.links.BfdProperties

import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.ResourceLock
import spock.lang.See
import spock.lang.Shared

import java.util.concurrent.TimeUnit

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery")
@Narrative("""BFD stands for Bidirectional Forwarding Detection. For now tested only on Noviflow switches. 
Main purpose is to detect ISL failure on switch level, which is times faster than a regular 
controller-involved discovery mechanism""")
@Tags([HARDWARE])
class BfdSpec extends HealthCheckSpecification {

    @Shared
    BfdProperties defaultBfdProps = new BfdProperties(350, (short)3)

    @Shared
    List<SwitchExtended> noviflowSws

    def setupSpec() {
        noviflowSws = switches.all().withManufacturer(NOVIFLOW).getListOfSwitches()
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "Able to create a valid BFD session between two Noviflow switches"() {
        given: "An a-switch ISL between two Noviflow switches with BFD and RTL"
        def switchesWithRtl = noviflowSws.findAll { it.isRtlSupported() }
        def isl = isls.all().betweenSwitches(switchesWithRtl).withASwitch().random()
        assumeTrue(isl as boolean, "The test requires at least one a-switch BFD and RTL ISL between Noviflow switches")

        when: "Create a BFD session on the ISL without props"
        def setBfdResponse = isl.setBfd()

        then: "Response reflects the requested bfd session with default prop values"
        setBfdResponse.properties == defaultBfdProps

        and: "Link reflects that bfd is up"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()){
                    enableBfd
                    bfdSessionStatus == "up"
                }
            }
        }

        and: "Get link bfd API shows bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(isl.getBfdDetails()) {
                it.properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        def costBeforeFailure = isl.getNbDetails().cost
        aSwitchFlows.removeFlows([isl.getASwitch()])

        then: "ISL immediately gets failed because bfd has higher priority than RTL"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()){
                    state == FAILED
                    bfdSessionStatus == "down"
                }
            }
        }

        and: "Cost of ISL is unchanged and round trip latency status is ACTIVE"
        [isl, isl.reversed].each {
            verifyAll(it.getNbDetails()){
                cost == costBeforeFailure
                roundTripStatus == DISCOVERED
            }
        }

        when: "Restore connection"
        aSwitchFlows.addFlows([isl.getASwitch()])

        then: "ISL is rediscovered and bfd status is 'up'"
        Wrappers.wait(discoveryAuxiliaryInterval + WAIT_OFFSET) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    state == DISCOVERED
                    bfdSessionStatus == "up"
                }
            }
        }

        when: "Remove existing BFD session"
        isl.deleteBfd()

        then: "Bfd field is removed from isl"
        [isl, isl.reversed].each {
            verifyAll(it.getNbDetails()) {
                !enableBfd
                bfdSessionStatus == null
            }
        }

        and: "Get BFD API shows '0' values in props"
        [isl, isl.reversed].each {
            verifyAll(it.getBfdDetails()) {
                it.properties == BfdProperties.DISABLED
                effectiveSource.properties == BfdProperties.DISABLED
                effectiveDestination.properties == BfdProperties.DISABLED
            }
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.getASwitch()])

        then: "ISL fails ONLY after discovery timeout"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert isl.getNbDetails().state == DISCOVERED
            assert isl.reversed.getNbDetails().state == DISCOVERED
        }
        isl.waitForStatus(FAILED, discoveryTimeout * 0.2 + WAIT_OFFSET)
    }

    @ResourceLock(BFD_TOGGLE)
    def "Reacting on BFD events can be turned on/off by a feature toggle"() {
        given: "An a-switch ISL between two Noviflow switches with BFD enabled"
        def isl = isls.all().betweenSwitches(noviflowSws).withASwitch().random()
        assumeTrue(isl as boolean, "Require at least one a-switch BFD ISL between Noviflow switches")
        isl.setBfd()

        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(isl.getBfdDetails()) {
                it.properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Set BFD toggle to 'off' state"
        featureToggles.useBfdForIslIntegrityCheck(false)

        and: "Interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.getASwitch()])

        then: "ISL does not get FAILED immediately"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert isl.getNbDetails().state == DISCOVERED
            assert isl.reversed.getNbDetails().state == DISCOVERED
        }

        and: "ISL fails after discovery timeout"
        isl.waitForStatus(FAILED, discoveryTimeout * 0.2 + WAIT_OFFSET)

        when: "Set BFD toggle back to 'on' state and restore the ISL"
        aSwitchFlows.addFlows([isl.getASwitch()])
        featureToggles.useBfdForIslIntegrityCheck(true)
        isl.waitForStatus(DISCOVERED, discoveryAuxiliaryInterval + WAIT_OFFSET)

        and: "Again interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.getASwitch()])

        then: "ISL immediately gets failed"
        isl.waitForStatus(FAILED, WAIT_OFFSET / 2)
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Deleting a failed BFD link also removes the BFD session from it"() {
        given: "An inactive a-switch link with BFD session"
        def isl = isls.all().betweenSwitches(noviflowSws).withASwitch().random()
        assumeTrue(isl as boolean, "Require at least one a-switch BFD ISL between Noviflow switches")
        isl.srcEndpoint.down()
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        isl.setBfd()
        Wrappers.wait(WAIT_OFFSET) {
            assert isl.getNbDetails().actualState == FAILED
        }

        when: "Delete the link"
        isl.delete()
        !isl.isPresent()

        and: "Discover the removed link again"
        isl.srcEndpoint.up()
        isl.waitForStatus(DISCOVERED, discoveryInterval + WAIT_OFFSET)

        then: "Discovered link shows no bfd session"
        !isl.getNbDetails().enableBfd
        !isl.reversed.getNbDetails().enableBfd

        and: "Acts like there is no BFD session (fails only after discovery timeout)"
        aSwitchFlows.removeFlows([isl.getASwitch()])
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert isl.getNbDetails().state == DISCOVERED
            assert isl.reversed.getNbDetails().state == DISCOVERED
        }

        isl.waitForStatus(FAILED, discoveryTimeout * 0.2 + WAIT_OFFSET)
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "System is able to rediscover failed link after deleting BFD session"() {
        given: "An interrupted a-switch ISL with BFD session"
        def isl = isls.all().betweenSwitches(noviflowSws).withASwitch().random()
        assumeTrue(isl as boolean, "The test requires at least one a-switch BFD ISL")
        isl.setBfd()
        aSwitchFlows.removeFlows([isl.getASwitch()])
        isl.waitForStatus(FAILED, WAIT_OFFSET / 2)

        when: "Remove existing BFD session"
        isl.deleteBfd()

        and: "Restore connection"
        aSwitchFlows.addFlows([isl.getASwitch()])

        then: "ISL is rediscovered"
        isl.waitForStatus(DISCOVERED, discoveryInterval + WAIT_OFFSET)
    }

    @Tags([HARDWARE])
    def "Able to create/update BFD session with custom properties"() {
        given: "An ISL between two Noviflow switches"
        def isl = isls.all().betweenSwitches(noviflowSws).random()
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Create bfd session with custom properties"
        def bfdProps = new BfdProperties(100, (short)1)
        isl.setBfd(bfdProps)

        then: "'get ISL' and 'get BFD' api reflect the changes"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
                verifyAll(it.getBfdDetails()) {
                    it.properties == bfdProps
                    effectiveSource.properties == bfdProps
                    effectiveDestination.properties == bfdProps
                }
            }
        }

        when: "Update bfd session with custom properties"
        def updatedBfdProps = new BfdProperties(500, (short)5)
        isl.setBfd(updatedBfdProps)

        then: "'get ISL' and 'get BFD' api reflect the changes"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
                verifyAll(it.getBfdDetails()) {
                    it.properties == updatedBfdProps
                    effectiveSource.properties == updatedBfdProps
                    effectiveDestination.properties == updatedBfdProps
                }
            }
        }
    }

    def "Unable to create bfd with #data.descr"() {
        given: "An ISL between two Noviflow switches"
        def isl = isls.all().betweenSwitches(noviflowSws).random()
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Try enabling bfd with forbidden properties"
        isl.setBfd(data.props)

        then: "Error is returned"
        def e = thrown(HttpClientErrorException)
        new LinkBfdNotSetExpectedError(data.expectedDescription).matches(e)

        where:
        data << [
                [
                        descr: "too small interval",
                        props: new BfdProperties(99, (short)1),
                        expectedDescription: ~/Errors: Invalid BFD interval value: 99 < 100/
                ],
                [
                        descr: "too small multiplier",
                        props: new BfdProperties(100, (short)0),
                        expectedDescription: ~/Errors: Invalid BFD multiplier value: 0 < 1/
                ]
        ]
    }

    def "Able to CRUD BFD sessions using v1 API"() {
        given: "An ISL between two Noviflow switches"
        def isl = isls.all().betweenSwitches(noviflowSws).random()
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Create a BFD session using v1 API"
        def setBfdResponse = isl.setBfdFromApiV1(true)

        then: "Response reports successful installation of the session"
//        setBfdResponse.size() == 2
        setBfdResponse.each {
            assert it.enableBfd
        }

        and: "Link reflects that bfd is up"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
            }
        }

        and: "Get link bfd API shows default bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(isl.getBfdDetails()) {
                it.properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Disable bfd using v1 API"
        def disableBfdResponse = isl.setBfdFromApiV1(false)

        then: "Response reports successful de-installation of the session"
//        disableBfdResponse.size() == 2
        disableBfdResponse.each {
            assert !it.enableBfd
        }

        and: "Link reflects that bfd is removed"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(it.getNbDetails()) {
                    !enableBfd
                    bfdSessionStatus == null
                }
            }
        }

        and: "Get link bfd API shows 'zero' bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(isl.getBfdDetails()) {
                it.properties == BfdProperties.DISABLED
                effectiveSource.properties == BfdProperties.DISABLED
                effectiveDestination.properties == BfdProperties.DISABLED
            }
        }
    }
}
