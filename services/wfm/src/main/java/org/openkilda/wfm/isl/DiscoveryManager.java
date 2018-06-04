package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;

import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The DiscoveryManager holds the core logic for managing ISLs. This includes all of the
 * business rules related to Switch Up/Down, Port Up/Down, failure counts and limits, etc.
 * Comments on the business logic and rules are embedded in the rest of this class.
 *
 * <p>TODO: Refactor DiscoveryManager in the following ways:
 *      1) Integrate any remaining business logic for storm code into this class so that the logic
 *          is concentrated in one place
 *      2) The timers/counters are a little bit out of whack and should be cleaned up:
 *          - how frequently to emit is clear .. but could be better to base it on seconds, not ticks
 *          - when to send a Failure if it is an ISL and we are not getting a response - currently
 *              using islConsecutiveFailureLimit, but the name could be better
 *          - when to stop - using forlornLimit - but would like a better name and more clarity
 *          - there are a few other fields that may be worthwhile .. ie better, more advance
 *              policy mechanism for behavior around ISL
 *      3) Ensure some separation between lifetime failure counts, current failure counts, and
 *          whether an ISL discovery packet should be sent. As an example, if we've stopped sending,
 *          and want to send discovery again, is there a clean way to do this?
 */
public class DiscoveryManager {
    private final Logger logger = LoggerFactory.getLogger(DiscoveryManager.class);

    private final IIslFilter filter;
    /**
     * The frequency with which we should check if the ISL is healthy or existant.
     */
    private final Integer islHealthCheckInterval;
    private final Integer islConsecutiveFailureLimit;
    private final Integer maxAttempts;
    private final LinkedList<DiscoveryLink> pollQueue;
    private final Map<NetworkEndpoint, DiscoveryLink> removedFromDiscovery;

    /**
     * Base constructor of discovery manager.
     * @param filter - a list of nodes we should not do discovery on, if any.
     * @param persistentQueue - the persistent queue to use.
     * @param islHealthCheckInterval - how frequently (in ticks) to check.
     * @param islConsecutiveFailureLimit - the threshold for sending ISL down, if it is an ISL
     * @param maxAttempts - the limit for stopping all checks.
     */
    public DiscoveryManager(IIslFilter filter, LinkedList<DiscoveryLink> persistentQueue,
                            Integer islHealthCheckInterval, Integer islConsecutiveFailureLimit,
                            Integer maxAttempts, Integer minutesKeepRemovedIsl) {
        this.filter = filter;
        this.islHealthCheckInterval = islHealthCheckInterval;
        this.islConsecutiveFailureLimit = islConsecutiveFailureLimit;
        this.maxAttempts = maxAttempts;
        this.pollQueue = persistentQueue;
        this.removedFromDiscovery = new PassiveExpiringMap<>(minutesKeepRemovedIsl, TimeUnit.MINUTES, new HashMap<>());
    }

    /**
     * The discovery plan takes into consideration multiple metrics to determine what should be
     * discovered.
     *
     * <p>At present, we want to send Discovery health checks on every ISL every x period.
     * And, if the Discovery fails (either isn't an ISL or ISL is down) then we may want to give up
     * checking.
     *
     * <p>General algorithm:
     * 1) if the node is an ISL (isFoundIsl) .. and is UP .. keep checking
     * 2) if the node is not an ISL (ie !isFoundIsl), then check less frequently
     * 3) if the node is an ISL .. and is DOWN .. keep checking
     */
    public Plan makeDiscoveryPlan() {
        Plan result = new Plan();

        for (DiscoveryLink link : pollQueue) {

            if (!checkForIsl(link)) {
                continue;
            }

            /*
             * If we get a response from FL, we clear the attempts. Otherwise, no response, and
             * number of attempts grows.
             *
             * Further, consecutivefailures = attempts - failure limit (we wait until attempt limit before increasing)
             */
            NetworkEndpoint node = link.getSource();
            if (link.isAttemptsLimitExceeded(islConsecutiveFailureLimit)) {
                // We've attempted to get the health multiple times, with no response.
                // Time to mark it as a failure and send a failure notice ** if ** it was an ISL.
                if (link.isActive() && link.getConsecutiveFailure() == 0) {
                    // It is a discovery failure if it was previously a success.
                    // NB:
                    result.discoveryFailure.add(node);
                    logger.info("ISL IS DOWN (NO RESPONSE): {}", link);
                }
                // Increment Failure = 1 after isAttemptsLimitExceeded failure, then increases every attempt.
                link.fail();
                // NB: this node can be in both discoveryFailure and needDiscovery
            }

            /*
             * If you get here, the following are true:
             *  - it isn't in some filter
             *  - it hasn't reached failure limit (forlorn)
             *  - it is either time to send discovery or not
             *  - NB: we'll keep trying to send discovery, even if we don't get a response.
             */
            if (link.timeToCheck()) {
                link.incAttempts();
                link.resetTickCounter();
                result.needDiscovery.add(node);
            } else {
                link.tick();
            }

        }

        return result;
    }

