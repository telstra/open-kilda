package org.bitbucket.openkilda.topo;

import java.beans.Transient;

/**
 * LinkEndpoint captures the key elements of a link endpoint
 */
public class LinkEndpoint implements ITopoSlug {

    private final Switch topoSwitch;
    private final Port switchPort;
    private final PortQueue portQueue;
    private transient String slug;

    private static final Switch nullSwitch = new Switch("0");
    private static final Port nullPort = new Port (nullSwitch,"0");
    private static final PortQueue nullQueue = new PortQueue(nullPort,"0");

    public LinkEndpoint(Switch topoSwitch, Port switchPort, PortQueue portQueue) {
        this.topoSwitch = (topoSwitch != null) ? topoSwitch : nullSwitch;
        this.switchPort = (switchPort != null) ? switchPort: nullPort;
        this.portQueue = (portQueue != null) ? portQueue: nullQueue;
    }

    public LinkEndpoint(PortQueue portQueue) {
        // check for nulls all the way up
        this(   (portQueue == null) ?
                        null : (portQueue.getParent() == null) ?
                                null : portQueue.getParent().getParent(),
                (portQueue == null) ? null : portQueue.getParent(),
                portQueue);
    }


    public Switch getTopoSwitch() {
        return topoSwitch;
    }

    public Port getSwitchPort() {
        return switchPort;
    }

    public PortQueue getPortQueue() {
        return portQueue;
    }

    public String getSlug() {
        if (slug == null)
            slug = TopoSlug.toString(this);
        return slug;
    }

    public static void main(String[] args) {
        // standard null endpoint
        System.out.println("nullQueue = " + nullQueue.getSlug());
        // effectively the same as null endpoint
        System.out.println("null port = " + new LinkEndpoint(null).getSlug());
    }

}
