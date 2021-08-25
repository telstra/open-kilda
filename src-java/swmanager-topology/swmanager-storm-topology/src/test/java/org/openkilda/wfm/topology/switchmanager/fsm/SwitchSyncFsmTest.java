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

package org.openkilda.wfm.topology.switchmanager.fsm;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.command.switches.SwitchValidateRequest;
import org.openkilda.messaging.info.switches.MeterInfoEntry;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.model.ValidateGroupsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateLogicalPortsResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateMetersResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidateRulesResult;
import org.openkilda.wfm.topology.switchmanager.model.ValidationResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SwitchSyncFsmTest {

    private static final SwitchId SWITCH_ID = new SwitchId(1);
    private static final long COOKIE_1 = 1;
    private static final long COOKIE_2 = 2;
    private static final long COOKIE_3 = 3;
    private static final long METER_ID_1 = 4;
    private static final long METER_ID_2 = 5;
    private static final long METER_ID_3 = 6;
    private static final ValidateGroupsResult EMPTY_VALIDATE_GROUPS_RESULT = new ValidateGroupsResult(
            newArrayList(), newArrayList(), newArrayList(), newArrayList());
    private static final ValidateRulesResult EMPTY_VALIDATE_RULES_RESULT = new ValidateRulesResult(
            newHashSet(), newHashSet(), newHashSet(), newHashSet());
    private static final ValidateLogicalPortsResult EMPTY_LOGICAL_PORTS_RESULT = new ValidateLogicalPortsResult(
            newArrayList(), newArrayList(), newArrayList(), newArrayList());

    @Test
    public void getModifyDefaultMetersWithMissingRulesTest() {
        ValidateRulesResult validateRulesResult = new ValidateRulesResult(
                newHashSet(COOKIE_1), newHashSet(), newHashSet(), newHashSet(COOKIE_2));

        ArrayList<MeterInfoEntry> misconfiguredMeters = newArrayList(
                MeterInfoEntry.builder().cookie(COOKIE_1).meterId(METER_ID_1).build(),
                MeterInfoEntry.builder().cookie(COOKIE_2).meterId(METER_ID_2).build(),
                MeterInfoEntry.builder().cookie(COOKIE_3).meterId(METER_ID_3).build());

        ValidateMetersResult validateMetersResult = new ValidateMetersResult(
                newArrayList(), misconfiguredMeters, newArrayList(), newArrayList());

        ValidationResult validationResult = new ValidationResult(new ArrayList<>(), true,
                validateRulesResult, validateMetersResult, EMPTY_VALIDATE_GROUPS_RESULT, EMPTY_LOGICAL_PORTS_RESULT);

        SwitchSyncFsm fsm = new SwitchSyncFsm(null, null, null,
                new SwitchValidateRequest(SWITCH_ID, true, true, true), validationResult);

        List<Long> modifyMeters = fsm.getModifyDefaultMeters();
        assertEquals(1, modifyMeters.size());
        assertEquals(METER_ID_3, modifyMeters.get(0).longValue());
    }

    @Test
    public void getModifyDefaultMetersWithFlowMeters() {
        ArrayList<MeterInfoEntry> misconfiguredMeters = newArrayList(
                MeterInfoEntry.builder().cookie(COOKIE_1).meterId((long) MeterId.MIN_FLOW_METER_ID).build());

        ValidateMetersResult validateMetersResult = new ValidateMetersResult(
                newArrayList(), misconfiguredMeters, newArrayList(), newArrayList());

        ValidationResult validationResult = new ValidationResult(new ArrayList<>(), true,
                EMPTY_VALIDATE_RULES_RESULT, validateMetersResult, EMPTY_VALIDATE_GROUPS_RESULT,
                EMPTY_LOGICAL_PORTS_RESULT);

        SwitchSyncFsm fsm = new SwitchSyncFsm(null, null, null,
                new SwitchValidateRequest(SWITCH_ID, true, true, true), validationResult);

        assertTrue(fsm.getModifyDefaultMeters().isEmpty());
    }
}
