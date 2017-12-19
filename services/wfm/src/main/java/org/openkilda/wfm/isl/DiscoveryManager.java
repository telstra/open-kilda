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

    public Plan makeDiscoveryPlan() {
        Plan result = new Plan();

        for (DiscoveryNode subject : pollQueue) {
            Node node = new Node(subject.getSwitchId(), subject.getPortId());

            if (subject.forlorn()) {
               continue;
            } else if (subject.isStale(consecutiveLostTillFail) && subject.timeToCheck()) {
                result.discoveryFailure.add(node);
                subject.resetTickCounter();
                continue;
            } else if (subject.isStale(consecutiveLostTillFail) && !subject.timeToCheck()){
                subject.logTick();
                continue;
            }

            if (filter.isMatch(subject)) {
                logger.debug("Skip {} due to ISL filter match", subject);
                subject.renew();
                subject.resetTickCounter();
                continue;
            }

            subject.incAge();
            subject.resetTickCounter();
            result.needDiscovery.add(node);
        }

        return result;
    }

    public void handleDiscovered(String switchId, String portId) {
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignore \"AVAIL\" request for {}: node not found", node);
        } else {
            DiscoveryNode subject = subjectList.get(0);
            subject.renew();
            logger.info("Handle \"AVAIL\" event for {}", subject);
        }
    }
    public void handleFailed(String switchId, String portId) {
        Node node = new Node(switchId, portId);
        List<DiscoveryNode> subjectList = filterQueue(node);

        if (subjectList.size() == 0) {
            logger.warn("Ignoring \"FAILED\" request for {}: node not found", node);
        } else {
            DiscoveryNode subject = subjectList.get(0);
            subject.countFailure();
            logger.info("Successfully handled \"FAILED\" event for {}", subject);
        }
    }
    public void handleSwitchUp(String switchId) {
        logger.info("Register switch {} into ISL discovery manager", switchId);
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
