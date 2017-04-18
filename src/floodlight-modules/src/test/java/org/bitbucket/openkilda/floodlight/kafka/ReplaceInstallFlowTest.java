package org.bitbucket.openkilda.floodlight.kafka;

import net.floodlightcontroller.core.SwitchDescription;
import org.bitbucket.openkilda.floodlight.message.command.encapsulation.ReplaceSchemeOutputCommands;
import org.bitbucket.openkilda.floodlight.switchmanager.ISwitchManager;

/**
 * Created by atopilin on 14/04/2017.
 */
public class ReplaceInstallFlowTest extends PushInstallFlowTest {
    @Override
    protected void initScheme() {
        scheme = new ReplaceSchemeOutputCommands();
        switchDescription = new SwitchDescription(ISwitchManager.OVS_MANUFACTURER, "", "", "", "");
    }
}
