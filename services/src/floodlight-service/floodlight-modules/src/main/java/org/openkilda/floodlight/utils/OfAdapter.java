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

package org.openkilda.floodlight.utils;

import static org.projectfloodlight.openflow.protocol.OFVersion.OF_12;
import static org.projectfloodlight.openflow.protocol.OFVersion.OF_15;

import org.openkilda.model.MeterId;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMeterMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.meterband.OFMeterBand;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class OfAdapter {
    public static final OfAdapter INSTANCE = new OfAdapter();

    /**
     * Create series of actions required to reTAG one set of vlan tags to another.
     */
    public List<OFAction> makeVlanReplaceActions(
            OFFactory of, List<Integer> currentVlanStack, List<Integer> desiredVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> desiredIter = desiredVlanStack.iterator();

        final List<OFAction> actions = new ArrayList<>();
        while (currentIter.hasNext() && desiredIter.hasNext()) {
            Integer current = currentIter.next();
            Integer desired = desiredIter.next();
            if (!current.equals(desired)) {
                // remove all extra VLANs
                while (currentIter.hasNext()) {
                    currentIter.next();
                    actions.add(of.actions().popVlan());
                }
                // rewrite existing VLAN stack "head"
                actions.add(setVlanIdAction(of, desired));
                break;
            }
        }

        // remove all extra VLANs (if previous loops ends with lack of desired VLANs
        while (currentIter.hasNext()) {
            currentIter.next();
            actions.add(of.actions().popVlan());
        }

        while (desiredIter.hasNext()) {
            actions.add(of.actions().pushVlan(EthType.VLAN_FRAME));
            actions.add(setVlanIdAction(of, desiredIter.next()));
        }
        return actions;
    }

    /**
     * Add vlanId match into match builder.
     */
    public Match.Builder matchVlanId(OFFactory of, Match.Builder match, int vlanId) {
        if (OF_12.compareTo(of.getVersion()) >= 0) {
            // This is invalid VID mask - it cut of highest bit that indicate presence of VLAN tag on package. But valid
            // mask 0x1FFF lead to rule reject during install attempt on accton based switches.
            // TODO(surabujin): we should use exact match here
            match.setMasked(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId),
                                   OFVlanVidMatch.ofRawVid((short) 0x0FFF));
        } else {
            match.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlan(vlanId));
        }
        return match;
    }

    /**
     * Add VxLAN VNI match into match builder.
     */
    public Match.Builder matchVxLAnVni(OFFactory of, Match.Builder match, long vni) {
        if (OF_12.compareTo(of.getVersion()) >= 0) {
            throw new UnsupportedOperationException(
                    "OF protocol before v1.3 do not support tunnel_id match (VxLAN vni)");
        } else {
            match.setExact(MatchField.TUNNEL_ID, U64.of(vni));
        }
        return match;
    }

    /**
     * Create set vlanId action.
     */
    public OFAction setVlanIdAction(OFFactory of, int vlanId) {
        OFActions actions = of.actions();
        OFVlanVidMatch vlanMatch = of.getVersion() == OFVersion.OF_12
                ? OFVlanVidMatch.ofRawVid((short) vlanId) : OFVlanVidMatch.ofVlan(vlanId);

        return actions.setField(of.oxms().vlanVid(vlanMatch));
    }

    /**
     * Add meter "call" instruction (or action).
     */
    public void makeMeterCall(OFFactory of, MeterId effectiveMeterId,
                              List<OFAction> actionList, List<OFInstruction> instructions) {
        if (of.getVersion().compareTo(OF_15) == 0) {
            actionList.add(of.actions().buildMeter().setMeterId(effectiveMeterId.getValue()).build());
        } else /* OF_13, OF_14 */ {
            instructions.add(of.instructions().buildMeter()
                                     .setMeterId(effectiveMeterId.getValue())
                                     .build());
        }
    }

    /**
     * Encapsulate difference in meter bands access.
     */
    public List<OFMeterBand> getMeterBands(OFMeterMod meterMod) {
        if (meterMod.getVersion().compareTo(OFVersion.OF_14) < 0) {
            return meterMod.getMeters();
        }
        return meterMod.getBands();
    }

    private OfAdapter() { }
}
