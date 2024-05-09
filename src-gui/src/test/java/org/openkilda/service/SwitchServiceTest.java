/* Copyright 2024 Telstra Open Source
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

package org.openkilda.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.openkilda.constants.IConstants;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.SwitchInventoryService;
import org.openkilda.integration.source.store.dto.InventorySwitch;
import org.openkilda.integration.source.store.dto.PopLocation;
import org.openkilda.model.Location;
import org.openkilda.model.SwitchDetail;
import org.openkilda.model.SwitchInfo;
import org.openkilda.store.model.SwitchStoreConfigDto;
import org.openkilda.store.model.UrlDto;
import org.openkilda.store.service.StoreService;
import org.openkilda.test.MockitoExtension;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.nio.file.AccessDeniedException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SwitchServiceTest {

    private static final String SW_TEST_1 = "SW_TEST_1";
    private static final String SW_TEST_2 = "SW_TEST_2";

    @InjectMocks
    private SwitchService switchService;
    @Mock
    private SwitchIntegrationService switchIntegrationService;
    @Mock
    private SwitchInventoryService switchInventoryService;
    @Mock
    private UserService userService;
    @Mock
    private StoreService storeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /**
     * Only switch in controller should be returned.
     */
    @Disabled
    @Test
    void switchDetailsOnlyForController() throws AccessDeniedException {
        when(switchIntegrationService.getSwitchesById(SW_TEST_1)).thenReturn(getSwitchInfo1(SW_TEST_1));

        List<SwitchDetail> actual = switchService.getSwitchDetails("SW_TEST_1", true);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        Assertions.assertEquals(getSwitchDetail1(SW_TEST_1, null), actual.get(0));
    }

    /**
     * SwitchId exist in controller and inventory switch with the same switch_id exist in inventory service
     * should be returned only one switch.
     */
    @Test
    @Disabled
    void switchDetails() throws AccessDeniedException {
        when(switchIntegrationService.getSwitchesById(SW_TEST_1)).thenReturn(getSwitchInfo1(SW_TEST_1));
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitch(SW_TEST_1)).thenReturn(getInventorySwitch(SW_TEST_1));

        List<SwitchDetail> actual = switchService.getSwitchDetails(SW_TEST_1, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        SwitchDetail expectedWithInventory = getSwitchDetail1(SW_TEST_1, getInventorySwitch(SW_TEST_1));
        Assertions.assertEquals(expectedWithInventory, actual.get(0));
    }

    /**
     * SwitchId exist in controller, but does not exist in Inventory, should be returned only one switch.
     */
    @Test
    @Disabled
    void switchDetailsEmptyInventory() throws AccessDeniedException {
        when(switchIntegrationService.getSwitchesById(SW_TEST_1)).thenReturn(getSwitchInfo1(SW_TEST_1));
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitch(SW_TEST_1)).thenReturn(null);

        List<SwitchDetail> actual1 = switchService.getSwitchDetails(SW_TEST_1, false);
        Assertions.assertNotNull(actual1);
        Assertions.assertEquals(1, actual1.size());
        SwitchDetail expectedWithoutInventory = getSwitchDetail1(SW_TEST_1, null);
        Assertions.assertEquals(expectedWithoutInventory, actual1.get(0));
    }

    /**
     * One switch in controller and different switch in Inventory should be returned.
     */
    @Test
    @Disabled
    void switchDetailsDifferentSwitches() throws AccessDeniedException {
        when(switchIntegrationService.getSwitches()).thenReturn(Collections.singletonList(getSwitchInfo1(SW_TEST_1)));
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitches()).thenReturn(Collections.singletonList(getInventorySwitch(SW_TEST_2)));

        List<SwitchDetail> actual = switchService.getSwitchDetails(null, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(2, actual.size());
        List<SwitchDetail> expected = Lists.newArrayList(getSwitchDetail1(SW_TEST_1, null),
                getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_2)));

        Assertions.assertEquals(expected.get(0), actual.get(0));
        Assertions.assertEquals(expected.get(1), actual.get(1));
    }

    /**
     * Only inventory switches exist.
     */
    @Test
    @Disabled
    void switchDetailsOnlyInventoryExist() throws AccessDeniedException {
        when(switchIntegrationService.getSwitches()).thenReturn(Collections.emptyList());
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitches()).thenReturn(Collections.singletonList(getInventorySwitch(SW_TEST_2)));

        List<SwitchDetail> actual = switchService.getSwitchDetails(null, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        List<SwitchDetail> expected
                = Lists.newArrayList(getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_2)));

        Assertions.assertEquals(expected.get(0), actual.get(0));
    }

    /**
     * No switches exist at all.
     */
    @Test
    @Disabled
    void switchDetailsNoSwitchesExist() throws AccessDeniedException {
        when(switchIntegrationService.getSwitches()).thenReturn(Collections.emptyList());
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitches()).thenReturn(Collections.emptyList());

        List<SwitchDetail> actual = switchService.getSwitchDetails(null, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(0, actual.size());
        List<SwitchDetail> expected = Lists.newArrayList(Collections.emptyList());

        Assertions.assertEquals(expected, actual);
    }


    private SwitchDetail getSwitchDetail1(String switchId, InventorySwitch inventorySwitch) {
        return SwitchDetail.builder()
                .switchId(switchId)
                .name(switchId)
                .address("address")
                .port("123")
                .hostname("qwerty")
                .description("qwerty")
                .state("qwerty")
                .underMaintenance(true)
                .ofVersion("qwerty")
                .manufacturer("qwerty")
                .hardware("qwerty")
                .software("qwerty")
                .serialNumber("qwerty")
                .pop("qwerty")
                .location(getLocation1())
                .inventorySwitchDetail(inventorySwitch)
                .build();
    }

    private SwitchDetail getSwitchDetailOnlyWithInventory(InventorySwitch inventorySwitch) {
        return SwitchDetail.builder()
                .inventorySwitchDetail(inventorySwitch)
                .build();
    }

    private SwitchInfo getSwitchInfo1(String switchId) {
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.setSwitchId(switchId);
        switchInfo.setAddress("address");
        switchInfo.setPort("123");
        switchInfo.setHostname("qwerty");
        switchInfo.setDescription("qwerty");
        switchInfo.setState("qwerty");
        switchInfo.setUnderMaintenance(true);
        switchInfo.setOfVersion("qwerty");
        switchInfo.setManufacturer("qwerty");
        switchInfo.setHardware("qwerty");
        switchInfo.setSoftware("qwerty");
        switchInfo.setSerialNumber("qwerty");
        switchInfo.setPop("qwerty");
        switchInfo.setLocation(getLocation1());
        switchInfo.setName(switchId);
        return switchInfo;
    }

    private Location getLocation1() {
        Location location = new Location();
        location.setCity("Sydney");
        location.setCountry("Australia");
        location.setStreet("P. Sherman 42 Wallaby Way");
        return location;
    }

    private InventorySwitch getInventorySwitch(String switchId) {
        InventorySwitch inventorySwitch = new InventorySwitch();
        inventorySwitch.setUuid("randomUUID");
        inventorySwitch.setSwitchId(switchId);
        inventorySwitch.setDescription("Sample switch description");
        inventorySwitch.setName("Switch1");
        inventorySwitch.setCommonName("CommonSwitch");
        inventorySwitch.setModel("ModelX");
        inventorySwitch.setStatus("Active");
        inventorySwitch.setRackLocation("Rack1");
        inventorySwitch.setReferenceUrl("http://example.com");
        inventorySwitch.setSerialNumber("123456789");
        inventorySwitch.setRackNumber("Rack01");
        inventorySwitch.setSoftwareVersion("1.0");
        inventorySwitch.setManufacturer("ManufacturerXYZ");

        PopLocation popLocation = new PopLocation();
        popLocation.setStateCode("NY");
        popLocation.setCountryCode("US");
        popLocation.setPopUuid("POP_UUID");
        popLocation.setPopName("Sample POP");
        popLocation.setPopCode("POP123");
        inventorySwitch.setPopLocation(popLocation);

        return inventorySwitch;
    }

    private UserInfo getUserInfoWithPermission() {
        UserInfo userInfo = new UserInfo();
        userInfo.setPermissions(Sets.newHashSet(IConstants.Permission.SW_SWITCH_INVENTORY));
        return userInfo;
    }
}
