/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.isl;

import org.openkilda.messaging.model.DiscoveryLink;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.model.SwitchId;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * The frequency with which we should check if the ISL is healthy or existant.
     */
    private final int islHealthCheckInterval;
    private final int islConsecutiveFailureLimit;
    private final int maxAttempts;

    private final Map<SwitchId, Set<DiscoveryLink>> linksBySwitch;

    /**
     * We need to have some kind of "history" of deactivated links in order to recognize if a link is moved
     * to another endpoint.
     */
    private final Map<NetworkEndpoint, DiscoveryLink> removedFromDiscovery;

    /**
     * Base constructor of discovery manager.
     *
     * @param linksBySwitch - links storage.
     * @param islHealthCheckInterval - how frequently (in ticks) to check.
     * @param islConsecutiveFailureLimit - the threshold for sending ISL down, if it is an ISL
     * @param maxAttempts - the limit for stopping all checks.
     */
    public DiscoveryManager(Map<SwitchId, Set<DiscoveryLink>> linksBySwitch,
                            int islHealthCheckInterval, int islConsecutiveFailureLimit,
                            int maxAttempts, Integer minutesKeepRemovedIsl) {
        this.islHealthCheckInterval = islHealthCheckInterval;
        this.islConsecutiveFailureLimit = islConsecutiveFailureLimit;
        this.maxAttempts = maxAttempts;
        this.linksBySwitch = linksBySwitch;
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
        int unsentDiscoPackets = 0;

        List<DiscoveryLink> links = linksBySwitch.values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toList());

        for (DiscoveryLink link : links) {
            if (!link.isNewAttemptAllowed()) {
                logger.trace("Disco packet from {} is not sent due to exceeded limit of consecutive failures: {}",
                        link.getSource(), link.getConsecutiveFailure());
                continue;
            }

            /*
             * If we get a response from FL, we clear the attempts. Otherwise, no response, and
             * number of attempts grows.
             *
             * Further, consecutivefailures = attempts - failure limit (we wait until attempt limit before increasing)
             */
            NetworkEndpoint node = link.getSource();
            if (link.isAckAttemptsLimitExceeded(islConsecutiveFailureLimit)) {
                // We've attempted to get the health multiple times, with no response.
                // Time to mark it as a failure and send a failure notice ** if ** it was an ISL.
                if (!link.getState().isInactive() && link.getConsecutiveFailure() == 0) {
                    // It is a discovery failure if it was previously a success.
                    result.discoveryFailure.add(node);
                    logger.info("ISL IS DOWN (NO RESPONSE): {}", link);
                }
                // Increment Failure = 1 after isAttemptsLimitExceeded failure, then increases every attempt.
                logger.trace("No response to the disco packet from {}", link.getSource());
                link.fail();
                // NB: this node can be in both discoveryFailure and needDiscovery
            }

            if (link.isAttemptsLimitExceeded(islConsecutiveFailureLimit) && link.getState().isActive()) {
                logger.info("Speaker doesn't send disco packet for {}", link);
                unsentDiscoPackets++;
            }

            link.tick();
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

                logger.trace("Added to discovery plan: {}", link);
            }

        }

        if (unsentDiscoPackets > 0) {
            logger.warn("Speaker does not send discovery packets. Affected links amount: {}", unsentDiscoPackets);
        }

        return result;
    }

    /**
     * ISL Discovery Event.
     *
     * @return true if this is a new event (ie first time discovered or prior failure).
     */
    public boolean handleDiscovered(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        boolean stateChanged = false;
        NetworkEndpoint node = new NetworkEndpoint(srcSwitch, srcPort);
        Optional<DiscoveryLink> matchedLink = findBySourceEndpoint(node);

        if (!matchedLink.isPresent()) {
            logger.warn("Ignore \"AVAIL\" request for {}: node not found", node);
        } else {
            DiscoveryLink link = matchedLink.get();
            if (!link.getState().isActive() || link.isDestinationChanged(dstSwitch, dstPort)) {
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
     *
     * @return true if this is new .. ie this isn't a consecutive failure.
     */
    public boolean handleFailed(SwitchId switchId, int portId) {
        boolean stateChanged = false;
        NetworkEndpoint endpoint = new NetworkEndpoint(switchId, portId);
        Optional<DiscoveryLink> matchedLink = findBySourceEndpoint(endpoint);

        if (!matchedLink.isPresent()) {
            logger.warn("Ignoring \"FAILED\" request. There is no link found from {}", endpoint);
        } else {
            DiscoveryLink link = matchedLink.get();
            if (!link.getState().isInactive() && link.getConsecutiveFailure() == 0) {
                // This is the first failure for an ISL. That is a state change.
                // IF this isn't an ISL and we receive a failure, that isn't a state change.
                stateChanged = true;
                logger.info("ISL IS DOWN (GOT RESPONSE): {}", link);
            }
            link.renew();
            link.fail();
        }
        return stateChanged;
    }

    /**
     * Processes response from speaker. Speaker notifies us that disco packet is sent as requested.
     *
     * @param endpoint the switch and the port from which disco packets is sent.
     */
    public void handleSentDiscoPacket(NetworkEndpoint endpoint) {
        findBySourceEndpoint(endpoint)
                .ifPresent(DiscoveryLink::incAcknowledgedAttempts);

        logger.debug("Received acknowledge of sending disco from {}", endpoint);
    }

    /**
     * Register switch.
     *
     * @param switchView switch's discovery data
     */
    public void registerSwitch(SpeakerSwitchView switchView) {
        SwitchId datapath = switchView.getDatapath();
        logger.info("Register switch {} into ISL discovery manager", datapath);

        for (SpeakerSwitchPortView port : switchView.getPorts()) {
            String logPrefix = String.format(
                    "Switch %s connect-time port %d state - %s", datapath, port.getNumber(), port.getState());
            if (port.getState() == SpeakerSwitchPortView.State.UP) {
                logger.info("{}: add to discovery(reset fail counters)", logPrefix);
                registerPort(datapath, port.getNumber())
                        .resetState();
            } else {
                logger.info("{}: ignore", logPrefix);
            }
        }
    }

    /**
     * Handle port up event.
     */
    public void handlePortUp(SwitchId switchId, int portId) {
        DiscoveryLink link = registerPort(switchId, portId);
        if (link.getState().isActive() || !link.isNewAttemptAllowed()) {
            // Similar to SwitchUp, if we have a PortUp on an existing port, either we are receiving
            // a duplicate, or we missed the port down, or a new discovery has occurred.
            // NB: this should cause an ISL discovery packet to be sent.
            // TODO: we should probably separate "port up" from "do discovery". ATM, one would call
            //          this function just to get the "do discovery" functionality.
            logger.info("Port UP on existing NetworkEndpoint {};  clear failures and isl status", link);
            link.resetState();
        } else {
            logger.info("Port UP on new NetworkEndpoint: {}", link.getSource());
        }
    }

    /**
     * Register a switch port for the discovery process.
     *
     * @param switchId the port's switch.
     * @param portId the port number.
     * @return either a link of already registered port or a new one.
     */
    @VisibleForTesting
    DiscoveryLink registerPort(SwitchId switchId, int portId) {
        NetworkEndpoint node = new NetworkEndpoint(switchId, portId);
        return findBySourceEndpoint(node)
                .orElseGet(() -> registerDiscoveryLink(node));
    }

    private DiscoveryLink registerDiscoveryLink(NetworkEndpoint node) {
        DiscoveryLink link = new DiscoveryLink(node.getDatapath(), node.getPortNumber(),
                this.islHealthCheckInterval, this.maxAttempts);
        linksBySwitch.computeIfAbsent(node.getDatapath(), key -> new HashSet<>())
                .add(link);

        logger.info("The link has been registered for discovery: {}", link);

        return link;
    }

    /**
     * Handle port down event.
     */
    public void handlePortDown(SwitchId switchId, int portId) {
        NetworkEndpoint node = new NetworkEndpoint(switchId, portId);
        Optional<DiscoveryLink> discoveryLink = findBySourceEndpoint(node);

        if (!discoveryLink.isPresent()) {
            logger.warn("Can't update discovery {} -> node not found", node);
            return;
        }

        removeFromDiscovery(node);
    }

    /**
     * Finds discovery link by specified endpoint (switch datapath id and port number).
     *
     * @param endpoint from which searching for a link should be performed.
     * @return result link wrapped into {@link Optional}.
     */
    @VisibleForTesting
    Optional<DiscoveryLink> findBySourceEndpoint(NetworkEndpoint endpoint) {
        Set<DiscoveryLink> links = linksBySwitch.getOrDefault(endpoint.getDatapath(), Collections.emptySet());

        return links.stream()
                .filter(link -> endpoint.equals(link.getSource()))
                .findFirst();
    }

    /**
     * Removes links from local storage and these links won't be added into discovery plan. Removes all links from
     * switch if port number is 0, otherwise deletes specified network endpoint.
     *
     * @param endpoint that should be removed from links' storage.
     */
    private void removeFromDiscovery(NetworkEndpoint endpoint) {
        if (endpoint.getPortNumber() == 0) {
            Set<DiscoveryLink> links = linksBySwitch.remove(endpoint.getDatapath());
            if (!CollectionUtils.isEmpty(links)) {
                Map<NetworkEndpoint, DiscoveryLink> removedLinks = links.stream()
                        .collect(Collectors.toMap(DiscoveryLink::getSource, Function.identity()));
                removedFromDiscovery.putAll(removedLinks);

                logger.info("Removed switch {} from discovery", endpoint.getDatapath());
            }
        } else {
            Set<DiscoveryLink> links = linksBySwitch.get(endpoint.getDatapath());

            Optional<DiscoveryLink> matchedLink = links.stream()
                    .filter(discoveryLink -> endpoint.equals(discoveryLink.getSource()))
                    .findFirst();

            matchedLink.ifPresent(link -> {
                links.remove(link);
                removedFromDiscovery.put(endpoint, link);

                logger.info("The link has been removed from discovery: {}", link);
            });
        }
    }

    /**
     * Check whether destination of the ISL is changed (replugged to another port/switch).
     */
    public boolean isIslMoved(SwitchId srcSwitch, int srcPort, SwitchId dstSwitch, int dstPort) {
        NetworkEndpoint endpoint = new NetworkEndpoint(srcSwitch, srcPort);
        DiscoveryLink link = findBySourceEndpoint(endpoint)
                .orElseGet(() -> removedFromDiscovery.get(endpoint));

        if (link != null && link.isDestinationChanged(dstSwitch, dstPort)) {
            logger.info("ISL Event: the link has been moved: {} to {}_{}", link, dstSwitch, dstPort);
            return true;
        }

        return false;
    }

    /**
     * Returns the endpoint of the link.
     */
    public NetworkEndpoint getLinkDestination(SwitchId srcSwitch, int srcPort) {
        NetworkEndpoint srcEndpoint = new NetworkEndpoint(srcSwitch, srcPort);
        DiscoveryLink link = findBySourceEndpoint(srcEndpoint)
                .orElseGet(() -> removedFromDiscovery.get(srcEndpoint));

        if (link != null && link.getDestination() != null) {
            return link.getDestination();
        }

        throw new IllegalStateException(String.format("Not found link from %s_%s", srcSwitch, srcPort));
    }

    /**
     * Deactivate link from switch/port and mark it as inactive. The link will be pulled from main discovery queue or
     * from temporary storage where we store ISLs to be deleted.
     */
    public void deactivateLinkFromEndpoint(NetworkEndpoint endpoint) {
        DiscoveryLink link = findBySourceEndpoint(endpoint)
                .orElseGet(() -> removedFromDiscovery.remove(endpoint));
        if (link != null) {
            link.deactivate();

            logger.info("The link has been deactivated: {}", link);
        }
    }

    /**
     * Checks if we are sending disco packets from specified endpoint.
     *
     * @param switchId switch datapath id.
     * @param portId port number.
     * @return true if we already send disco packets, no need to add it one more time to discovery plan.
     */
    public boolean isInDiscoveryPlan(SwitchId switchId, int portId) {
        Optional<DiscoveryLink> link = findBySourceEndpoint(new NetworkEndpoint(switchId, portId));

        return link.isPresent() && link.get().isNewAttemptAllowed();
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
