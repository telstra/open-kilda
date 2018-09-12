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

package org.openkilda.floodlight;

import org.openkilda.floodlight.config.provider.FloodlightModuleConfigurationProvider;
import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.MetricService;
import org.openkilda.floodlight.service.of.InputService;
import org.openkilda.floodlight.utils.CommandContextFactory;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This module is a container for all base kilda services. The main mark of such service - lack of dependencies on other
 * kilda services. I.e. they have dependencies only on base FL services.
 */
public class KildaCore implements IFloodlightModule, IFloodlightService {
    private KildaCoreConfig config;
    private final CommandContextFactory commandContextFactory = new CommandContextFactory();

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                KildaCore.class,
                MetricService.class,
                CommandProcessorService.class,
                InputService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        HashMap<Class<? extends IFloodlightService>, IFloodlightService> services = new HashMap<>();
        services.put(KildaCore.class, this);
        services.put(MetricService.class, new MetricService());
        services.put(CommandProcessorService.class, new CommandProcessorService(this, commandContextFactory));
        services.put(InputService.class, new InputService(commandContextFactory));
        return services;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                IThreadPoolService.class,
                IFloodlightProviderService.class);
    }

    @Override
    public void init(FloodlightModuleContext moduleContext) {
        FloodlightModuleConfigurationProvider provider = FloodlightModuleConfigurationProvider.of(moduleContext, this);
        config = provider.getConfiguration(KildaCoreConfig.class);
    }

    @Override
    public void startUp(FloodlightModuleContext moduleContext) {
        commandContextFactory.init(moduleContext);

        moduleContext.getServiceImpl(MetricService.class).setup(moduleContext);
        moduleContext.getServiceImpl(CommandProcessorService.class).setup(moduleContext);
        moduleContext.getServiceImpl(InputService.class).setup(moduleContext);
    }

    public KildaCoreConfig getConfig() {
        return config;
    }

    public boolean isTestingMode() {
        return config.isTestingMode();
    }
}
