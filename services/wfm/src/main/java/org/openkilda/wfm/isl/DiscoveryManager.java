package org.openkilda.wfm.isl;

import org.openkilda.wfm.topology.stats.StatsTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class DiscoveryManager {
    private final Logger logger = LoggerFactory.getLogger(StatsTopology.class);

    private final IIslFilter filter;
    private final Integer consecutiveLostTillFail;
    private final LinkedList<DiscoveryNode> pollQueue;

    public DiscoveryManager(
            IIslFilter filter, LinkedList<DiscoveryNode> persistentQueue, Integer consecutiveLostTillFail) {
        this.filter = filter;
        this.consecutiveLostTillFail = consecutiveLostTillFail;
        this.pollQueue = persistentQueue;
    }

    /**
     * The discovery plan takes into consideration multiple metrics to determine what should be
     * discovered.
     *
     * At present, we want to send Discovery health checks on every ISL every x period.
     * And, if the Discovery fails (either isn't an ISL or ISL is down) then we may want to give up
     * checking.
     *
     * General algorithm:
     * 1) if the node is an ISL (isFoundIsl) .. and is UP .. keep checking
     * 2) if the node is not an ISL (ie !isFoundIsl), then check less frequently
     * 3) if the node is an ISL .. and is DOWN .. keep checking
     */
    public Plan makeDiscoveryPlan() {
        Plan result = new Plan();

        for (DiscoveryNode subject : pollQueue) {

            if (filter.isMatch(subject)) {
                // skip checks on what is in the Filter:
                // TODO: what is in the FILTER? Is this the external filter (ie known ISL's?) Still want health check in this scenario..
                logger.debug("Skip {} due to ISL filter match", subject);
                subject.renew();
                subject.resetTickCounter();
                continue;
            }

            if (subject.forlorn()) {
                // stop checking if it has reached the maximum check period.
                continue;
            }

            /*
             * If we get a response from FL, we clear the attempts
             */
            Node node = new Node(subject.getSwitchId(), subject.getPortId());
            if (subject.maxAttempts(consecutiveLostTillFail)) {
                // We've attempted to get the health multiple times, with no response.
                // Time to mark it as a failure and send a failure notice ** if ** it was an ISL.
                if (subject.isFoundIsl() && subject.getConsecutiveFailure() == 0) {
                    // It is a discovery failure if it was previously a success
                    result.discoveryFailure.add(node);
                    logger.info("ISL IS DOWN (NO RESPONSE): {}", subject);
                }
                subject.incConsecutiveFailure(); // don't notify of discovery failure again
                // NB: this node can be in both discoveryFailure and needDiscovery
            }

            /*
             * If you get here, the following are true:
             *  - it isn't in some filter
             *  - it hasn't reached failure limit (forlorn)
             *  - it is either time to send discovery or not
             *  - NB: we'll keep trying to send discovery, even if we don't get a response.
             */
            if (subject.timeToCheck()) {
                subject.incAttempts();
                subject.resetTickCounter();
                result.needDiscovery.add(node);
            } else {
                subject.logTick();
            }

        }

        return result;
    }

    /**
     * ISL Discovery Event
     * @return true if this is a new event (ie first time discovered or prior failure)
     */
    public boolean handleDiscovered(String switchId, String portId) {
        boolean stateChanged = false;
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignore \"AVAIL\" request for {}: node not found", node);
        } else {
            DiscoveryNode subject = subjectList.get(0);
            if (!subject.isFoundIsl()){
                // "forever" mark this port as part of an ISL
                subject.setFoundIsl(true);
                stateChanged = true;
                logger.info("FOUND ISL: {}", subject);
            }
            if (subject.getConsecutiveFailure()>0){
                // We've found failures, but now we've had success, so that is a state change.
                // To repeat, current model for state change is just 1 failure. If we change this
                // policy, then change the test above.
                stateChanged = true;
                logger.info("ISL IS UP: {}", subject);
            }
            subject.renew();
            subject.incConsecutiveSuccess();
            subject.clearConsecutiveFailure();
            // If one of the logs above wasn't reachd, don't log anything .. ISL was up and is still up
        }
        return stateChanged;
    }

    /**
     * ISL Failure Event
     * @return true if this is new .. ie this isn't a consecutive failure.
     */
    public boolean handleFailed(String switchId, String portId) {
        boolean stateChanged = false;
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignoring \"FAILED\" request for {}: node not found", node);
        } else {
            DiscoveryNode subject = subjectList.get(0);
            if (subject.isFoundIsl() && subject.getConsecutiveFailure() == 0){
                // This is the first failure for an ISL. That is a state change.
                // IF this isn't an ISL and we receive a failure, that isn't a state change.
                stateChanged = true;
                logger.info("ISL IS DOWN (GOT RESPONSE): {}", subject);
            }
            subject.renew();
            subject.incConsecutiveFailure();
            subject.clearConsecutiveSuccess();
        }
        return stateChanged;
    }

    public void handleSwitchUp(String switchId) {
        logger.info("Register switch {} into ISL discovery manager", switchId);
        // TODO: this method doesn't do anything .. but it should register the switch.
        //          At least, it seems like it should do something to register a switch, even
        //          though this can be lazily done when the first port event arrives.
    }

    public void handleSwitchDown(String switchId) {
        Node node = new Node(switchId, null);
        List<DiscoveryNode> subjectList = filterQueue(node, true);

        logger.info("Deregister switch {} from ISL discovery manager", switchId);
        for (DiscoveryNode subject : subjectList) {
            logger.info("Del {}", subject);
        }
    }

    public void handlePortUp(String switchId, String portId) {
        DiscoveryNode subject;
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node);

        if (subjectList.size() != 0) {
            subject = subjectList.get(0);
            logger.warn("Try to add already exists {}", subject);
            return;
        }

        subject = new DiscoveryNode(node.switchId, node.portId);
        pollQueue.add(subject);
        logger.info("New {}", subject);
    }

    public void handlePortDown(String switchId, String portId) {
        DiscoveryNode subject;
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node, true);

        if (subjectList.size() == 0) {
            logger.warn("Can't update discovery {} -> node not found", node);
            return;
        }

        subject = subjectList.get(0);
        logger.info("Del {}", subject);
    }

    private List<DiscoveryNode> filterQueue(Node subject) {
        return filterQueue(subject, false);
    }

    private List<DiscoveryNode> filterQueue(Node subject, boolean extract) {
        List<DiscoveryNode> result = new LinkedList<>();
        for (ListIterator<DiscoveryNode> it = pollQueue.listIterator(); it.hasNext(); ) {
            DiscoveryNode node = it.next();
            if (!subject.matchDiscoveryNode(node)) {
                continue;
            }

            if (extract) {
                it.remove();
            }
            result.add(node);
        }

        return result;
    }

    public class Plan {
        public final List<Node> needDiscovery;
        public final List<Node> discoveryFailure;

        private Plan() {
            this.needDiscovery = new LinkedList<>();
            this.discoveryFailure = new LinkedList<>();
        }
    }

    public class Node {
        public final String switchId;
        public final String portId;

        public Node(String switchId, String portId) {
            this.switchId = switchId;
            this.portId = portId;
        }

        public Node(DiscoveryNode node) {
            this.switchId = node.getSwitchId();
            this.portId = node.getPortId();
        }

        boolean matchDiscoveryNode(DiscoveryNode target) {
            if (! switchId.equals(target.getSwitchId())) {
                return false;
            }
            if (portId == null) {
                return true;
            }
            return portId.equals(target.getPortId());
        }

        @Override
        public String toString() {
            return "Node{" +
                    "switchId='" + switchId + '\'' +
                    ", portId='" + portId + '\'' +
                    '}';
        }
    }
}
