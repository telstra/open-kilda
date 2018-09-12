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

package org.openkilda.floodlight.service;

import org.openkilda.floodlight.KildaCore;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.jmx.JmxReporter;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class MetricService implements IService {
    private final MetricRegistry registry = new MetricRegistry();
    private final JmxReporter reporter = JmxReporter.forRegistry(registry).build();

    public Timer timer(Class<?> klass, String... nameChunks) {
        return timer(MetricRegistry.name(klass, nameChunks));
    }

    public Timer timer(String namePrefix, String... nameChunks) {
        return timer(MetricRegistry.name(namePrefix, nameChunks));
    }

    private Timer timer(String name) {
        return registry.timer(name);
    }

    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        KildaCore kildaCore = moduleContext.getServiceImpl(KildaCore.class);
        if (kildaCore.isTestingMode()) {
            setupReporting();
        }
    }

    private void setupReporting() {
        reporter.start();
    }
}
