/* Copyright 2021 Telstra Open Source
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

import org.openkilda.model.Speaker;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchConnect.SwitchConnectData;
import org.openkilda.model.SwitchConnectMode;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.InstantStringConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchConnectModeConverter;

import com.syncleus.ferma.annotations.Property;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Iterator;

public abstract class SwitchConnectFrame extends KildaBaseEdgeFrame implements SwitchConnectData {
    public static final String FRAME_LABEL = "switch_connect";

    public static final String MODE_PROPERTY = "mode";
    public static final String MASTER_PROPERTY = "master";
    public static final String CONNECTED_AT_PROPERTY = "connected_at";
    public static final String SWITCH_ADDRESS_PROPERTY = "switch_address";
    public static final String SWITCH_ADDRESS_PORT_PROPERTY = "switch_address_port";
    public static final String SPEAKER_ADDRESS_PROPERTY = "speaker_address";
    public static final String SPEAKER_ADDRESS_PORT_PROPERTY = "speaker_address_port";

    private Switch owner;
    private Speaker speaker;

    @Override
    public SwitchId getOwnerSwitchId() {
        Switch owner = getOwner();
        return owner != null ? owner.getSwitchId() : null;
    }

    @Override
    public Speaker getSpeaker() {
        if (speaker == null) {
            Iterator<? extends SpeakerFrame> speakerIterator = traverse(GraphTraversal::inV)
                    .frameExplicit(SpeakerFrame.class);
            if (speakerIterator.hasNext()) {
                speaker = new Speaker(speakerIterator.next());
            } else {
                throw new IllegalStateException(String.format(
                        "Unable to load outgoing vertex for edge %s", this));
            }
        }
        return speaker;
    }

    @Override
    public void setSpeaker(Speaker speaker) {
        throw new UnsupportedOperationException("Changing edge's destination is not supported");
    }

    @Override
    public Switch getOwner() {
        if (owner == null) {
            Iterator<? extends SwitchFrame> ownerIterator = traverse(GraphTraversal::outV)
                    .frameExplicit(SwitchFrame.class);
            if (ownerIterator.hasNext()) {
                owner = new Switch(ownerIterator.next());
            } else {
                throw new IllegalStateException(String.format(
                        "Unable to load incoming vertex for edge %s", this));
            }
        }
        return owner;
    }

    @Override
    public void setOwner(Switch owner) {
        throw new UnsupportedOperationException("Changing edge's destination is not supported");
    }

    @Override
    @Property(MODE_PROPERTY)
    @Convert(SwitchConnectModeConverter.class)
    public abstract SwitchConnectMode getMode();

    @Override
    @Property(MODE_PROPERTY)
    @Convert(SwitchConnectModeConverter.class)
    public abstract void setMode(SwitchConnectMode mode);

    @Override
    @Property(MASTER_PROPERTY)
    public abstract boolean isMaster();

    @Override
    @Property(MASTER_PROPERTY)
    public abstract void setMaster(boolean isMaster);

    @Override
    @Property(CONNECTED_AT_PROPERTY)
    @Convert(InstantStringConverter.class)
    public abstract Instant getConnectedAt();

    @Override
    @Property(CONNECTED_AT_PROPERTY)
    @Convert(InstantStringConverter.class)
    public abstract void setConnectedAt(Instant connectedAt);

    @Override
    public InetSocketAddress getSwitchAddress() {
        return getAddress(SWITCH_ADDRESS_PROPERTY, SWITCH_ADDRESS_PORT_PROPERTY);
    }

    @Override
    public void setSwitchAddress(InetSocketAddress address) {
        setAddress(SWITCH_ADDRESS_PROPERTY, SWITCH_ADDRESS_PORT_PROPERTY, address);
    }

    @Override
    public InetSocketAddress getSpeakerAddress() {
        return getAddress(SPEAKER_ADDRESS_PROPERTY, SPEAKER_ADDRESS_PORT_PROPERTY);
    }

    @Override
    public void setSpeakerAddress(InetSocketAddress address) {
        setAddress(SPEAKER_ADDRESS_PROPERTY, SPEAKER_ADDRESS_PORT_PROPERTY, address);
    }

    private InetSocketAddress getAddress(String addressProperty, String portProperty) {
        String address = getProperty(addressProperty);
        if (address == null) {
            return null;
        }

        int port = getAddressPort(portProperty);
        try {
            return new InetSocketAddress(InetAddress.getByName(address), port);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(String.format(
                    "Switch connect relation stores invalid address %s:%d (fields %s / %s)",
                    address, port, addressProperty, portProperty));
        }
    }

    private void setAddress(String addressProperty, String portProperty, InetSocketAddress address) {
        if (address == null) {
            setProperty(addressProperty, null);
            setProperty(portProperty, null);
            return;
        }

        setProperty(addressProperty, address.getAddress().getHostAddress());
        setProperty(portProperty, address.getPort());
    }

    private int getAddressPort(String property) {
        Number value = getProperty(property);
        if (value == null) {
            return 0;
        }
        return value.intValue();
    }
}
