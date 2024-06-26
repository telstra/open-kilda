package org.openkilda.functionaltests.spec.links

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.error.link.LinkBfdNotSetExpectedError
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.messaging.info.event.IslChangeType
import org.openkilda.model.SwitchFeature
import org.openkilda.northbound.dto.v2.links.BfdProperties
import org.springframework.web.client.HttpClientErrorException
import spock.lang.Narrative
import spock.lang.ResourceLock
import spock.lang.See
import spock.lang.Shared

import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.openkilda.functionaltests.ResourceLockConstants.BFD_TOGGLE
import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.ISL_RECOVER_ON_FAIL
import static org.openkilda.functionaltests.extension.tags.Tag.LOCKKEEPER
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.Constants.WAIT_OFFSET

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/network-discovery")
@Narrative("""BFD stands for Bidirectional Forwarding Detection. For now tested only on Noviflow switches. 
Main purpose is to detect ISL failure on switch level, which is times faster than a regular 
controller-involved discovery mechanism""")
@Tags([HARDWARE])
class BfdSpec extends HealthCheckSpecification {

    @Shared
    BfdProperties defaultBfdProps = new BfdProperties(350, (short)3)

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "Able to create a valid BFD session between two Noviflow switches"() {
        given: "An a-switch ISL between two Noviflow switches with BFD and RTL"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort &&
                it.srcSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD) &&
                it.dstSwitch.features.contains(SwitchFeature.NOVIFLOW_COPY_FIELD)
        }
        assumeTrue(isl as boolean,
"The test requires at least one a-switch BFD and RTL ISL between Noviflow switches")

        when: "Create a BFD session on the ISL without props"
        def setBfdResponse = islHelper.setLinkBfd(isl)

        then: "Response reflects the requested bfd session with default prop values"
        setBfdResponse.properties == defaultBfdProps

        and: "Link reflects that bfd is up"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
            }
        }

        and: "Get link bfd API shows bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(northboundV2.getLinkBfd(isl)) {
                properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        def costBeforeFailure = islUtils.getIslInfo(isl).get().cost
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "ISL immediately gets failed because bfd has higher priority than RTL"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    state == IslChangeType.FAILED
                    bfdSessionStatus == "down"
                }
            }
        }

        and: "Cost of ISL is unchanged and round trip latency status is ACTIVE"
        [isl, isl.reversed].each {
            verifyAll(northbound.getLink(it)) {
                cost == costBeforeFailure
                roundTripStatus == IslChangeType.DISCOVERED
            }
        }

        when: "Restore connection"
        aSwitchFlows.addFlows([isl.aswitch])

        then: "ISL is rediscovered and bfd status is 'up'"
        Wrappers.wait(discoveryAuxiliaryInterval + WAIT_OFFSET) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    state == IslChangeType.DISCOVERED
                    bfdSessionStatus == "up"
                }
            }
        }

        when: "Remove existing BFD session"
        northboundV2.deleteLinkBfd(isl)
        def bfdRemoved = true

        then: "Bfd field is removed from isl"
        [isl, isl.reversed].each {
            verifyAll(northbound.getLink(it)) {
                !enableBfd
                bfdSessionStatus == null
            }
        }

        and: "Get BFD API shows '0' values in props"
        [isl, isl.reversed].each {
            verifyAll(northboundV2.getLinkBfd(isl)) {
                properties == BfdProperties.DISABLED
                effectiveSource.properties == BfdProperties.DISABLED
                effectiveDestination.properties == BfdProperties.DISABLED
            }
        }

        when: "Interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "ISL fails ONLY after discovery timeout"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }
    }

    @ResourceLock(BFD_TOGGLE)
    def "Reacting on BFD events can be turned on/off by a feature toggle"() {
        given: "An a-switch ISL between two Noviflow switches with BFD enabled"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue(isl as boolean, "Require at least one a-switch BFD ISL between Noviflow switches")
        islHelper.setLinkBfd(isl)
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(northboundV2.getLinkBfd(isl)) {
                properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Set BFD toggle to 'off' state"
        featureToggles.useBfdForIslIntegrityCheck(false)

        and: "Interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "ISL does not get FAILED immediately"
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        and: "ISL fails after discovery timeout"
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        when: "Set BFD toggle back to 'on' state and restore the ISL"
        aSwitchFlows.addFlows([isl.aswitch])
        featureToggles.useBfdForIslIntegrityCheck(true)
        Wrappers.wait(discoveryAuxiliaryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }

        and: "Again interrupt ISL connection by breaking rule on a-switch"
        aSwitchFlows.removeFlows([isl.aswitch])

        then: "ISL immediately gets failed"
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }
    }

    @Tags(ISL_RECOVER_ON_FAIL)
    def "Deleting a failed BFD link also removes the BFD session from it"() {
        given: "An inactive a-switch link with BFD session"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort }
        assumeTrue(isl as boolean, "Require at least one a-switch BFD ISL between Noviflow switches")
        antiflap.portDown(isl.srcSwitch.dpId, isl.srcPort)
        TimeUnit.SECONDS.sleep(2) //receive any in-progress disco packets
        islHelper.setLinkBfd(isl)
        Wrappers.wait(WAIT_OFFSET) {
            assert northbound.getLink(isl).actualState == IslChangeType.FAILED
        }

        when: "Delete the link"
        northbound.deleteLink(islUtils.toLinkParameters(isl))
        !islUtils.getIslInfo(isl)
        !islUtils.getIslInfo(isl.reversed)

        and: "Discover the removed link again"
        antiflap.portUp(isl.srcSwitch.dpId, isl.srcPort)
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
        def isLinkUp = true

        then: "Discovered link shows no bfd session"
        !northbound.getLink(isl).enableBfd
        !northbound.getLink(isl.reversed).enableBfd
        def isBfdDisabled = true

        and: "Acts like there is no BFD session (fails only after discovery timeout)"
        aSwitchFlows.removeFlows([isl.aswitch])
        Wrappers.timedLoop(discoveryTimeout * 0.8) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
        Wrappers.wait(discoveryTimeout * 0.2 + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }
    }

    @Tags([SMOKE_SWITCHES, LOCKKEEPER])
    def "System is able to rediscover failed link after deleting BFD session"() {
        given: "An interrupted a-switch ISL with BFD session"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow &&
                it.aswitch?.inPort && it.aswitch?.outPort
        }
        assumeTrue(isl as boolean, "The test requires at least one a-switch BFD ISL")
        islHelper.setLinkBfd(isl)
        aSwitchFlows.removeFlows([isl.aswitch])
        def isAswitchRuleDeleted = true
        Wrappers.wait(WAIT_OFFSET / 2) {
            assert northbound.getLink(isl).state == IslChangeType.FAILED
            assert northbound.getLink(isl.reversed).state == IslChangeType.FAILED
        }

        when: "Remove existing BFD session"
        northboundV2.deleteLinkBfd(isl)

        and: "Restore connection"
        lockKeeper.addFlows([isl.aswitch])
        isAswitchRuleDeleted = false

        then: "ISL is rediscovered"
        Wrappers.wait(discoveryInterval + WAIT_OFFSET) {
            assert northbound.getLink(isl).state == IslChangeType.DISCOVERED
            assert northbound.getLink(isl.reversed).state == IslChangeType.DISCOVERED
        }
    }

    @Tags([HARDWARE])
    def "Able to create/update BFD session with custom properties"() {
        given: "An ISL between two Noviflow switches"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow }
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Create bfd session with custom properties"
        def bfdProps = new BfdProperties(100, (short)1)
        islHelper.setLinkBfd(isl, bfdProps)

        then: "'get ISL' and 'get BFD' api reflect the changes"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
                verifyAll(northboundV2.getLinkBfd(it)) {
                    properties == bfdProps
                    effectiveSource.properties == bfdProps
                    effectiveDestination.properties == bfdProps
                }
            }
        }

        when: "Update bfd session with custom properties"
        def updatedBfdProps = new BfdProperties(500, (short)5)
        northboundV2.setLinkBfd(isl, updatedBfdProps)

        then: "'get ISL' and 'get BFD' api reflect the changes"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
                verifyAll(northboundV2.getLinkBfd(it)) {
                    properties == updatedBfdProps
                    effectiveSource.properties == updatedBfdProps
                    effectiveDestination.properties == updatedBfdProps
                }
            }
        }
    }

    def "Unable to create bfd with #data.descr"() {
        given: "An ISL between two Noviflow switches"
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow }
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Try enabling bfd with forbidden properties"
        islHelper.setLinkBfd(isl, data.props)

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
        def isl = topology.islsForActiveSwitches.find { it.srcSwitch.noviflow && it.dstSwitch.noviflow }
        assumeTrue(isl as boolean, "The test requires at least one BFD ISL")

        when: "Create a BFD session using v1 API"
        def setBfdResponse = islHelper.setLinkBfdFromApiV1(isl, true)

        then: "Response reports successful installation of the session"
