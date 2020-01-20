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
import org.openkilda.floodlight.pathverification.IPathVerificationService;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.IService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManagerConfig;
import org.openkilda.floodlight.switchmanager.factory.generator.BfdCatchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.DropFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.DropLoopFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.RoundTripLatencyFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.SwitchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.TablePassThroughDefaultFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.UnicastVerificationVxlanRuleGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.VerificationFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpInputPreDropFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressOneSwitchFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpPostIngressVxlanFlowGenerator;
import org.openkilda.floodlight.switchmanager.factory.generator.lldp.LldpTransitFlowGenerator;

import net.floodlightcontroller.core.module.FloodlightModuleContext;

public class SwitchFlowFactory implements IService {
    private SwitchManagerConfig config;
    private FeatureDetectorService featureDetectorService;
    private KildaCore kildaCore;
    private String verificationBcastPacketDst;

    @Override
    public void setup(FloodlightModuleContext context) {
        ISwitchManager switchManager = context.getServiceImpl(ISwitchManager.class);
        config = switchManager.getSwitchManagerConfig();
        featureDetectorService = context.getServiceImpl(FeatureDetectorService.class);
        kildaCore = context.getServiceImpl(KildaCore.class);

        verificationBcastPacketDst =
                context.getServiceImpl(IPathVerificationService.class).getConfig().getVerificationBcastPacketDst();
    }

    /**
     * Get default drop switch flow generator.
     */
    public SwitchFlowGenerator getDropFlowGenerator(long cookie, int tableId) {
        return DropFlowGenerator.builder()
                .cookie(cookie)
                .tableId(tableId)
                .build();
    }

    /**
     * Get verification switch flow generator.
     */
    public SwitchFlowGenerator getVerificationFlow(boolean broadcast) {
        return VerificationFlowGenerator.builder()
                .broadcast(broadcast)
                .config(config)
                .featureDetectorService(featureDetectorService)
                .kildaCore(kildaCore)
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get drop loop switch flow generator.
     */
    public SwitchFlowGenerator getDropLoopFlowGenerator() {
        return DropLoopFlowGenerator.builder()
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get BFD catch switch flow generator.
     */
    public SwitchFlowGenerator getBfdCatchFlowGenerator() {
        return BfdCatchFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get round trip latency switch flow generator.
     */
    public SwitchFlowGenerator getRoundTripLatencyFlowGenerator() {
        return RoundTripLatencyFlowGenerator.builder()
                .featureDetectorService(featureDetectorService)
                .verificationBcastPacketDst(verificationBcastPacketDst)
                .build();
    }

    /**
     * Get unicast verification VXLAN switch flow generator.
     */
    public SwitchFlowGenerator getUnicastVerificationVxlanFlowGenerator() {
        return UnicastVerificationVxlanRuleGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .kildaCore(kildaCore)
                .build();
    }

    /**
     * Get table pass through default switch flow generator.
     */
    public SwitchFlowGenerator getTablePassThroughDefaultFlowGenerator(long cookie, int goToTableId, int tableId) {
        return TablePassThroughDefaultFlowGenerator.builder()
                .cookie(cookie)
                .goToTableId(goToTableId)
                .tableId(tableId)
                .build();
    }

    /**
     * Get LLDP input pre drop switch flow generator.
     */
    public SwitchFlowGenerator getLldpInputPreDropFlowGenerator() {
        return LldpInputPreDropFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP ingress switch flow generator.
     */
    public SwitchFlowGenerator getLldpIngressFlowGenerator() {
        return LldpIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP post ingress switch flow generator.
     */
    public SwitchFlowGenerator getLldpPostIngressFlowGenerator() {
        return LldpPostIngressFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP post ingress VXLAN switch flow generator.
     */
    public SwitchFlowGenerator getLldpPostIngressVxlanFlowGenerator() {
        return LldpPostIngressVxlanFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP one switch post ingress switch flow generator.
     */
    public SwitchFlowGenerator getLldpPostIngressOneSwitchFlowGenerator() {
        return LldpPostIngressOneSwitchFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }

    /**
     * Get LLDP transit switch flow generator.
     */
    public SwitchFlowGenerator getLldpTransitFlowGenerator() {
        return LldpTransitFlowGenerator.builder()
                .config(config)
                .featureDetectorService(featureDetectorService)
                .build();
    }
}
