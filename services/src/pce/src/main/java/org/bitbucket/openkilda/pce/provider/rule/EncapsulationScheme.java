package org.bitbucket.openkilda.pce.provider.rule;

import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.rule.FlowInstall;

public interface EncapsulationScheme {
    FlowInstall buildIngressInstallationFlowRule(Flow flow);

    FlowInstall buildIngressUpdateFlowRule(Flow flow);

    FlowInstall buildIngressDeletionFlowRule(Flow flow);

    FlowInstall buildEgressInstallationFlowRule(Flow flow);

    FlowInstall buildEgressUpdateFlowRule(Flow flow);

    FlowInstall buildEgressDeletionFlowRule(Flow flow);

    FlowInstall buildTransitInstallationFlowRule(Flow flow);

    FlowInstall buildTransitUpdateFlowRule(Flow flow);

    FlowInstall buildTransitDeletionFlowRule(Flow flow);

    FlowInstall buildInstallationMeter(Flow flow);

    FlowInstall buildUpdateMeter(Flow flow);

    FlowInstall buildDeletionMeter(Flow flow);

    FlowInstall buildSuspendIngressFlowRule(Flow flow);

    FlowInstall buildSuspendEgressFlowRule(Flow flow);

    default FlowInstall buildResumeIngressFlowRule(Flow flow) {
        return buildIngressUpdateFlowRule(flow);
    }

    default FlowInstall buildResumeEgressFlowRule(Flow flow) {
        return buildEgressUpdateFlowRule(flow);
    }
}