//        setBfdResponse.size() == 2
        setBfdResponse.each {
            assert it.enableBfd
        }

        and: "Link reflects that bfd is up"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    enableBfd
                    bfdSessionStatus == "up"
                }
            }
        }

        and: "Get link bfd API shows default bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(northboundV2.getLinkBfd(isl)) {
                properties == defaultBfdProps
                effectiveSource.properties == defaultBfdProps
                effectiveDestination.properties == defaultBfdProps
            }
        }

        when: "Disable bfd using v1 API"
        def disableBfdResponse = northbound.setLinkBfd(islUtils.toLinkEnableBfd(isl, false))

        then: "Response reports successful de-installation of the session"
//        disableBfdResponse.size() == 2
        disableBfdResponse.each {
            assert !it.enableBfd
        }

        and: "Link reflects that bfd is removed"
        Wrappers.wait(WAIT_OFFSET / 2) {
            [isl, isl.reversed].each {
                verifyAll(northbound.getLink(it)) {
                    !enableBfd
                    bfdSessionStatus == null
                }
            }
        }

        and: "Get link bfd API shows 'zero' bfd props for link src/dst"
        Wrappers.wait(WAIT_OFFSET / 2) {
            verifyAll(northboundV2.getLinkBfd(isl)) {
                properties == BfdProperties.DISABLED
                effectiveSource.properties == BfdProperties.DISABLED
                effectiveDestination.properties == BfdProperties.DISABLED
            }
        }
    }
}