    /**
     * ISL Discovery Event.
     * @return true if this is a new event (ie first time discovered or prior failure).
     */
    public boolean handleDiscovered(String srcSwitch, int srcPort, String dstSwitch, int dstPort) {
        boolean stateChanged = false;
        NetworkEndpoint node = new NetworkEndpoint(srcSwitch, srcPort);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignore \"AVAIL\" request for {}: node not found", node);
        } else {
            DiscoveryLink link = subjectList.get(0);
            if (!link.isActive() || link.isDestinationChanged(dstSwitch, dstPort)) {
                // we've found newly discovered or moved/replugged isl
                link.activate(new NetworkEndpoint(dstSwitch, dstPort));

                stateChanged = true;
                logger.info("FOUND ISL: {}", link);
            } else if (link.getConsecutiveFailure() > 0) {
                // We've found failures, but now we've had success, so that is a state change.
                // To repeat, current model for state change is just 1 failure. If we change this
                // policy, then change the test above.
                stateChanged = true;
                logger.info("ISL IS UP: {}", link);
            }
            link.renew();
            link.success();
            link.clearConsecutiveFailure();
            // If one of the logs above wasn't reachd, don't log anything .. ISL was up and is still up
        }

        if (stateChanged) {
            // Add logic to ensure we send a discovery packet for the opposite direction.
            // TODO: in order to do this here, we need more information (ie the other end of the ISL)
            //      Since that isn't passed in and isn't available in our state, have to rely on the
            //      calling function.

        }
        return stateChanged;
    }

    /**
     * ISL Failure Event.
     * @return true if this is new .. ie this isn't a consecutive failure.
     */
    public boolean handleFailed(String switchId, int portId) {
        boolean stateChanged = false;
        NetworkEndpoint node = new NetworkEndpoint(switchId, portId);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignoring \"FAILED\" request for {}: node not found", node);
        } else {
            DiscoveryLink link = subjectList.get(0);
            if (link.isActive() && link.getConsecutiveFailure() == 0) {
                // This is the first failure for an ISL. That is a state change.
                // IF this isn't an ISL and we receive a failure, that isn't a state change.
                stateChanged = true;
                logger.info("ISL IS DOWN (GOT RESPONSE): {}", link);
            }
            link.renew();
            link.fail();
            link.deactivate();
        }
        return stateChanged;
    }

    /**
     * Handle added/activated switch.
     * @param switchId id of the switch.
     */
    public void handleSwitchUp(String switchId) {
        logger.info("Register switch {} into ISL discovery manager", switchId);
        // TODO: this method *use to not* do anything .. but it should register the switch.
        //          At least, it seems like it should do something to register a switch, even
        //          though this can be lazily done when the first port event arrives.

        /*
         * If a switch comes up, clear any "isFoundIsl" flags, in case something has changed,
         * and/or if the TE has cleared it's state .. this will pass along the ISL.
         */
        NetworkEndpoint node = new NetworkEndpoint(switchId, 0);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node, false);

        if (subjectList.size() > 0) {
            logger.info("Received SWITCH UP (id:{}) with EXISTING NODES.  Clearing isFoundISL flags", switchId);
            for (DiscoveryLink subject : subjectList) {
                subject.deactivate();
            }
        }
    }

    /**
     * Handle deactivated switch.
     * @param switchId id of the switch.
     */
    public void handleSwitchDown(String switchId) {
        NetworkEndpoint node = new NetworkEndpoint(switchId, 0);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node, true);

        logger.info("Deregister switch {} from ISL discovery manager", switchId);
        for (DiscoveryLink subject : subjectList) {
            logger.info("Del {}", subject);
        }
    }

    /**
     * Handle port up event.
     */
    public void handlePortUp(String switchId, int portId) {
        DiscoveryLink link;
        NetworkEndpoint node = new NetworkEndpoint(switchId, portId);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node);

        if (subjectList.size() != 0) {
            // Similar to SwitchUp, if we have a PortUp on an existing port, either we are receiving
            // a duplicate, or we missed the port down, or a new discovery has occurred.
            // NB: this should cause an ISL discovery packet to be sent.
            // TODO: we should probably separate "port up" from "do discovery". ATM, one would call
            //          this function just to get the "do discovery" functionality.
            link = subjectList.get(0);
            logger.info("Port UP on existing node {};  clear failures and ISLFound", link);
            link.deactivate();
        } else {
            link = new DiscoveryLink(node.getSwitchDpId(), node.getPortId(),
                    this.islHealthCheckInterval, this.maxAttempts);
            pollQueue.add(link);
            logger.info("New {}", link);
        }
    }

    /**
     * Handle port down event.
     */
    public void handlePortDown(String switchId, int portId) {
        DiscoveryLink subject;
        NetworkEndpoint node = new NetworkEndpoint(switchId, portId);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node, true);

        if (subjectList.size() == 0) {
            logger.warn("Can't update discovery {} -> node not found", node);
            return;
        }

        subject = subjectList.get(0);
        logger.info("Del {}", subject);
    }

    /**
     * Filter the list of nodes based on switch, or switch and port.
     *
     * @param subject The switch (if port is null), or switch and port, to match
     * @return a list of any matched nodes.
     */
    public List<DiscoveryLink> findBySourceSwitch(NetworkEndpoint subject) {
        return findBySourceSwitch(subject, false);
    }

    /**
     * Find all discovery links from all ports in the particular switch.
     */
    public List<DiscoveryLink> findBySourceSwitch(String switchId) {
        return findBySourceSwitch(new NetworkEndpoint(switchId, 0));
    }

    private List<DiscoveryLink> findBySourceSwitch(NetworkEndpoint subject, boolean extract) {
        List<DiscoveryLink> result = new LinkedList<>();
        for (ListIterator<DiscoveryLink> it = pollQueue.listIterator(); it.hasNext(); ) {
            DiscoveryLink link = it.next();

            if (isMatchedEndpoint(subject, link.getSource())) {
                if (extract) {
                    logger.debug("Removing Link from discovery mechanism: {}", link);
                    removedFromDiscovery.put(subject, link);
                    it.remove();
                }

                result.add(link);
                // no need to continue searching if we are looking for the link from particular port
                // looks like we don't store more than one link from one port in discovery manager
                if (subject.getPortId() != 0) {
                    break;
                }
            }
        }

        return result;
    }

    private boolean isMatchedEndpoint(NetworkEndpoint subject, NetworkEndpoint target) {
        return StringUtils.equals(subject.getSwitchDpId(), target.getSwitchDpId())
                && (subject.getPortId() == 0 || subject.getPortId().equals(target.getPortId()));
    }

    /**
     * Check whether destination of the ISL is changed (replugged to another port/switch).
     */
    public boolean isIslMoved(String srcSwitch, int srcPort, String dstSwitch, int dstPort) {
        boolean isMoved = false;
        NetworkEndpoint node = new NetworkEndpoint(srcSwitch, srcPort);
        List<DiscoveryLink> subjectList = findBySourceSwitch(node);
        if (!subjectList.isEmpty()) {
            if (subjectList.size() > 1) {
                logger.warn("There more than one link on {}_{} in discovery manager", srcSwitch, srcPort);
            }

            DiscoveryLink link = subjectList.get(0);

            isMoved = link.isDestinationChanged(dstSwitch, dstPort);
        } else if (removedFromDiscovery.containsKey(node)) {
            DiscoveryLink link = removedFromDiscovery.get(node);
            isMoved = link.isDestinationChanged(dstSwitch, dstPort);
        }

        return isMoved;
    }

    /**
     * Returns the endpoint of the link.
     */
    public NetworkEndpoint getLinkDestination(String srcSwitch, int srcPort) {
        NetworkEndpoint srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        NetworkEndpoint dstEndpoint;
        List<DiscoveryLink> links = findBySourceSwitch(srcEndpoint);
        if (!links.isEmpty()) {
            dstEndpoint = links.get(0).getDestination();
        } else if (removedFromDiscovery.containsKey(srcEndpoint)) {
            DiscoveryLink link = removedFromDiscovery.get(srcEndpoint);
            dstEndpoint = link.getDestination();
        } else {
            throw new IllegalStateException(String.format("Not found link from %s_%s", srcSwitch, srcPort));
        }

        return dstEndpoint;
    }

    /**
     * Deactivate link from switch/port and mark it as inactive. The link will be pulled from main discovery queue or
     * from temporary storage where we store ISLs to be deleted.
     */
    public void deactivateLinkFromEndpoint(NetworkEndpoint endpoint) {
        List<DiscoveryLink> links = findBySourceSwitch(endpoint);
        if (!links.isEmpty()) {
            links.get(0).deactivate();
        } else if (removedFromDiscovery.containsKey(endpoint)) {
            DiscoveryLink link = removedFromDiscovery.get(endpoint);
            link.deactivate();
        }
    }

    /**
     * The "ISL" could be down if it is.
     * - not an ISL
     * - has timed out (forlorned)
     *
     * @return true if not an ISL or is forlorned
     */
    public boolean checkForIsl(String switchId, int portId) {
        List<DiscoveryLink> subjectList = findBySourceSwitch(new NetworkEndpoint(switchId, portId));

        if (subjectList.size() != 0) {
            return checkForIsl(subjectList.get(0));
        }
        // We don't know about this node .. definitely not testing for ISL.
        return false;
    }

    private boolean checkForIsl(DiscoveryLink link) {
        if (filter.isMatch(link)) {
            // skip checks on what is in the Filter:
            // TODO: what is in the FILTER? Is this the external filter (ie known ISL's?)
            // Still want health check in this scenario..
            logger.debug("Skip {} due to ISL filter match", link);
            link.renew();
            link.resetTickCounter();
            return false;
        }
        return !link.isDiscoverySuspended();
    }


    public final class Plan {
        public final List<NetworkEndpoint> needDiscovery;
        public final List<NetworkEndpoint> discoveryFailure;

        private Plan() {
            this.needDiscovery = new LinkedList<>();
            this.discoveryFailure = new LinkedList<>();
        }
    }
}
