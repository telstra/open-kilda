package org.openkilda.functionaltests.helpers.model

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.junit.jupiter.api.Assumptions.assumeFalse
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISL_PARAMETERS
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.factory.IslFactory
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.functionaltests.model.stats.Direction
import org.openkilda.northbound.dto.v1.links.LinkPropsDto
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Isl
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.transform.EqualsAndHashCode
import groovy.transform.Memoized
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Slf4j
@Component
@Scope(SCOPE_PROTOTYPE)
@ToString(includeNames = true, excludes = 'topology, northbound, northboundV2, islFactory, database, cleanupManager')
@EqualsAndHashCode(excludes = 'topology, northbound, northboundV2, islFactory, database, cleanupManager')
class Isls {

    @Autowired
    TopologyDefinition topology
    @Autowired
    @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    @Qualifier("islandNbV2")
    NorthboundServiceV2 northboundV2
    @Autowired
    IslFactory islFactory
    @Autowired
    Database database
    @Autowired
    CleanupManager cleanupManager

    private List<IslExtended> isls

    Isls all() {
        isls = collectIsls()
        return this
    }

    Isls allNotConnected() {
        isls = collectNotConnectedIsls()
        return this
    }

    @Memoized
    private List<IslExtended> collectIsls() {
        topology.getIslsForActiveSwitches().collect { islFactory.get(it) }
    }

    @Memoized
    private List<IslExtended> collectNotConnectedIsls() {
        topology.getNotConnectedIsls().collect { islFactory.get(it) }
    }

    List<IslExtended> getListOfIsls() {
        isls.findAll()
    }

    /***
     * a-switch is an additional switch in the middle of ISL, it is used to simulate ISL breaking-event
     * @return Isls object with all Isls that have a-switch
     */
    Isls withASwitch() {
        isls = isls.findAll { it.hasASwitch() }
        return this
    }

    /***
     *
     * @return Isls object with regular ISLs without an additional a-switch in the middle
     */
    Isls withoutASwitch() {
        isls = isls.findAll { !it.hasASwitch() }
        return this
    }

    /***
     *
     * @param switches
     * @return all direct ISLs between specified switches
     */
    Isls betweenSwitches(List<SwitchExtended> switches) {
        isls = isls.findAll { switches*.switchId.containsAll(it.involvedSwIds) }
        return this
    }

    /***
     *
     * @param swPair
     * @return all direct ISLs between specified switchPair
     */
    Isls betweenSwitchPair(SwitchPair swPair) {
        def foundIsls = []
        isls.each {
            it.srcSwId == swPair.src.switchId && it.dstSwId == swPair.dst.switchId && foundIsls.add(it)
            it.srcSwId == swPair.dst.switchId && it.dstSwId == swPair.src.switchId && foundIsls.add(it.reversed)
        }
        isls = foundIsls
        return this
    }

    /***
     *
     * @param switches
     * @return all ISLs whose source is specified switch
     */
    Isls relatedTo(SwitchExtended sw) {
        def foundIsls = []
        isls.each {
            it.srcSwId == sw.switchId && foundIsls.add(it)
            it.dstSwId == sw.switchId && foundIsls.add(it.reversed)
        }
        isls = foundIsls
        return this
    }

    /***
     *
     * @param switchPair
     * @return all ISLs whose source or destination is from switchPair switches
     */
    Isls relatedTo(SwitchPair switchPair) {
        isls = isls.findAll { !it.involvedSwIds.intersect(switchPair.toList().switchId).isEmpty() }
        return this
    }

    Isls notRelatedTo(List<SwitchExtended> switches) {
        isls = isls.findAll { it.involvedSwIds.intersect(switches.switchId).isEmpty() }
        return this
    }

    Isls notRelatedTo(SwitchPair switchPair) {
        notRelatedTo(switchPair.toList())
    }

    Isls notRelatedTo(SwitchExtended sw) {
        isls = isls.findAll { sw.switchId !in it.involvedSwIds }
        return this
    }

    Isls excludeIsls(List<IslExtended> islsToAvoid) {
        def allIslsToAvoid = collectIsls().collectMany {
            //collect forward and reverse isls to exclude from the current isls list
            islsToAvoid.intersect([it, it.reversed]).isEmpty() ? [] : [it, it.reversed]
        }
        isls = isls.findAll { it !in allIslsToAvoid }
        return this
    }

    Isls collectIslsFromPaths(List<Path> paths) {
        def allIsls = paths.collectMany { it.getInvolvedIsls() }.unique()
        isls = isls.findAll { !allIsls.intersect([it.isl, it.reversed.isl]).isEmpty() }
        return this
    }


    List<IslExtended> findInPath(FlowEntityPath flowPath, Direction direction = Direction.FORWARD) {
        findIsls(flowPath.getInvolvedIsls(direction))
    }

