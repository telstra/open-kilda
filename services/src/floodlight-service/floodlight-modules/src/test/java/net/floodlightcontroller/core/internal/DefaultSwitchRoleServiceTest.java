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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.Map;

public class DefaultSwitchRoleServiceTest {
    private static final String DEFAULT_ROLE = "defaultRole";

    private static Map<DatapathId, OFControllerRole> switchInitialRoleOriginal;

    private FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    private DefaultSwitchRoleService defaultSwitchRoleService = new DefaultSwitchRoleService();

    @BeforeClass
    public static void setUp() {
        switchInitialRoleOriginal = OFSwitchManager.switchInitialRole;
    }

    @AfterClass
    public static void tearDown() {
        OFSwitchManager.switchInitialRole = switchInitialRoleOriginal;
    }

    @Test
    public void nullConfig() {
        setDefaultRole(null);
        Map<DatapathId, OFControllerRole> originalMap = new HashMap<>();
        OFSwitchManager.switchInitialRole = originalMap;
        defaultSwitchRoleService.init(moduleContext);
        assertSame(OFSwitchManager.switchInitialRole, originalMap);
    }

    @Test
    public void confParsingError() {
        setDefaultRole("foo");
        Map<DatapathId, OFControllerRole> originalMap = new HashMap<>();
        OFSwitchManager.switchInitialRole = originalMap;
        defaultSwitchRoleService.init(moduleContext);
        assertSame(OFSwitchManager.switchInitialRole, originalMap);
    }

    @Test
    public void defaultRole() {
        setDefaultRole("ROLE_SLAVE");
        Map<DatapathId, OFControllerRole> originalMap = new HashMap<>();
        DatapathId datapathId1 = DatapathId.of(1);
        originalMap.put(datapathId1, OFControllerRole.ROLE_MASTER);
        OFSwitchManager.switchInitialRole = originalMap;
        defaultSwitchRoleService.init(moduleContext);
        assertNotSame(OFSwitchManager.switchInitialRole, originalMap);
        assertEquals(OFControllerRole.ROLE_MASTER, OFSwitchManager.switchInitialRole.get(datapathId1));
        DatapathId datapathId2 = DatapathId.of(2);
        assertTrue(OFSwitchManager.switchInitialRole.containsKey(datapathId2));
        assertEquals(OFControllerRole.ROLE_SLAVE, OFSwitchManager.switchInitialRole.get(datapathId2));
    }

    private void setDefaultRole(String defaultRole) {
        moduleContext.addConfigParam(defaultSwitchRoleService, DEFAULT_ROLE, defaultRole);
    }
}
