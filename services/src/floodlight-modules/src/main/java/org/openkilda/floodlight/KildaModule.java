/*
 * Copyright 2017 Telstra Open Source
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openkilda.floodlight;

import org.openkilda.floodlight.kafka.KafkaMessageProducer;
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.FlowVerificationService;
import org.openkilda.floodlight.service.batch.OFBatchService;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import java.util.Collection;
import java.util.Map;

public class KildaModule implements IFloodlightModule {
    OFBatchService ofBatchService = new OFBatchService();
    FlowVerificationService flowVerificationService = new FlowVerificationService();

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return ImmutableList.of(
                OFBatchService.class,
                FlowVerificationService.class);
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return ImmutableMap.of(
                OFBatchService.class, ofBatchService,
                FlowVerificationService.class, flowVerificationService);
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return ImmutableList.of(
                IFloodlightProviderService.class,
                IOFSwitchService.class,
                IThreadPoolService.class,
                KafkaMessageProducer.class,
                IPathVerificationService.class);
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {

    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        ofBatchService.init(context);
        flowVerificationService.init(context);
    }
}
