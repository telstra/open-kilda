package org.openkilda.wfm;

import org.kohsuke.args4j.CmdLineException;
import org.openkilda.wfm.error.ConfigurationException;
import org.openkilda.wfm.topology.Topology;

import java.util.Properties;

public class LaunchEnvironment {
    private static String CLI_OVERLAY = "cli";

    private CliArguments cli;
    private Properties properties;

    public LaunchEnvironment(String args[]) throws CmdLineException, ConfigurationException {
        this.cli = new CliArguments(args);
        properties = cli.getProperties();
        properties = makeCliOverlay();
    }

    public String getTopologyName() {
        return cli.getTopologyName();
    }

    public PropertiesReader makePropertiesReader() {
        return new PropertiesReader(getProperties(), CLI_OVERLAY, "");
    }

    public PropertiesReader makePropertiesReader(String ownSection, String defaults) {
        defaults = Topology.TOPOLOGY_PROPERTIES_DEFAULTS_PREFIX + defaults;
        return new PropertiesReader(getProperties(), CLI_OVERLAY, ownSection, defaults, "");
    }

    public void setupOverlay(Properties overlay) {
        Properties newLayer = new Properties(getProperties());
        for (String name : overlay.stringPropertyNames()) {
            newLayer.setProperty(name, overlay.getProperty(name));
        }
        properties = newLayer;
    }

    private Properties makeCliOverlay() {
        Properties overlay = new Properties(getProperties());

        overlay.setProperty(CLI_OVERLAY + ".local", cli.getIsLocal() ? "true" : "false");
        if (cli.getLocalExecutionTime() != null) {
            overlay.setProperty(CLI_OVERLAY + ".local.execution.time", cli.getLocalExecutionTime().toString());
        }

        return overlay;
    }

    protected Properties getProperties() {
        return properties;
    }
}
