/* Copyright 2019 Telstra Open Source
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

package org.openkilda.persistence.ferma.repositories.frames;

import org.openkilda.model.IslDownReason;
import org.openkilda.model.IslStatus;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.model.Isl;
import org.openkilda.persistence.ferma.model.Switch;

import com.syncleus.ferma.AbstractEdgeFrame;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.GraphElement;
import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Edge;

import java.time.Instant;

@GraphElement
public abstract class IslFrame extends AbstractEdgeFrame implements Isl {
    public static final String FRAME_LABEL = "isl";

    static final String SRC_PORT_PROPERTY = "src_port";
    static final String DST_PORT_PROPERTY = "dst_port";

    @Override
    public int getSrcPort() {
        return ((Long) getProperty(SRC_PORT_PROPERTY)).intValue();
    }

    @Override
    public void setSrcPort(int srcPort) {
        setProperty(SRC_PORT_PROPERTY, (long) srcPort);
    }

    @Override
    public int getDestPort() {
        return ((Long) getProperty(DST_PORT_PROPERTY)).intValue();
    }

    @Override
    public void setDestPort(int destPort) {
        setProperty(DST_PORT_PROPERTY, (long) destPort);
    }

    @Property("latency")
    @Override
    public abstract long getLatency();

    @Property("latency")
    @Override
    public abstract void setLatency(long latency);

    @Property("speed")
    @Override
    public abstract long getSpeed();

    @Property("speed")
    @Override
    public abstract void setSpeed(long speed);

    @Override
    public int getCost() {
        return ((Long) getProperty("cost")).intValue();
    }

    @Override
    public void setCost(int cost) {
        setProperty("cost", (long) cost);
    }

    @Property("max_bandwidth")
    @Override
    public abstract long getMaxBandwidth();

    @Property("max_bandwidth")
    @Override
    public abstract void setMaxBandwidth(long maxBandwidth);

    @Property("default_max_bandwidth")
    @Override
    public abstract long getDefaultMaxBandwidth();

    @Property("default_max_bandwidth")
    @Override
    public abstract void setDefaultMaxBandwidth(long defaultMaxBandwidth);

    @Property("available_bandwidth")
    @Override
    public abstract long getAvailableBandwidth();

    @Property("available_bandwidth")
    @Override
    public abstract void setAvailableBandwidth(long availableBandwidth);

    @Override
    public IslStatus getStatus() {
        String value = getProperty("status");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return IslStatus.valueOf(value.toUpperCase());
    }

    @Override
    public void setStatus(IslStatus status) {
        setProperty("status", status == null ? null : status.name().toLowerCase());
    }

    @Override
    public IslStatus getActualStatus() {
        String value = getProperty("actual_status");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return IslStatus.valueOf(value.toUpperCase());
    }

    @Override
    public void setActualStatus(IslStatus status) {
        setProperty("actual_status", status == null ? null : status.name().toLowerCase());
    }

    @Override
    public IslDownReason getDownReason() {
        String value = getProperty("down_reason");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return IslDownReason.valueOf(value.toUpperCase());
    }

    @Override
    public void setDownReason(IslDownReason downReason) {
        setProperty("down_reason", downReason == null ? null : downReason.name().toLowerCase());
    }

    @Override
    public Instant getTimeCreate() {
        String value = getProperty("time_create");
        return value == null ? null : Instant.parse(value);
    }

    @Override
    public void setTimeCreate(Instant timeCreate) {
        setProperty("time_create", timeCreate == null ? null : timeCreate.toString());
    }

    @Override
    public Instant getTimeModify() {
        String value = getProperty("time_modify");
        return value == null ? null : Instant.parse(value);
    }

    @Override
    public void setTimeModify(Instant timeModify) {
        setProperty("time_modify", timeModify == null ? null : timeModify.toString());
    }

    @Property("under_maintenance")
    @Override
    public abstract boolean isUnderMaintenance();

    @Property("under_maintenance")
    @Override
    public abstract void setUnderMaintenance(boolean underMaintenance);

    @Property("enable_bfd")
    @Override
    public abstract boolean isEnableBfd();

    @Property("enable_bfd")
    @Override
    public abstract void setEnableBfd(boolean enableBfd);

    @Property("bfd_session")
    @Override
    public abstract String getBfdSessionStatus();

    @Property("bfd_session")
    @Override
    public abstract void setBfdSessionStatus(String bfdSessionStatus);

    @Override
    public Switch getSrcSwitch() {
        return traverse(e -> e.outV().hasLabel(SwitchFrame.FRAME_LABEL)).nextExplicit(SwitchFrame.class);
    }

    @Override
    public SwitchId getSrcSwitchId() {
        return new SwitchId(traverse(e -> e.outV().hasLabel(SwitchFrame.FRAME_LABEL)
                .values(SwitchFrame.SWITCH_ID_PROPERTY)).nextExplicit(String.class));
    }

    @Override
    public Switch getDestSwitch() {
        return traverse(e -> e.inV().hasLabel(SwitchFrame.FRAME_LABEL)).nextExplicit(SwitchFrame.class);
    }

    @Override
    public SwitchId getDestSwitchId() {
        return new SwitchId(traverse(e -> e.inV().hasLabel(SwitchFrame.FRAME_LABEL)
                .values(SwitchFrame.SWITCH_ID_PROPERTY)).nextExplicit(String.class));
    }

    public void updateWith(Isl isl) {
        setLatency(isl.getLatency());
        setSpeed(isl.getSpeed());
        setCost(isl.getCost());
        setMaxBandwidth(isl.getMaxBandwidth());
        setDefaultMaxBandwidth(isl.getDefaultMaxBandwidth());
        setAvailableBandwidth(isl.getAvailableBandwidth());
        setStatus(isl.getStatus());
        setActualStatus(isl.getActualStatus());
        setDownReason(isl.getDownReason());
        setTimeModify(isl.getTimeModify());
        setUnderMaintenance(isl.isUnderMaintenance());
        setEnableBfd(isl.isEnableBfd());
        setBfdSessionStatus(isl.getBfdSessionStatus());
    }

    public void delete() {
        remove();
    }

    public static IslFrame addNew(FramedGraph graph, Isl newIsl) {
        // A workaround for improper implementation of the untyped mode in OrientTransactionFactoryImpl.
        SwitchFrame source = SwitchFrame.load(graph, newIsl.getSrcSwitchId());
        SwitchFrame destination = SwitchFrame.load(graph, newIsl.getDestSwitchId());
        Edge element = graph.addFramedEdge(source, destination, FRAME_LABEL).getElement();
        IslFrame frame = graph.frameElementExplicit(element, IslFrame.class);
        frame.setSrcPort(newIsl.getSrcPort());
        frame.setDestPort(newIsl.getDestPort());
        frame.setTimeCreate(newIsl.getTimeCreate());
        frame.updateWith(newIsl);
        return frame;
    }

    public static IslFrame load(FramedGraph graph, SwitchId srcSwitchId, int srcPort,
                                SwitchId destSwitchId, int destPort) {
        SwitchFrame source = SwitchFrame.load(graph, srcSwitchId);
        if (source == null) {
            throw new IllegalArgumentException("Unable to locate the switch " + srcSwitchId);
        }

        return source.traverse(v -> v.inE(FRAME_LABEL).as("r")
                .has(SRC_PORT_PROPERTY, (long) srcPort)
                .has(DST_PORT_PROPERTY, (long) destPort)
                .where(__.inV().has(SwitchFrame.SWITCH_ID_PROPERTY, destSwitchId.toString()))
                .select("r"))
                .nextExplicit(IslFrame.class);
    }

    public static void delete(FramedGraph graph, Isl isl) {
        if (isl instanceof IslFrame) {
            ((IslFrame) isl).delete();
        } else {
            IslFrame islFrame = load(graph, isl.getSrcSwitchId(), isl.getSrcPort(),
                    isl.getDestSwitchId(), isl.getDestPort());
            if (islFrame != null) {
                islFrame.delete();
            }
        }
    }
}
