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

import org.openkilda.model.SwitchId;
import org.openkilda.model.SwitchStatus;
import org.openkilda.persistence.ferma.model.Switch;

import com.syncleus.ferma.AbstractVertexFrame;
import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.TVertex;
import com.syncleus.ferma.annotations.GraphElement;
import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.time.Instant;

@GraphElement
public abstract class SwitchFrame extends AbstractVertexFrame implements Switch {
    public static final String FRAME_LABEL = "switch";

    public static final String SWITCH_ID_PROPERTY = "name";

    @Override
    public SwitchId getSwitchId() {
        return new SwitchId(getProperty(SWITCH_ID_PROPERTY));
    }

    @Property(SWITCH_ID_PROPERTY)
    @Override
    public void setSwitchId(@NonNull SwitchId switchId) {
        setProperty(SWITCH_ID_PROPERTY, switchId.toString());
    }

    @Override
    public SwitchStatus getStatus() {
        String value = getProperty("state");
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return SwitchStatus.valueOf(value.toUpperCase());
    }

    @Override
    public void setStatus(SwitchStatus status) {
        setProperty("state", status == null ? null : status.name().toLowerCase());
    }

    @Property("address")
    @Override
    public abstract String getAddress();

    @Property("address")
    @Override
    public abstract void setAddress(String address);

    @Property("hostname")
    @Override
    public abstract String getHostname();

    @Property("hostname")
    @Override
    public abstract void setHostname(String hostname);

    @Property("controller")
    @Override
    public abstract String getController();

    @Property("controller")
    @Override
    public abstract void setController(String controller);

    @Property("description")
    @Override
    public abstract String getDescription();

    @Property("description")
    @Override
    public abstract void setDescription(String description);

    @Property("of_version")
    @Override
    public abstract String getOfVersion();

    @Property("of_version")
    @Override
    public abstract void setOfVersion(String ofVersion);

    @Property("of_description_manufacturer")
    @Override
    public abstract String getOfDescriptionManufacturer();

    @Property("of_description_manufacturer")
    @Override
    public abstract void setOfDescriptionManufacturer(String ofDescriptionManufacturer);

    @Property("of_description_hardware")
    @Override
    public abstract String getOfDescriptionHardware();

    @Property("of_description_hardware")
    @Override
    public abstract void setOfDescriptionHardware(String ofDescriptionHardware);

    @Property("of_description_software")
    @Override
    public abstract String getOfDescriptionSoftware();

    @Property("of_description_software")
    @Override
    public abstract void setOfDescriptionSoftware(String ofDescriptionSoftware);

    @Property("of_description_serial_number")
    @Override
    public abstract String getOfDescriptionSerialNumber();

    @Property("of_description_serial_number")
    @Override
    public abstract void setOfDescriptionSerialNumber(String ofDescriptionSerialNumber);

    @Property("of_description_datapath")
    @Override
    public abstract String getOfDescriptionDatapath();

    @Property("of_description_datapath")
    @Override
    public abstract void setOfDescriptionDatapath(String ofDescriptionDatapath);

    @Property("under_maintenance")
    @Override
    public abstract boolean isUnderMaintenance();

    @Property("under_maintenance")
    @Override
    public abstract void setUnderMaintenance(boolean underMaintenance);

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

    public void updateWith(Switch sw) {
        setStatus(sw.getStatus());
        setAddress(sw.getAddress());
        setHostname(sw.getHostname());
        setController(sw.getController());
        setDescription(sw.getDescription());
        setOfVersion(sw.getOfVersion());
        setOfDescriptionManufacturer(sw.getOfDescriptionManufacturer());
        setOfDescriptionHardware(sw.getOfDescriptionHardware());
        setOfDescriptionSoftware(sw.getOfDescriptionSoftware());
        setOfDescriptionSerialNumber(sw.getOfDescriptionSerialNumber());
        setOfDescriptionDatapath(sw.getOfDescriptionDatapath());
        setUnderMaintenance(sw.isUnderMaintenance());
        setTimeModify(sw.getTimeModify());
    }

    public void delete() {
        remove();
    }

    public static SwitchFrame addNew(FramedGraph graph, Switch newSwitch) {
        // A workaround for improper implementation of the untyped mode in OrientTransactionFactoryImpl.
        Vertex element = graph.addFramedVertex(TVertex.DEFAULT_INITIALIZER, T.label, FRAME_LABEL).getElement();
        SwitchFrame frame = graph.frameElementExplicit(element, SwitchFrame.class);
        frame.setSwitchId(newSwitch.getSwitchId());
        frame.setTimeCreate(newSwitch.getTimeCreate());
        frame.updateWith(newSwitch);
        return frame;
    }

    public static SwitchFrame load(FramedGraph graph, SwitchId switchId) {
        return graph.traverse(input -> input.V().hasLabel(FRAME_LABEL).has(SWITCH_ID_PROPERTY, switchId.toString()))
                .nextOrDefaultExplicit(SwitchFrame.class, null);
    }

    public static void delete(FramedGraph graph, Switch sw) {
        if (sw instanceof SwitchFrame) {
            ((SwitchFrame) sw).delete();
        } else {
            SwitchFrame switchFrame = load(graph, sw.getSwitchId());
            if (switchFrame != null) {
                switchFrame.delete();
            }
        }
    }
}