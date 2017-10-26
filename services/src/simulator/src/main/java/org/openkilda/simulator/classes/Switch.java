package org.openkilda.simulator.classes;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openkilda.messaging.Destination;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.stats.PortStatsData;
import org.openkilda.messaging.info.stats.PortStatsEntry;
import org.openkilda.messaging.info.stats.PortStatsReply;
import org.projectfloodlight.openflow.types.DatapathId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Switch {
    private static final Logger logger = LogManager.getLogger(Switch.class);
    protected boolean active = true;
    protected int controlPlaneLatency = 0;
    protected List<Port> ports = new ArrayList<>();
    protected DatapathId dpid;

    public Switch(DatapathId dpid) {
        this(dpid, 0);
    }

    public Switch(DatapathId dpid, int numOfPorts) {
        this.dpid = dpid;
        int portNum = 0;
        while (portNum < numOfPorts) {
            addPort(true, true);
            portNum++;
        }
    }

    protected void addPort(boolean isActive, boolean isForwarding) {
        Port port = new Port(numOfPorts(), isActive, isForwarding);
        this.ports.add(port);
    }

    public int numOfPorts() {
        return ports.size();
    }

    public InfoMessage portStats() {
        long xid = 12345;

        List<PortStatsEntry> portStatsEntries = new ArrayList<>();
        ports.forEach(port->portStatsEntries.add(port.getStats()));

        PortStatsReply portStatsReply = new PortStatsReply(xid, portStatsEntries);
        List<PortStatsReply> portStatsReplies = new ArrayList<>();
        portStatsReplies.add(portStatsReply);
        InfoData data = new PortStatsData(dpid.toString(), portStatsReplies);

        long now = Instant.now().toEpochMilli();
        String correlationId = "simulator";
        return new InfoMessage(data, now, correlationId, Destination.WFM);
    }

    public DatapathId getDpid() {
        return dpid;
    }

    public List<Port> getPorts() {
        return ports;
    }

    public Port getPort(int num) throws ArrayIndexOutOfBoundsException {
        Port port;
        try {
            port = ports.get(num);
        } catch (ArrayIndexOutOfBoundsException e) {
            logger.error("port {} is invalid.", num);
            throw e;
        }
        return port;
    }
}
