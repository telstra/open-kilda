package org.bitbucket.openkilda.pce.provider;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Rule;

public interface EncapsulationScheme {
    Rule buildIngressInstallationFlowRule(Flow flow);

    Rule buildIngressUpdateFlowRule(Flow flow);

    Rule buildIngressDeletionFlowRule(Flow flow);

    Rule buildEgressInstallationFlowRule(Flow flow);

    Rule buildEgressUpdateFlowRule(Flow flow);

    Rule buildEgressDeletionFlowRule(Flow flow);

    Rule buildTransitInstallationFlowRule(Flow flow);

    Rule buildTransitUpdateFlowRule(Flow flow);

    Rule buildTransitDeletionFlowRule(Flow flow);

    Rule buildInstallationMeter(Flow flow);

    Rule buildUpdateMeter(Flow flow);

    Rule buildDeletionMeter(Flow flow);

    Rule buildSuspendIngressFlowRule(Flow flow);

    Rule buildSuspendEgressFlowRule(Flow flow);

    default Rule buildResumeIngressFlowRule(Flow flow) {
        return buildIngressUpdateFlowRule(flow);
    }

    default Rule buildResumeEgressFlowRule(Flow flow) {
        return buildEgressUpdateFlowRule(flow);
    }
}
