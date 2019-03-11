/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DefaultSwitchRoleService implements IFloodlightModule {
    private static final Logger log = LoggerFactory.getLogger(DefaultSwitchRoleService.class);

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {
        Map<String, String> configParams = floodlightModuleContext.getConfigParams(this);
        String defaultRole = configParams.get("defaultRole");
        if (defaultRole == null) {
            log.info("Default switch role is not set.");
            return;
        }
        try {
            OFControllerRole controllerRole = OFControllerRole.valueOf(defaultRole);
            log.info("Default switch role {}", controllerRole);
            OFSwitchManager.switchInitialRole = new DefaultHashMap<>(OFSwitchManager.switchInitialRole, controllerRole);
        } catch (IllegalArgumentException e) {
            log.warn("Couldn't parse {} as controller role.", defaultRole);
        }
    }

    @Override
    public void startUp(FloodlightModuleContext floodlightModuleContext) throws FloodlightModuleException {

    }

    static class DefaultHashMap<K, V> extends HashMap<K, V> {
        protected V defaultValue;

        public DefaultHashMap(Map<? extends K, ? extends V> m, V defaultValue) {
            super(m);
            this.defaultValue = defaultValue;
        }

        @Override
        public boolean containsKey(Object key) {
            return true;
        }

        @Override
        public V get(Object key) {
            return super.getOrDefault(key, defaultValue);
        }
    }
}