    List<IslExtended> findInPath(FlowWithSubFlowsEntityPath complexFlowPath, Direction direction = Direction.FORWARD) {
        findIsls(complexFlowPath.getInvolvedIsls(direction))
    }

    List<IslExtended> findInPath(Path path) {
        findIsls(path.getInvolvedIsls())
    }

    private findIsls(List<Isl> islList) {
        //both forward and reverse ISLs are required to collect flow-related ISLs
        def allIsls = isls.collectMany { [it, it.reversed] }
        def foundIsls = islList.collect { flowIsl ->
            allIsls.find { it.isl == flowIsl }
        }
        assert islList.size() == foundIsls.size()
        return foundIsls
    }

    IslExtended random() {
        assumeFalse(isls.isEmpty(), "No suiting ISL found")
        return isls.get(new Random().nextInt(isls.size()))
    }

    IslExtended first() {
        assumeFalse(isls.isEmpty(), "No suiting ISL found")
        return isls.first()
    }

    void deleteAllProps() {
        northbound.deleteLinkProps(northbound.getLinkProps(isls*.isl))
    }

    List<LinkPropsDto> getProps() {
        northbound.getLinkProps(isls*.isl)
    }

    List<IslExtended> updateCost(long newCost) {
        cleanupManager.addAction(DELETE_ISLS_PROPERTIES, { northbound.deleteLinkProps(northbound.getLinkProps(topology.isls)) })
        cleanupManager.addAction(RESET_ISLS_COST, { database.resetCosts(topology.isls) })
        northbound.updateLinkProps(isls.collectMany { [it.toLinkProps(["cost": newCost.toString()])] })
        return isls
    }

    IslExtended makePathIslsMorePreferable(List<IslExtended> morePreferablePathIsls,
                                           List<IslExtended> lessPreferablePathIsls) {
        def allLinksFromNb = northbound.getAllLinks()
        def totalCostOfMorePrefPath = morePreferablePathIsls.sum { it.getInfo(allLinksFromNb).cost ?: 700 }
        def totalCostOfLessPrefPath = lessPreferablePathIsls.sum { it.getInfo(allLinksFromNb).cost ?: 700 }
        def difference = totalCostOfMorePrefPath - totalCostOfLessPrefPath
        def islToAvoid
        if (difference >= 0) {
            assert isls.size() == topology.islsForActiveSwitches.size()
            def allMorePreferableIsls = isls.collectMany {
                //collect forward and reverse isls of preferable path
               morePreferablePathIsls.intersect([it, it.reversed]).isEmpty() ? [] : [it, it.reversed]
            }
            islToAvoid = lessPreferablePathIsls.find { it !in allMorePreferableIsls }
            if (!islToAvoid) {
                //this should be impossible
                throw new Exception("Unable to make some path more preferable because both paths use same ISLs")
            }
            log.debug "ISL to avoid: $islToAvoid"
            Integer newCost = islToAvoid.getInfo(allLinksFromNb).cost + difference + 1
            islToAvoid.updateCost(newCost)
        }
        return islToAvoid
    }

    Isls updateIslsAvailableBandwidth(long value) {
        def islToUpdate = isls.collect { it.isl }
        cleanupManager.addAction(RESET_ISL_PARAMETERS, { database.resetIslsBandwidth(islToUpdate) })
        database.updateIslsAvailableBandwidth(islToUpdate, value)
        return this
    }

    Isls resetCostsInDb() {
        database.resetCosts(isls.isl)
        return this
    }

    /***
     *
     * @param availableValue
     * @param maxValue
     * @return list of IslExtended that were updated
     */
    Isls updateIslsAvailableAndMaxBandwidthInDb(long availableValue, long maxValue = availableValue) {
        def islToUpdate = isls.collect{ it.isl }
        cleanupManager.addAction(RESET_ISL_PARAMETERS, { database.resetIslsBandwidth(islToUpdate)})
        database.updateIslsAvailableAndMaxBandwidth(islToUpdate, availableValue, maxValue)
        return this
    }

    /***
     *
     * @param value
     * @return list of IslExtended that were updated
     */
    List<IslExtended> updateIslsCostInDb(int value) {
        cleanupManager.addAction(RESET_ISLS_COST, { database.resetCosts(topology.isls) })
        //both forward and reverse isls are updated in DB
        database.updateIslsCosts(isls*.isl, value)
        return isls.collectMany {[it, it.reversed]}
    }

    static void breakIsls(List<IslExtended> islsToBreak, CleanupAfter cleanupAfter = TEST) {
        withPool {
            (islsToBreak as Set).eachParallel {
                it.breakIt(cleanupAfter)
            }
        }
    }

    static void restoreIsls(List<IslExtended> islsToRestore) {
        withPool {
            (islsToRestore as Set).eachParallel {
                it.restore()
            }
        }
    }
}
