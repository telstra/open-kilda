package org.bitbucket.openkilda.wfm.topology.utils;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by carmine on 5/14/17.
 */
public class LinkTracker implements Serializable {

    /**
     * SwitchID -> PortID,PortID (maybe LinkID someday)
     */
    protected ConcurrentHashMap<String,
            ConcurrentHashMap<String,String>> state = new ConcurrentHashMap<>();

    public ConcurrentHashMap<String,String> getSwitchPorts(String switchID){
        return state.get(switchID);
    }

    public ConcurrentHashMap<String,String> getOrNewSwitchPorts(String switchID){
        ConcurrentHashMap<String,String> result = state.get(switchID);
        if (result == null)
            result = new ConcurrentHashMap<String,String>();
        return result;
    }

    /** for use in foreach */
    public ConcurrentHashMap.KeySetView<String, ConcurrentHashMap<String, String>> getSwitches() {
        return state.keySet();
    }

}
