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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class SpeakerCommandProcessor {
    @Getter
    private final FloodlightModuleContext moduleContext;

    public SpeakerCommandProcessor(@NonNull FloodlightModuleContext moduleContext) {
        this.moduleContext = moduleContext;
    }

    /**
     * Manage command execution and make basic error reporting in case of exceptional completion.
     */
    public <T extends SpeakerCommandReport> void process(SpeakerCommand<T> command, String kafkaKey) {
        log.debug("Process speaker command: {}", command);
        execute(command)
                .whenComplete((response, error) -> handleResult(response, error, kafkaKey));
    }

    public <T extends SpeakerCommandReport> CompletableFuture<T> chain(SpeakerCommand<T> command) {
        return execute(command);
    }

    private <T extends SpeakerCommandReport> CompletableFuture<T> execute(SpeakerCommand<T> command) {
        return command.execute(this);
    }

    private void handleResult(SpeakerCommandReport report, Throwable error, String kafkaKey) {
        if (error == null) {
            handleResult(report, kafkaKey);
        } else {
            handleResult(error);
        }
    }

    private void handleResult(SpeakerCommandReport report, String kafkaKey) {
        if (report instanceof SpeakerCommandRemoteReport) {
            handleResult((SpeakerCommandRemoteReport) report, kafkaKey);
        } else {
            ensureSuccess(report);
        }
    }

    private void handleResult(SpeakerCommandRemoteReport report, String kafkaKey) {
        KafkaUtilityService kafkaUtil = moduleContext.getServiceImpl(KafkaUtilityService.class);
        IKafkaProducerService kafkaProducer = moduleContext.getServiceImpl(IKafkaProducerService.class);
        report.reply(kafkaUtil.getKafkaChannel(), kafkaProducer, kafkaKey);
    }

    private void handleResult(Throwable error) {
        log.error("Error occurred while processing speaker command", error);
    }

    private void ensureSuccess(SpeakerCommandReport report) {
        try {
            report.raiseError();
        } catch (Exception e) {
            log.error(String.format("Command %s failed with error: %s", report.getCommand(), e), e);
        }
    }
}
