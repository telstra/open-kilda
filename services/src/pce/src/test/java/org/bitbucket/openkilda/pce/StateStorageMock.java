package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.FlowStorage;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Collections;
import java.util.Set;

public class StateStorageMock implements NetworkStorage, FlowStorage {
    private int switches = 0;
    private int isls = 0;
    private int flows = 0;

    public int getSwitchesCount() {
        return switches;
    }

    public int getIslsCount() {
        return isls;
    }

    public int getFlowsCount() {
        return flows;
    }

    @Override
    public void deleteSwitch(String switchId) {
        System.out.println("Delete Switch: " + switchId);
        --switches;
    }

    @Override
    public void updateSwitch(String switchId, Switch newSwitch) {
        System.out.println("Update Switch: " + newSwitch);
    }

    @Override
    public Switch getSwitch(String switchId) {
        System.out.println("Get Switch: " + switchId);
        return null;
    }

    @Override
    public void createSwitch(Switch newSwitch) {
        System.out.println("Create Switch: " + newSwitch);
        ++switches;
    }

    @Override
    public Set<Switch> dumpSwitches() {
        System.out.println("Dump Switches");
        return Collections.emptySet();
    }

    @Override
    public void createIsl(Isl isl) {
        System.out.println("Create Isl: " + isl);
        ++isls;
    }

    @Override
    public void deleteIsl(String islId) {
        System.out.println("Delete Isl: " + islId);
        --isls;
    }

    @Override
    public void updateIsl(String islId, Isl isl) {
        System.out.println("Update Isl: " + isl);
    }

    @Override
    public Isl getIsl(String islId) {
        System.out.println("Get Isl: " + islId);
        return null;
    }

    @Override
    public Set<Isl> dumpIsls() {
        System.out.println("Dump Isls");
        return Collections.emptySet();
    }

    @Override
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        System.out.println("Get Flow: " + flowId);
        return null;
    }

    @Override
    public void createFlow(ImmutablePair<Flow, Flow> flow) {
        System.out.println("Create Flow: " + flow);
        ++flows;
    }

    @Override
    public void deleteFlow(String flowId) {
        System.out.println("Delete Flow: " + flowId);
        --flows;
    }

    @Override
    public void updateFlow(String flowId, ImmutablePair<Flow, Flow> flow) {
        System.out.println("Update Flow: " + flow);
    }

    @Override
    public Set<ImmutablePair<Flow, Flow>> dumpFlows() {
        System.out.println("Dump Flows");
        return Collections.emptySet();
    }
}
