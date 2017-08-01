package org.bitbucket.openkilda.pce;

import org.bitbucket.openkilda.pce.model.Flow;
import org.bitbucket.openkilda.pce.model.Isl;
import org.bitbucket.openkilda.pce.model.Node;
import org.bitbucket.openkilda.pce.storage.Storage;
import org.bitbucket.openkilda.pce.model.Switch;

import java.util.Collections;
import java.util.List;

public class StorageMock implements Storage {
    @Override
    public void deleteSwitch(String switchId) {
        System.out.println("deleteSwitch");
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
    }

    @Override
    public List<Switch> dumpSwitches() {
        System.out.println("dumpSwitches");
        return Collections.emptyList();
    }

    @Override
    public void createIsl(Isl isl) {
        System.out.println("createIsl");
    }

    @Override
    public void deleteIsl(String islId) {
        System.out.println("deleteIsl");
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
    public List<Isl> dumpIsls() {
        System.out.println("dumpIsls");
        return Collections.emptyList();
    }

    @Override
    public Flow getFlow(String flowId) {
        System.out.println("getFlow");
        return null;
    }

    @Override
    public void createFlow(Flow flow) {
        System.out.println("createFlow");
    }

    @Override
    public void deleteFlow(String flowId) {
        System.out.println("deleteFlow");
    }

    @Override
    public void updateFlow(String flowId, Flow flow) {
        System.out.println("updateFlow");
    }

    @Override
    public List<Flow> dumpFlows() {
        System.out.println("dumpFlows");
        return Collections.emptyList();
    }

    @Override
    public List<Node> getSwitchPath(String srcSwitch, String destinationSwitch) {
        System.out.println("getSwitchPath");
        return null;
    }

    @Override
    public List<Node> getIslPath(String srcSwitch, String destinationSwitch) {
        System.out.println("getIslPath");
        return null;
    }
}
