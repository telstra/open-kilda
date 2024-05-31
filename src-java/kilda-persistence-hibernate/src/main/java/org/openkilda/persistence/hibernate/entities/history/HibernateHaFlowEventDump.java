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

package org.openkilda.persistence.hibernate.entities.history;

import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathComputationStrategy;
import org.openkilda.model.history.DumpType;
import org.openkilda.model.history.HaFlowEventDump.HaFlowEventDumpData;
import org.openkilda.model.history.HaFlowEventDump.HaFlowPathDump;
import org.openkilda.model.history.HaFlowEventDump.HaSubFlowDumpWrapper;
import org.openkilda.persistence.hibernate.entities.EntityBase;
import org.openkilda.persistence.hibernate.entities.JsonPayloadBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Delegate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity(name = "HaFlowEventDump")
@Table(name = "ha_flow_event_dump")
public class HibernateHaFlowEventDump extends EntityBase implements HaFlowEventDumpData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ha_flow_event_id")
    private HibernateHaFlowEvent haFlowEvent;

    @Column(name = "event_dump_json", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    @Delegate
    private HibernateHaFlowEventDumpWrapper eventDumpWrapper = new HibernateHaFlowEventDumpWrapper();

    @Getter
    @Setter
    public static class HibernateHaFlowEventDumpWrapper extends JsonPayloadBase {
        private DumpType dumpType;
        private String taskId;
        private String haFlowId;
        private String affinityGroupId;
        private Boolean allocateProtectedPath;
        private String description;
        private String diverseGroupId;
        private FlowEncapsulationType encapsulationType;
        private String flowTimeCreate;
        private String flowTimeModify;
        private Boolean ignoreBandwidth;
        private Long maxLatency;
        private Long maxLatencyTier2;
        private Long maximumBandwidth;
        private PathComputationStrategy pathComputationStrategy;
        private Boolean periodicPings;
        private Boolean pinned;
        private Integer priority;
        private Integer sharedInnerVlan;
        private Integer sharedOuterVlan;
        private Integer sharedPort;
        private String sharedSwitchId;
        private FlowStatus status;
        private String statusInfo;
        private Boolean strictBandwidth;
        private HaSubFlowDumpWrapper haSubFlows;
        private HaFlowPathDump forwardPath;
        private HaFlowPathDump reversePath;
        private HaFlowPathDump protectedForwardPath;
        private HaFlowPathDump protectedReversePath;
    }
}
