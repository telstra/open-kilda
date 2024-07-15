package org.openkilda.functionaltests.model.cleanup


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.ConcurrentHashMap

import static groovyx.gpars.GParsExecutorsPool.withPool
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.CLEAN_LINK_DELAY
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_FLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_HAFLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_ISLS_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_LAG_LOGICAL_PORT
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_MIRROR
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_YFLOW
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.OTHER
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.PORT_UP
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISLS_COST
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_ISL_PARAMETERS
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESET_SWITCH_MAINTENANCE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_A_SWITCH_FLOWS
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_FEATURE_TOGGLE
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_ISL
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_KILDA_CONFIGURATION
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_PORT_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.RESTORE_SWITCH_PROPERTIES
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.REVIVE_SWITCH
import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.SYNCHRONIZE_SWITCH
import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST

/**
 * This class handles disposable objects (flows, switch settings, turned off ISLs, etc) and deletes them or restores
 * their state after test or test class run (when called in AutomaticCleanupListener)
 */
@Component
@Scope("specThread")
class CleanupManager {
    @Value("#{T(java.util.Collections).emptyMap()}")
    private ConcurrentHashMap<CleanupActionType, List<Closure>> postTestActions
    @Value("#{T(java.util.Collections).emptyMap()}")
    private ConcurrentHashMap<CleanupActionType, List<Closure>> postClassActions

    @PostConstruct
    void init() {
        CleanupActionType.values().each {
            postTestActions[it] = Collections.synchronizedList(new ArrayList())
            postClassActions[it] = Collections.synchronizedList(new ArrayList())
        }
    }

    /**
     * Adds any closure to the list to execute after test/test class. E.g., before shutting down the port switch,
     * one must add a closure that would turn the port on after test is over
     * IMPORTANT: order matters. First in - last called.
     * IMPORTANT2: all the closures must be 'safe', i.e. the ones must not cause exceptions and have more mechanisms to
     * achieve the result (restore the order in environment) than other methods
     * @param action groovy closure which restores some piece of environment.
     * @param cleanupAfter when to delete the flow: after test or after test class
     * @return nothing
     */
    void addAction(CleanupActionType actionType, Closure action, cleanupAfter = TEST) {
        if (cleanupAfter == TEST) {
            postTestActions[actionType].add(action)
        } else {
            postClassActions[actionType].add(action)
        }
    }

    /**
     * Runs cleanup. This method is directly called from AutomaticCleanupListener
     * IMPORTANT: order matters. Trying to run all the cleanup actions asynchronously can break en environment
     * @param cleanupAfter when to delete the flow: after test or after test class
     * @return nothing
     */
    void run(cleanupAfter = TEST) {
        def failedActions
        if (cleanupAfter == TEST) {
            failedActions = runActionsByType(postTestActions)
        } else {
            failedActions = runActionsByType(postClassActions)
        }
        if (failedActions) {
            throw new RuntimeException(failedActions.toString())
        }
    }

    private static List<Exception> runActionsByType(ConcurrentHashMap<CleanupActionType, List<Closure>> actions) {
        def exceptions = []
        exceptions += runActionsSynchronously(firstElementOrEmptyList(actions[RESTORE_KILDA_CONFIGURATION]))
        exceptions += runActionsSynchronously(firstElementOrEmptyList(actions[RESTORE_FEATURE_TOGGLE]))
        exceptions += runActionsSynchronously(actions[DELETE_MIRROR])
        exceptions += runActionsSynchronously(actions[DELETE_FLOW])
        exceptions += runActionsSynchronously(actions[DELETE_YFLOW])
        exceptions += runActionsAsynchronously(actions[REVIVE_SWITCH])
        exceptions += runActionsAsynchronously(actions[RESET_SWITCH_MAINTENANCE])
        /* We don't have tests that change several different toggles, so we can just roll the env back to the initial
        state (which is the first action stored) */
        exceptions += runActionsSynchronously(actions[RESTORE_SWITCH_PROPERTIES].reverse())
        exceptions += runActionsAsynchronously(actions[RESTORE_PORT_PROPERTIES].reverse())
        exceptions += runActionsSynchronously(actions[DELETE_LAG_LOGICAL_PORT].reverse())
        exceptions += runActionsSynchronously(actions[RESTORE_A_SWITCH_FLOWS].reverse())
        exceptions += runActionsAsynchronously(actions[PORT_UP])
        exceptions += runActionsAsynchronously(actions[RESTORE_ISL])
        exceptions += runActionsAsynchronously(actions[CLEAN_LINK_DELAY])
        if (actions[RESET_ISLS_COST]) {
            exceptions += runActionsSynchronously([actions[RESET_ISLS_COST].first()])
        }
        if (actions[DELETE_ISLS_PROPERTIES]) {
            exceptions += runActionsSynchronously([actions[DELETE_ISLS_PROPERTIES].first()])
        }
        exceptions += runActionsSynchronously(actions[DELETE_HAFLOW])
        exceptions += runActionsAsynchronously(actions[SYNCHRONIZE_SWITCH])
        exceptions += runActionsSynchronously(actions[RESET_ISL_PARAMETERS])
        exceptions += runActionsSynchronously(actions[OTHER])
        clearActionsList(actions)
        return exceptions
    }

    private static List<Exception> runActionsSynchronously(List<Closure> actions) {
        return actions.collect{
            try {
                it()
                return null
            } catch (Exception exception) {
                return exception
            }
        }.findAll {}
    }

    private static List<Exception> runActionsAsynchronously(List<Closure> actions) {
        return  withPool {
            actions.collectParallel {
                try {
                    it()
                    return null
                } catch (Exception exception) {
                    return exception
                }
            }
        }.findAll {}
    }

    private static def clearActionsList(ConcurrentHashMap<CleanupActionType, List<Closure>> actions) {
        CleanupActionType.values().each {
            actions[it].clear()
        }
    }

    private static List<Closure> firstElementOrEmptyList(List<Closure> list) {
        return list.isEmpty() ? list : [list.first()]
    }
}
