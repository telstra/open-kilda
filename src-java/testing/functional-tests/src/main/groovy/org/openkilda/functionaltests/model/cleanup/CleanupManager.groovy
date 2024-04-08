package org.openkilda.functionaltests.model.cleanup


import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct

import static org.openkilda.functionaltests.model.cleanup.CleanupAfter.TEST

/**
 * This class handles disposable objects (flows, switch settings, turned off ISLs, etc) and deletes them or restores
 * their state after test or test class run (when called in AutomaticCleanupListener)
 */
@Component
@Scope("specThread")
class CleanupManager {
    @Value("#{T(java.util.Collections).emptyMap()}")
    private HashMap<CleanupAfter, List<Closure>> cleanupActions
    @Value("#{T(java.util.Collections).emptyMap()}")

    @PostConstruct
    void init() {
        cleanupActions[CleanupAfter.TEST] = []
        cleanupActions[CleanupAfter.CLASS] = []
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
    void addAction(Closure action, cleanupAfter = TEST) {
        cleanupActions[cleanupAfter].add(action)
    }

    /**
     * Runs cleanup. This method is directly called from AutomaticCleanupListener
     * IMPORTANT: order matters. Trying to run all the cleanup actions asynchronously can break en environment
     * @param cleanupAfter when to delete the flow: after test or after test class
     * @return nothing
     */
    void run(cleanupAfter = TEST) {
        def failedActions = cleanupActions[cleanupAfter].reverse().collect {
            try {
                it()
                return null
            } catch (Exception e) {
                return e.getMessage()
            }
        }.findAll()
        cleanupActions[cleanupAfter].clear()
        if (failedActions) {
            throw new RuntimeException(failedActions.toString())
        }
    }
}
