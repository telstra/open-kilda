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

package org.openkilda.rulemanager.utils;

import org.openkilda.rulemanager.Field;
import org.openkilda.rulemanager.action.Action;
import org.openkilda.rulemanager.action.PopVlanAction;
import org.openkilda.rulemanager.action.PushVlanAction;
import org.openkilda.rulemanager.action.SetFieldAction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Utils {

    private Utils() {
    }

    /**
     * Create series of actions required to reTAG one set of vlan tags to another.
     */
    public static List<Action> makeVlanReplaceActions(List<Integer> currentVlanStack, List<Integer> targetVlanStack) {
        Iterator<Integer> currentIter = currentVlanStack.iterator();
        Iterator<Integer> targetIter = targetVlanStack.iterator();

        final List<Action> actions = new ArrayList<>();
        while (currentIter.hasNext() && targetIter.hasNext()) {
            Integer current = currentIter.next();
            Integer target = targetIter.next();
            if (current == null || target == null) {
                throw new IllegalArgumentException(
                        "Null elements are not allowed inside currentVlanStack and targetVlanStack arguments");
            }

            if (!current.equals(target)) {
                // remove all extra VLANs
                while (currentIter.hasNext()) {
                    currentIter.next();
                    actions.add(new PopVlanAction());
                }
                // rewrite existing VLAN stack "head"
                actions.add(SetFieldAction.builder().field(Field.VLAN_VID).value(target).build());
                break;
            }
        }

        // remove all extra VLANs (if previous loops ends with lack of target VLANs
        while (currentIter.hasNext()) {
            currentIter.next();
            actions.add(new PopVlanAction());
        }

        while (targetIter.hasNext()) {
            actions.add(PushVlanAction.builder().vlanId(targetIter.next().shortValue()).build());
        }
        return actions;
    }
}
