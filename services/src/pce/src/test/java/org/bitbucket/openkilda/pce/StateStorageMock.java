package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Rule;
import org.bitbucket.openkilda.pce.model.Switch;
import org.bitbucket.openkilda.pce.provider.FlowStorage;
import org.bitbucket.openkilda.pce.provider.NetworkStorage;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.Collections;
import java.util.Set;

public class StateStorageMock implements NetworkStorage, FlowStorage {
    private int switches = 0;
    private int isls = 0;

    public int getSwitchesCount() {
        return switches;
    }

    public int getIslsCount() {
        return isls;
    }

    @Override
    public void deleteSwitch(String switchId) {
        System.out.println("deleteSwitch");
        --switches;
    }

    @Override
    public void updateSwitch(String switchId, Switch newSwitch) {
        System.out.println("updateSwitch");
    }

    @Override
    public Switch getSwitch(String switchId) {
        System.out.println("getSwitch");
        return null;
    }

    @Override
    public void createSwitch(Switch newSwitch) {
        System.out.println("createSwitch");
        ++switches;
    }

    @Override
    public Set<Switch> dumpSwitches() {
        System.out.println("dumpSwitches");
        return Collections.emptySet();
    }

    @Override
    public void createIsl(Isl isl) {
        System.out.println("createIsl");
        ++isls;
    }

    @Override
    public void deleteIsl(String islId) {
        System.out.println("deleteIsl");
        --isls;
    }

    @Override
    public void updateIsl(String islId, Isl isl) {
        System.out.println("updateIsl");
    }

    @Override
    public Isl getIsl(String islId) {
        System.out.println("getIsl");
        return null;
    }

    @Override
    public Set<Isl> dumpIsls() {
        System.out.println("dumpIsls");
        return Collections.emptySet();
    }

    @Override
    public ImmutablePair<Flow, Flow> getFlow(String flowId) {
        return null;
    }

    @Override
    public void createFlow(ImmutablePair<Flow, Flow> flow) {

    }

    @Override
    public void deleteFlow(String flowId) {

    }

    @Override
    public void updateFlow(String flowId, ImmutablePair<Flow, Flow> flow) {

    }

    @Override
    public Set<ImmutablePair<Flow, Flow>> dumpFlows() {
        System.out.println("dumpFlows");
        return Collections.emptySet();
    }
}
