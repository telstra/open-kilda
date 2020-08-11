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

package org.openkilda.floodlight.command.statistics;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.statistics.IStatisticsService;
import org.openkilda.messaging.command.stats.StatsRequest;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StatsCommand extends Command {
    private final StatsRequest data;
    private final Set<DatapathId> scope;

    public StatsCommand(CommandContext context, StatsRequest data) {
        this(context, data, null);
    }

    public StatsCommand(CommandContext context, StatsRequest data, Set<SwitchId> scope) {
        super(context);
        this.data = data;
        this.scope = scope != null ? mapDatapathId(scope) : null;
    }

    @Override
    public Command call() {
        FloodlightModuleContext moduleContext = getContext().getModuleContext();
        IStatisticsService statsService = moduleContext.getServiceImpl(IStatisticsService.class);
        if (scope != null) {
            statsService.processStatistics(moduleContext, mapDatapathId(data.getExcludeSwitchIds()), scope);
        } else {
            statsService.processStatistics(moduleContext, mapDatapathId(data.getExcludeSwitchIds()));
        }
        return null;
    }

    private static Set<DatapathId> mapDatapathId(List<SwitchId> raw) {
        return mapDatapathId(new HashSet<>(Optional.ofNullable(raw).orElse(Collections.emptyList())));
    }

    private static Set<DatapathId> mapDatapathId(Set<SwitchId> raw) {
        return Optional.ofNullable(raw).orElse(Collections.emptySet()).stream()
                .map(it -> DatapathId.of(it.toLong()))
                .collect(Collectors.toSet());
    }
}
