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

package org.openkilda.persistence.hibernate.entities.history;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.MeterId;
import org.openkilda.model.MirrorPointStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.FlowSegmentCookie;
import org.openkilda.model.history.FlowEventDump.FlowEventDumpData;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.entities.JsonPayloadBase;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.hibernate.annotations.Type;

import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

@Getter
@Setter
@Entity(name = "FlowEventDump")
@Table(name = "flow_event_dump")
public class HibernateFlowEventDump extends EntityBase implements FlowEventDumpData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kind")
    private String type;

    @Delegate
    @Type(type = "json")
    @Column(name = "unstructured", columnDefinition = "json")
    private FlowEventDumpUnstructured unstructured;

    @Transient
    private String taskId;

    @ManyToOne
    @JoinColumn(name = "flow_event_id")
    private HibernateFlowEvent event;

    public HibernateFlowEventDump() {
        unstructured = new FlowEventDumpUnstructured();
    }

    @Override
    public String getTaskId() {
        if (taskId != null) {
            return taskId;
        }
        if (event != null) {
            return event.getTaskId();
        }
        return null;
    }

    @Override
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @Override
    public Boolean isAllocateProtectedPath() {
        return getUnstructured().getAllocateProtectedPath();
    }

    @Override
    public Boolean isPinned() {
        return getUnstructured().getPinned();
    }

    @Override
    public Boolean isPeriodicPings() {
        return getUnstructured().getPeriodicPings();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class FlowEventDumpUnstructured extends JsonPayloadBase {
        private String flowId;
        private long bandwidth;
        private boolean ignoreBandwidth;
        private boolean strictBandwidth;
        private FlowSegmentCookie forwardCookie;
        private FlowSegmentCookie reverseCookie;

        private SwitchId sourceSwitch;
        private int sourcePort;
        private int sourceVlan;
        private Integer sourceInnerVlan;

        private SwitchId destinationSwitch;
        private int destinationPort;
        private int destinationVlan;
        private Integer destinationInnerVlan;

        private MeterId forwardMeterId;
        private String forwardPath;
        private FlowPathStatus forwardStatus;

        private MeterId reverseMeterId;
        private String reversePath;
        private FlowPathStatus reverseStatus;

        private String diverseGroupId;
        private String affinityGroupId;
        private Boolean allocateProtectedPath;
        private Boolean pinned;
        private Boolean periodicPings;
        private FlowEncapsulationType encapsulationType;
        private PathComputationStrategy pathComputationStrategy;
        private Long maxLatency;
        private Long maxLatencyTier2;
        private Integer priority;
        private List<MirrorPointStatus> mirrorPointStatuses;

        private SwitchId loopSwitchId;
    }
}
