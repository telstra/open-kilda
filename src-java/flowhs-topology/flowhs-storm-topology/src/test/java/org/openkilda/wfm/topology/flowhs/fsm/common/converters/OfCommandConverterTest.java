/* Copyright 2022 Telstra Open Source
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

package org.openkilda.wfm.topology.flowhs.fsm.common.converters;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;

import org.openkilda.floodlight.api.request.rulemanager.FlowCommand;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.rulemanager.FlowSpeakerData;

import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class OfCommandConverterTest {
    @Test
    public void shouldRemoveExcessDependencies() {
        // given
        FlowSpeakerData first = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(singleton(UUID.randomUUID())).build();
        FlowSpeakerData second = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(singleton(first.getUuid())).build();
        FlowSpeakerData third = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(asList(first.getUuid(), second.getUuid(), UUID.randomUUID())).build();
        List<OfCommand> before = asList(new FlowCommand(first), new FlowCommand(second), new FlowCommand(third));

        // when
        List<OfCommand> after = OfCommandConverter.INSTANCE.removeExcessDependencies(before);
        Map<UUID, FlowSpeakerData> dataAfter = after.stream()
                .map(command -> ((FlowCommand) command).getData())
                .collect(Collectors.toMap(FlowSpeakerData::getUuid, Function.identity()));

        FlowSpeakerData firstAfter = dataAfter.get(first.getUuid());
        FlowSpeakerData secondAfter = dataAfter.get(second.getUuid());
        FlowSpeakerData thirdAfter = dataAfter.get(third.getUuid());

        // then
        assertThat(firstAfter.getDependsOn(), is(empty()));
        assertThat(secondAfter.getDependsOn(), contains(equalTo(firstAfter.getUuid())));
        assertThat(thirdAfter.getDependsOn(),
                containsInAnyOrder(equalTo(firstAfter.getUuid()), equalTo(secondAfter.getUuid())));
    }

    @Test
    public void shouldReverseDependencies() {
        // given
        FlowSpeakerData first = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(singleton(UUID.randomUUID())).build();
        FlowSpeakerData second = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(singleton(first.getUuid())).build();
        FlowSpeakerData third = FlowSpeakerData.builder().uuid(UUID.randomUUID())
                .dependsOn(asList(first.getUuid(), second.getUuid(), UUID.randomUUID())).build();
        List<OfCommand> before = asList(new FlowCommand(first), new FlowCommand(second), new FlowCommand(third));

        // when
        List<OfCommand> after = OfCommandConverter.INSTANCE.reverseDependenciesForDeletion(before);
        Map<UUID, FlowSpeakerData> dataAfter = after.stream()
                .map(command -> ((FlowCommand) command).getData())
                .collect(Collectors.toMap(FlowSpeakerData::getUuid, Function.identity()));

        // then
        assertThat(dataAfter.keySet(), hasSize(3));
        assertThat(dataAfter.get(first.getUuid()).getDependsOn(),
                containsInAnyOrder(equalTo(second.getUuid()), equalTo(third.getUuid())));
        assertThat(dataAfter.get(second.getUuid()).getDependsOn(),
                containsInAnyOrder(equalTo(third.getUuid())));
        assertThat(dataAfter.get(third.getUuid()).getDependsOn(), empty());
    }
}
