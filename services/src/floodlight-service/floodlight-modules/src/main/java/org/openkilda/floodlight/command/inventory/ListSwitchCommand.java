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

package org.openkilda.floodlight.command.inventory;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.switches.ListSwitchResponse;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.core.internal.FloodlightProvider;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ListSwitchCommand extends Command {
    protected static final Logger log = LoggerFactory.getLogger(ListSwitchCommand.class);

    private final IOFSwitchService ofSwitchService;
    private final IKafkaProducerService kafkaProducer;
    private final KafkaUtilityService kafkaUtility;

    public ListSwitchCommand(CommandContext context) {
        super(context);
        FloodlightModuleContext moduleContext = context.getModuleContext();
        ofSwitchService = moduleContext.getServiceImpl(IOFSwitchService.class);
        kafkaProducer = moduleContext.getServiceImpl(IKafkaProducerService.class);
        kafkaUtility = context.getModuleContext().getServiceImpl(KafkaUtilityService.class);
    }

    @Override
    public Command call() {
        log.trace("Incoming list switch request");
        List<SwitchId> switchIds = ofSwitchService.getAllSwitchDpids().stream()
                .map(it -> new SwitchId(it.getLong()))
                .collect(Collectors.toList());
        String controllerId =
                getContext().getModuleContext().getConfigParams(FloodlightProvider.class).get("controllerId");
        InfoMessage response = getContext().makeInfoMessage(new ListSwitchResponse(switchIds, controllerId));
        kafkaProducer.sendMessageAndTrack(kafkaUtility.getKafkaChannel().getFlStatsSwitchesPrivRegionTopic(), response);
        log.trace("List switch response send");
        return null;
    }
}
