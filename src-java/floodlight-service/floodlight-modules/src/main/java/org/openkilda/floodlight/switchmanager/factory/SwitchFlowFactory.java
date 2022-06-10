/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager.factory;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.switchmanager.factory.generator.server42.Server42IslRttInputFlowGenerator;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class SwitchFlowFactory implements IService {
    private KildaCore kildaCore;

    @Override
    public void setup(FloodlightModuleContext context) {
        kildaCore = context.getServiceImpl(KildaCore.class);
    }

    /**
     * Get Server 42 ISL RTT input flow generator.
     */
    public Server42IslRttInputFlowGenerator getServer42IslRttInputFlowGenerator(int server42Port, int islPort) {
        return Server42IslRttInputFlowGenerator.builder()
                .kildaCore(kildaCore)
                .server42Port(server42Port)
                .islPort(islPort)
                .build();
    }
}
