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

package org.openkilda.pce.model;

import org.openkilda.model.SwitchId;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Getter
@EqualsAndHashCode(of = "switchId")
@ToString(exclude = {"incomingLinks", "outgoingLinks", "backupIncomingLinks", "backupOutgoingLinks"})
public class Node {
    private final SwitchId switchId;
    private final String pop;

    private Set<Edge> incomingLinks = new HashSet<>();
    private Set<Edge> outgoingLinks = new HashSet<>();

    private Set<Edge> backupIncomingLinks;
    private Set<Edge> backupOutgoingLinks;

    private int diversityGroupUseCounter;

    public void increaseDiversityGroupUseCounter() {
        diversityGroupUseCounter++;
    }

    /**
     * Constructs {@link Node} instance with the passed values.
     *
     * @param switchId the {@link SwitchId} instance.
     * @param pop the switch's pop.
     */
    public Node(@NonNull SwitchId switchId, String pop) {
        this.switchId = switchId;
        this.pop = pop;
    }

    /**
     * Remove node.
     */
    public void remove() {
        backupIncomingLinks = incomingLinks;
        backupOutgoingLinks = outgoingLinks;

        incomingLinks = new HashSet<>();
        outgoingLinks = new HashSet<>();
    }

    /**
     * Restore node.
     */
    public void restore() {
        if (backupIncomingLinks != null && !backupIncomingLinks.isEmpty()
                && backupOutgoingLinks != null && !backupOutgoingLinks.isEmpty()) {
            incomingLinks = backupIncomingLinks;
            outgoingLinks = backupOutgoingLinks;
        }
    }
}
