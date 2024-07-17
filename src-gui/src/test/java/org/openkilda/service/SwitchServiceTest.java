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
import org.openkilda.integration.exception.InvalidResponseException;
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class SwitchServiceTest {

    private static final String SW_TEST_1 = "sw_test_1";
    private static final String SW_TEST_2 = "sw_test_2";
    private static final String WRONG_SWITCH_ID = "WRONG_SWITCH_ID";

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


    @Test
    void switchDetailsOnlyForController() throws AccessDeniedException {
        when(switchIntegrationService.getSwitchesById(SW_TEST_1)).thenReturn(getSwitchInfo1(SW_TEST_1));

        List<SwitchDetail> actual = switchService.getSwitchDetails(SW_TEST_1, true);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        Assertions.assertEquals(getSwitchDetail1(SW_TEST_1, null), actual.get(0));
    }

    /**
     * SwitchId exist in controller and inventory switch with the same switch_id exist in inventory service
     * should be returned only one switch.
     */
    @Test
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
     * Get inventory switch by id with wrong switchId, northbound causing InvalidResponseException.
     * But inventory switch exist.
     */
    @Test
    void switchDetailsInvalidResponse() throws AccessDeniedException {
        when(switchIntegrationService.getSwitchesById(WRONG_SWITCH_ID))
                .thenThrow(InvalidResponseException.class);

        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitch(WRONG_SWITCH_ID))
                .thenReturn(getInventorySwitch(WRONG_SWITCH_ID));

        List<SwitchDetail> actual = switchService.getSwitchDetails(WRONG_SWITCH_ID, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        SwitchDetail expectedWithInventory = getSwitchDetail1(null, getInventorySwitch(WRONG_SWITCH_ID));
        Assertions.assertEquals(expectedWithInventory, actual.get(0));
    }

    /**
     * SwitchId exist in controller, but does not exist in Inventory, should be returned only one switch.
     */
    @Test
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

    /**
     * SwitchDetails for all switches, controller false.
     * One switch in controller and three switches in inventory exist, two of them with the same switchId.
     * Should return 3 switches in total.
     */
    @Test
    void switchDetailsWithOneConrollerAndDifferentInventory() throws AccessDeniedException {
        when(switchIntegrationService.getSwitches()).thenReturn(Collections.singletonList(getSwitchInfo1(SW_TEST_2)));
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        List<InventorySwitch> inventorySwitches = Lists.newArrayList();
        inventorySwitches.add(getInventorySwitch(SW_TEST_1));
        inventorySwitches.add(getInventorySwitch(SW_TEST_2));
        inventorySwitches.add(getInventorySwitch(SW_TEST_2));
        when(switchInventoryService.getSwitches()).thenReturn(inventorySwitches);

        List<SwitchDetail> actual = switchService.getSwitchDetails(null, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(3, actual.size());

        List<SwitchDetail> expected =
                Lists.newArrayList(getSwitchDetail1(SW_TEST_2, getInventorySwitch(SW_TEST_2, true)),
                        getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_2, true)),
                        getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_1)));

        Assertions.assertEquals(expected.get(0), actual.get(0));
        Assertions.assertEquals(expected.get(1), actual.get(1));
        Assertions.assertEquals(expected.get(2), actual.get(2));
    }

    /**
     * One switch in controller and the same switch in inventory exist, but switchId have the different letter case.
     * One switch it total should be returned.
     */
    @Test
    void switchDetailsSameSwitchesDifferentSwitchIdLetterCase() throws AccessDeniedException {
        when(switchIntegrationService.getSwitches()).thenReturn(Collections.singletonList(getSwitchInfo1(SW_TEST_1)));
        when(userService.getLoggedInUserInfo()).thenReturn(getUserInfoWithPermission());
        SwitchStoreConfigDto switchStoreConfig = mock(SwitchStoreConfigDto.class);
        when(storeService.getSwitchStoreConfig()).thenReturn(switchStoreConfig);
        Map<String, UrlDto> map = new HashMap<>();
        map.put("someKey", new UrlDto());
        when(storeService.getSwitchStoreConfig().getUrls()).thenReturn(map);
        when(switchInventoryService.getSwitches())
                .thenReturn(Collections.singletonList(getInventorySwitch(SW_TEST_1.toUpperCase(), "f3")));

        List<SwitchDetail> actual = switchService.getSwitchDetails(null, false);
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(1, actual.size());
        List<SwitchDetail> expected = Lists.newArrayList(getSwitchDetail1(SW_TEST_1,
                getInventorySwitch(SW_TEST_1.toUpperCase(), "f3")));
        Assertions.assertEquals(expected.get(0), actual.get(0));


        when(switchInventoryService.getSwitches())
                .thenReturn(Arrays.asList(getInventorySwitch(SW_TEST_1.toUpperCase(), "f1"),
                        getInventorySwitch(SW_TEST_1.toLowerCase(), "f2"),
                        getInventorySwitch(SW_TEST_1.toUpperCase(), "f3")));


        actual = switchService.getSwitchDetails(null, false);
        expected.set(0, getSwitchDetail1(SW_TEST_1, getInventorySwitch(SW_TEST_1.toUpperCase(), true, "f3")));
        expected.add(getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_1.toUpperCase(), true, "f1")));
        expected.add(getSwitchDetailOnlyWithInventory(getInventorySwitch(SW_TEST_1.toLowerCase(), true, "f2")));
        Assertions.assertNotNull(actual);
        Assertions.assertEquals(3, actual.size());
        Assertions.assertEquals(expected.get(0), actual.get(0));
        Assertions.assertEquals(expected.get(1), actual.get(1));
        Assertions.assertEquals(expected.get(2), actual.get(2));
    }

    private SwitchDetail getSwitchDetail1(String switchId, InventorySwitch inventorySwitch) {
        SwitchDetail.SwitchDetailBuilder builder = SwitchDetail.builder();
        if (switchId != null) {
            builder.switchId(switchId)
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
                    .location(getLocation1());
        }
        builder.inventorySwitchDetail(inventorySwitch);
        return builder.build();
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
        return getInventorySwitch(switchId, false, null);
    }

    private InventorySwitch getInventorySwitch(String switchId, String description) {
        return getInventorySwitch(switchId, false, description);
    }

    private InventorySwitch getInventorySwitch(String switchId, boolean duplicate) {
        return getInventorySwitch(switchId, duplicate, null);
    }

    private InventorySwitch getInventorySwitch(String switchId, boolean duplicate, String description) {
        InventorySwitch inventorySwitch = new InventorySwitch();
        inventorySwitch.setUuid("randomUUID");
        inventorySwitch.setSwitchId(switchId);
        inventorySwitch.setDescription(description != null ? description : "Sample switch description");
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

        inventorySwitch.setHasDuplicate(duplicate);
        return inventorySwitch;
    }

    private UserInfo getUserInfoWithPermission() {
        UserInfo userInfo = new UserInfo();
        userInfo.setPermissions(Sets.newHashSet(IConstants.Permission.SW_SWITCH_INVENTORY));
        return userInfo;
    }
}
