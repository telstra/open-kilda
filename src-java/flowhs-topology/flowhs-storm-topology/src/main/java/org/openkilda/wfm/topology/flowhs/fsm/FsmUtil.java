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

package org.openkilda.wfm.topology.flowhs.fsm;

import org.openkilda.wfm.share.metrics.MeterRegistryHolder;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.LongTaskTimer.Sample;
import org.squirrelframework.foundation.fsm.StateMachine;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class FsmUtil {
    /**
     * Add FSM execution time metric.
     */
    public static void addExecutionTimeMeter(
            StateMachine<?, ?, ?, ?> subject, Supplier<Boolean> resultSupplier) {
        MeterRegistryHolder.getRegistry().ifPresent(registry -> {
            // TODO(surabujin): should the name be FSM specific?
            Sample sample = LongTaskTimer.builder("fsm.active_execution")
                    .register(registry)
                    .start();
            subject.addTerminateListener(e -> {
                long duration = sample.stop();

                String name = "fsm.execution." + Optional.ofNullable(resultSupplier.get())
                        .map(result -> result ? "success" : "failed")
                        .orElse("undefined");
                registry.timer(name)
                        .record(duration, TimeUnit.NANOSECONDS);
            });
        });
    }

    private FsmUtil() {
        // hide public constructor
    }
}
