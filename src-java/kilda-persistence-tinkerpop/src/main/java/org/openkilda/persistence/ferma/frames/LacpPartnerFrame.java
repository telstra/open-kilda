/* Copyright 2023 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;


import org.openkilda.model.LacpPartner.LacpPartnerData;
import org.openkilda.model.MacAddress;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.MacAddressConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

@Slf4j
public abstract class LacpPartnerFrame extends KildaBaseVertexFrame implements LacpPartnerData {
    public static final String FRAME_LABEL = "lacp_partner";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String LOGICAL_PORT_NUMBER_PROPERTY = "logical_port_number";
    public static final String SYSTEM_PRIORITY_PROPERTY = "system_priority";
    public static final String SYSTEM_ID_PROPERTY = "system_id";
    public static final String KEY_PROPERTY = "key";
    public static final String PORT_PRIORITY_PROPERTY = "port_priority";
    public static final String PORT_NUMBER_PROPERTY = "port_number";
    public static final String STATE_ACTIVE_PROPERTY = "state_active";
    public static final String STATE_SHORT_TIMEOUT_PROPERTY = "state_short_timeout";
    public static final String STATE_AGGREGATABLE_PROPERTY = "state_aggregatable";
    public static final String STATE_SYNCHRONISED_PROPERTY = "state_synchronised";
    public static final String STATE_COLLECTING_PROPERTY = "state_collecting";
    public static final String STATE_DISTRIBUTING_PROPERTY = "state_distributing";
    public static final String STATE_DEFAULTED_PROPERTY = "state_defaulted";
    public static final String STATE_EXPIRED_PROPERTY = "state_expired";

    public static Optional<LacpPartnerFrame> load(FramedGraph graph, String switchId, int logicalPortNumber) {
        List<? extends LacpPartnerFrame> lacpPartnerFrames = graph.traverse(input -> input.V()
                        .hasLabel(FRAME_LABEL)
                        .has(SWITCH_ID_PROPERTY, switchId)
                        .has(LOGICAL_PORT_NUMBER_PROPERTY, logicalPortNumber))
                .toListExplicit(LacpPartnerFrame.class);
        return lacpPartnerFrames.isEmpty() ? Optional.empty() : Optional.of(lacpPartnerFrames.get(0));
    }

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setSwitchId(@NonNull SwitchId switchId);

    @Override
    @Property(LOGICAL_PORT_NUMBER_PROPERTY)
    public abstract int getLogicalPortNumber();

    @Override
    @Property(LOGICAL_PORT_NUMBER_PROPERTY)
    public abstract void setLogicalPortNumber(int logicalPortNumber);

    @Override
    @Property(SYSTEM_PRIORITY_PROPERTY)
    public abstract int getSystemPriority();

    @Override
    @Property(SYSTEM_PRIORITY_PROPERTY)
    public abstract void setSystemPriority(int systemPriority);

    @Override
    @Property(SYSTEM_ID_PROPERTY)
    @Convert(MacAddressConverter.class)
    public abstract MacAddress getSystemId();

    @Override
    @Property(SYSTEM_ID_PROPERTY)
    @Convert(MacAddressConverter.class)
    public abstract void setSystemId(MacAddress systemId);

    @Override
    @Property(KEY_PROPERTY)
    public abstract int getKey();

    @Override
    @Property(KEY_PROPERTY)
    public abstract void setKey(int key);

    @Override
    @Property(PORT_PRIORITY_PROPERTY)
    public abstract int getPortPriority();

    @Override
    @Property(PORT_PRIORITY_PROPERTY)
    public abstract void setPortPriority(int portPriority);

    @Override
    @Property(PORT_NUMBER_PROPERTY)
    public abstract int getPortNumber();

    @Override
    @Property(PORT_NUMBER_PROPERTY)
    public abstract void setPortNumber(int portNumber);

    @Override
    @Property(STATE_ACTIVE_PROPERTY)
    public abstract boolean isStateActive();

    @Override
    @Property(STATE_ACTIVE_PROPERTY)
    public abstract void setStateActive(boolean stateActive);

    @Override
    @Property(STATE_SHORT_TIMEOUT_PROPERTY)
    public abstract boolean isStateShortTimeout();

    @Override
    @Property(STATE_SHORT_TIMEOUT_PROPERTY)
    public abstract void setStateShortTimeout(boolean stateShortTimeout);

    @Override
    @Property(STATE_AGGREGATABLE_PROPERTY)
    public abstract boolean isStateAggregatable();

    @Override
    @Property(STATE_AGGREGATABLE_PROPERTY)
    public abstract void setStateAggregatable(boolean stateAggregatable);

    @Override
    @Property(STATE_SYNCHRONISED_PROPERTY)
    public abstract boolean isStateSynchronised();

    @Override
    @Property(STATE_SYNCHRONISED_PROPERTY)
    public abstract void setStateSynchronised(boolean stateSynchronised);

    @Override
    @Property(STATE_COLLECTING_PROPERTY)
    public abstract boolean isStateCollecting();

    @Override
    @Property(STATE_COLLECTING_PROPERTY)
    public abstract void setStateCollecting(boolean stateCollecting);

    @Override
    @Property(STATE_DISTRIBUTING_PROPERTY)
    public abstract boolean isStateDistributing();

    @Override
    @Property(STATE_DISTRIBUTING_PROPERTY)
    public abstract void setStateDistributing(boolean stateDistributing);

    @Override
    @Property(STATE_DEFAULTED_PROPERTY)
    public abstract boolean isStateDefaulted();

    @Override
    @Property(STATE_DEFAULTED_PROPERTY)
    public abstract void setStateDefaulted(boolean stateDefaulted);

    @Override
    @Property(STATE_EXPIRED_PROPERTY)
    public abstract boolean isStateExpired();

    @Override
    @Property(STATE_EXPIRED_PROPERTY)
    public abstract void setStateExpired(boolean stateExpired);
}
