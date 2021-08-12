package org.openkilda.functionaltests

import org.openkilda.testing.model.topology.TopologyDefinition

import org.springframework.beans.factory.ObjectFactory
import org.springframework.beans.factory.config.Scope

class SpecThreadScope implements Scope {

    @Override
    Object get(String name, ObjectFactory<?> objectFactory) {
        TopologyDefinition topo = BaseSpecification.threadLocalTopology.get();
        if (topo == null) {
            topo = (TopologyDefinition) objectFactory.getObject();
            BaseSpecification.threadLocalTopology.set(topo)
        }
        return topo;
    }

    @Override
    Object remove(String name) { return null }

    @Override
    void registerDestructionCallback(String name, Runnable callback) { }

    @Override
    Object resolveContextualObject(String key) { return null }

    @Override
    String getConversationId() { return null }
}
