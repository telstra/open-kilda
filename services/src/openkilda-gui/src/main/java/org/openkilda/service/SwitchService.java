/* Copyright 2018 Telstra Open Source
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

import org.openkilda.constants.HttpError;
import org.openkilda.constants.IConstants;
import org.openkilda.dao.entity.SwitchNameEntity;
import org.openkilda.dao.repository.SwitchNameRepository;
import org.openkilda.integration.converter.FlowConverter;
import org.openkilda.integration.exception.IntegrationException;
import org.openkilda.integration.exception.InvalidResponseException;
import org.openkilda.integration.model.PortConfiguration;
import org.openkilda.integration.model.response.ConfiguredPort;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.integration.source.store.SwitchStoreService;
import org.openkilda.integration.source.store.dto.InventorySwitch;
import org.openkilda.model.FlowInfo;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.model.LinkMaxBandwidth;
import org.openkilda.model.LinkParametersDto;
import org.openkilda.model.LinkProps;
import org.openkilda.model.LinkUnderMaintenanceDto;
import org.openkilda.model.PopLocation;
import org.openkilda.model.SwitchDiscrepancy;
import org.openkilda.model.SwitchInfo;
import org.openkilda.model.SwitchMeter;
import org.openkilda.model.SwitchStatus;
import org.openkilda.store.model.Customer;
import org.openkilda.store.service.StoreService;
import org.openkilda.utility.StringUtil;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.usermanagement.model.UserInfo;
import org.usermanagement.service.UserService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * The Class ServiceSwitchImpl.
 *
 * @author Gaurav Chugh
 */
@Service
public class SwitchService {

    private static final Logger LOGGER = Logger.getLogger(SwitchService.class);

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    @Autowired
    private SwitchStoreService switchStoreService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private StoreService storeService;
    
    @Autowired
    private SwitchNameRepository switchNameRepository;

    @Autowired
    private FlowConverter flowConverter;
    
    /**
     * get All SwitchList.
     *
     * @return SwitchRelationData the switch info
     * @throws IntegrationException the integration exception

     */
    public List<SwitchInfo> getSwitches(boolean storeConfigurationStatus, boolean controller) 
            throws IntegrationException {
        List<SwitchInfo> switchInfo = switchIntegrationService.getSwitches();
        if (switchInfo == null) {
            switchInfo = new ArrayList<SwitchInfo>();
        }
        if (!controller) {
            try {
                UserInfo userInfo = userService.getLoggedInUserInfo();
                if (userInfo.getPermissions().contains(IConstants.Permission.SW_SWITCH_INVENTORY)) {
                    if (storeConfigurationStatus && storeService.getSwitchStoreConfig().getUrls().size() > 0) {
                        List<InventorySwitch> inventorySwitches = new ArrayList<InventorySwitch>();
                        inventorySwitches = switchStoreService.getSwitches();
                        processInventorySwitch(switchInfo, inventorySwitches);
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Error occurred while retrieving switches from store", ex);
            }
        }
        return switchInfo;
    }
    
    /**
     * get All SwitchList.
     *
     * @return SwitchRelationData the switch info
     * @throws IntegrationException the integration exception

     */
    public SwitchInfo getSwitch(final String switchId, boolean controller) throws IntegrationException {
        SwitchInfo switchInfo = null;
        try {
            switchInfo = switchIntegrationService.getSwitchesById(switchId);
        } catch (Exception ex) {
            LOGGER.error("Error occurred while retrieving switches from controller", ex);
        }
        if (!controller) {
            try {
                UserInfo userInfo = userService.getLoggedInUserInfo();
                if (userInfo.getPermissions().contains(IConstants.Permission.SW_SWITCH_INVENTORY)) {
                    if (storeService.getSwitchStoreConfig().getUrls().size() > 0) {
                        InventorySwitch inventorySwitch = switchStoreService.getSwitch(switchId);
                        if (inventorySwitch.getSwitchId() != null) {
                            switchInfo = processInventorySwitch(switchInfo, inventorySwitch);
                        } else {
                            SwitchDiscrepancy discrepancy = new SwitchDiscrepancy();
                            discrepancy.setControllerDiscrepancy(false);
                            discrepancy.setStatus(true);
                            discrepancy.setInventoryDiscrepancy(true);

                            SwitchStatus switchState = new SwitchStatus();
                            switchState.setControllerStatus(switchInfo.getState());
                            discrepancy.setStatusValue(switchState);
                            switchInfo.setDiscrepancy(discrepancy);
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.error("Error occurred while retrieving switches from store", ex);
            }
        }
        return switchInfo;
    }

    private SwitchInfo processInventorySwitch(SwitchInfo switchInfo, final InventorySwitch inventorySwitch) {
        if (switchInfo == null) {
            switchInfo = new SwitchInfo();
            toSwitchInfo(switchInfo, inventorySwitch);
        } else {
            appendInventoryInfo(switchInfo, inventorySwitch);
            SwitchDiscrepancy discrepancy = new SwitchDiscrepancy();
            discrepancy.setControllerDiscrepancy(false);
            if (!((switchInfo.getState()).equalsIgnoreCase(inventorySwitch.getStatus()))) {
                discrepancy.setStatus(true);
                discrepancy.setInventoryDiscrepancy(true);

                SwitchStatus switchState = new SwitchStatus();
                switchState.setControllerStatus(switchInfo.getState());
                switchState.setInventoryStatus(inventorySwitch.getStatus());
                discrepancy.setStatusValue(switchState);

                switchInfo.setDiscrepancy(discrepancy);
            }
            switchInfo.setInventorySwitch(true);
        }
        return switchInfo;
    }

    /**
     * Process inventory switch.
     *
     * @param switches the switch.

     * @param inventorySwitches the inventory switch.

     */
    private void processInventorySwitch(final List<SwitchInfo> switches,
            final List<InventorySwitch> inventorySwitches) {
        List<SwitchInfo> discrepancySwitch = new ArrayList<SwitchInfo>();
        for (InventorySwitch inventorySwitch : inventorySwitches) {
            int index = -1;
            for (SwitchInfo switchInfo : switches) {
                if (switchInfo.getSwitchId().equalsIgnoreCase((inventorySwitch.getSwitchId()))) {
                    index = switches.indexOf(switchInfo);
                    break;
                }
            }
            if (index >= 0) {
                SwitchInfo switchObj =  switches.get(index);
                appendInventoryInfo(switchObj, inventorySwitch);
                SwitchDiscrepancy discrepancy = new SwitchDiscrepancy();
                discrepancy.setControllerDiscrepancy(false);
                if (!((switchObj.getState()).equalsIgnoreCase(inventorySwitch.getStatus()))) {
                    discrepancy.setStatus(true);
                    discrepancy.setInventoryDiscrepancy(true);

                    SwitchStatus switchState = new SwitchStatus();
                    switchState.setControllerStatus(switchObj.getState());
                    switchState.setInventoryStatus(inventorySwitch.getStatus());
                    discrepancy.setStatusValue(switchState);

                    switchObj.setDiscrepancy(discrepancy);
                }
                switchObj.setInventorySwitch(true);
            } else {
                SwitchInfo switchInfoObj = new SwitchInfo();
                toSwitchInfo(switchInfoObj, inventorySwitch);
                discrepancySwitch.add(switchInfoObj);
            }
        }

        for (SwitchInfo switchInfo : switches) {
            boolean flag = false;
            for (InventorySwitch inventorySwitch : inventorySwitches) {
                if (switchInfo.getSwitchId().equalsIgnoreCase((inventorySwitch.getSwitchId()))) {
                    flag = true;
                    break;
                }
            }
            if (!flag) {
                SwitchDiscrepancy discrepancy = new SwitchDiscrepancy();
                discrepancy.setInventoryDiscrepancy(true);
                discrepancy.setControllerDiscrepancy(false);
                discrepancy.setStatus(true);

                SwitchStatus switchState = new SwitchStatus();
                switchState.setControllerStatus(switchInfo.getState());
                switchState.setInventoryStatus(null);
                discrepancy.setStatusValue(switchState);
                
                switchInfo.setDiscrepancy(discrepancy);
            }
            switchInfo.setControllerSwitch(true);
        }
        switches.addAll(discrepancySwitch);
    }
    
    private void appendInventoryInfo(final SwitchInfo switchInfo, final InventorySwitch inventorySwitch) {
        switchInfo.setUuid(inventorySwitch.getUuid());
        switchInfo.setCommonName(inventorySwitch.getCommonName());
        if (inventorySwitch.getPopLocation() != null) {
            PopLocation popLocation = new PopLocation();
            popLocation.setStateCode(inventorySwitch.getPopLocation().getStateCode());
            popLocation.setCountryCode(inventorySwitch.getPopLocation().getCountryCode());
            popLocation.setPopUuid(inventorySwitch.getPopLocation().getPopUuid());
            popLocation.setPopName(inventorySwitch.getPopLocation().getPopName());
            popLocation.setPopCode(inventorySwitch.getPopLocation().getPopCode());

            switchInfo.setPopLocation(popLocation);
        }
        switchInfo.setModel(inventorySwitch.getModel());
        switchInfo.setRackLocation(inventorySwitch.getRackLocation());
        switchInfo.setReferenceUrl(inventorySwitch.getReferenceUrl());
        switchInfo.setSerialNumber(inventorySwitch.getSerialNumber());
        switchInfo.setRackNumber(inventorySwitch.getRackNumber());
        switchInfo.setSoftwareVersion(inventorySwitch.getSoftwareVersion());
        switchInfo.setManufacturer(inventorySwitch.getManufacturer());
        if (StringUtil.isNullOrEmpty(switchInfo.getState())) {
            switchInfo.setState(inventorySwitch.getStatus());
        }
    }

    private void toSwitchInfo(final SwitchInfo switchInfo, final InventorySwitch inventorySwitch) {

        switchInfo.setSwitchId(inventorySwitch.getSwitchId());
        switchInfo.setCommonName(inventorySwitch.getCommonName());
        switchInfo.setName(inventorySwitch.getSwitchId());
        switchInfo.setDescription(inventorySwitch.getDescription());
        switchInfo.setUuid(inventorySwitch.getUuid());
        switchInfo.setInventorySwitch(true);

        SwitchDiscrepancy discrepancy = new SwitchDiscrepancy();
        discrepancy.setControllerDiscrepancy(true);
        discrepancy.setStatus(true);

        SwitchStatus switchState = new SwitchStatus();
        switchState.setControllerStatus(null);
        switchState.setInventoryStatus(inventorySwitch.getStatus());
        discrepancy.setStatusValue(switchState);
        switchInfo.setDiscrepancy(discrepancy);
        
        appendInventoryInfo(switchInfo, inventorySwitch);
    }

    /**
     * Gets the isl links.
     *
     * @param srcSwitch the src switch
     * @param srcPort the src port
     * @param dstSwitch the dst switch
     * @param dstPort the dst port
     * @return the isl links
     */
    public List<IslLinkInfo> getIslLinks(final String srcSwitch, final String srcPort, final String dstSwitch,
            final String dstPort) {
        if (StringUtil.isAnyNullOrEmpty(srcSwitch, srcPort, dstPort, dstSwitch)) {
            return switchIntegrationService.getIslLinks(null);
        }
        LinkProps keys = new LinkProps();
        keys.setDstPort(dstPort);
        keys.setDstSwitch(dstSwitch);
        keys.setSrcPort(srcPort);
        keys.setSrcSwitch(srcSwitch);
        return switchIntegrationService.getIslLinks(keys);
    }
    
    /**
     * Gets the link props.
     *
     * @param srcSwitch the src switch

     * @param srcPort  the src port

     * @param dstSwitch the dst switch

     * @param dstPort the dst port

     * @return the link props
     */
    public LinkProps getLinkProps(final String srcSwitch, final String srcPort, final String dstSwitch,
            final String dstPort) {

        if (StringUtil.isAnyNullOrEmpty(srcSwitch, srcPort, dstPort, dstSwitch)) {
            throw new InvalidResponseException(HttpError.PRECONDITION_FAILED.getCode(),
                    HttpError.PRECONDITION_FAILED.getMessage());
        }

        LinkProps keys = new LinkProps();
        keys.setDstPort(dstPort);
        keys.setDstSwitch(dstSwitch);
        keys.setSrcPort(srcPort);
        keys.setSrcSwitch(srcSwitch);

        List<LinkProps> linkPropsList = switchIntegrationService.getIslLinkProps(keys);
        LinkProps linkProps = null;
        if (linkPropsList != null && linkPropsList.size() > 1) {
            throw new InvalidResponseException(HttpError.PRECONDITION_FAILED.getCode(),
                    HttpError.PRECONDITION_FAILED.getMessage());
        } else {
            if (linkPropsList != null && linkPropsList.size() == 1) {
                linkProps = linkPropsList.get(0);
                if (!linkProps.getDstPort().equals(keys.getDstPort())
                        || !linkProps.getDstSwitch().equals(keys.getDstSwitch())
                        || !linkProps.getSrcPort().equals(keys.getSrcPort())
                        || !linkProps.getSrcSwitch().equals(keys.getSrcSwitch())) {
                    throw new InvalidResponseException(HttpError.NO_CONTENT.getCode(),
                            HttpError.NO_CONTENT.getMessage());
                }
            }
        }
        return linkProps;
    }

    /**
     * Update link props.
     * 
     * @param keys the link properties

     * @return the link properties
     */
    public String updateLinkProps(List<LinkProps> keys) {
        return switchIntegrationService.updateIslLinkProps(keys);
    }

    /**
     * Get Switch Rules.
     * 
     * @param switchId the switch id

     * @return the switch rules
     */
    public String getSwitchRules(String switchId) {
        return switchIntegrationService.getSwitchRules(switchId);
    }

    /**
     * Configure port.
     *
     * @param switchId the switch id

     * @param port the port

     * @param configuration the configuration

     * @return the configuredPort
     */
    public ConfiguredPort configurePort(String switchId, String port, PortConfiguration configuration) {
        return switchIntegrationService.configurePort(switchId, port, configuration);
    }
    
    /**
     * Gets port flows.
     *
     * @param switchId the switch id
     * @param port the port
     * @return the customers detail
     */
    public ResponseEntity<List<?>> getPortFlows(String switchId, String port, boolean inventory) {
        if (!inventory) {
            List<FlowInfo> flowList = switchIntegrationService.getSwitchFlows(switchId, port);
            return new ResponseEntity<List<?>>(flowList, HttpStatus.OK);
        } 
        if (inventory && port != null) {
            if (userService.getLoggedInUserInfo().getPermissions().contains(IConstants.Permission.FW_FLOW_INVENTORY)) {
                List<Customer> customers = new ArrayList<Customer>();
                if (storeService.getSwitchStoreConfig().getUrls().size() > 0) {
                    try {
                        customers = switchStoreService.getPortFlows(switchId, port);
                    } catch (Exception ex) {
                        LOGGER.warn("Error occured while retreiving port flows.", ex);
                    }
                }
                return new ResponseEntity<List<?>>(customers, HttpStatus.OK);
            }
        }
        return null;
    }
    
    /**
     * Gets the isl Flows.
     *
     * @param srcSwitch the source switch
     * @param srcPort the source port
     * @param dstSwitch the destination switch
     * @param dstPort the destination port
     * @return the isl flows
     */
    public List<FlowInfo> getIslFlows(String srcSwitch, String srcPort, String dstSwitch,
            String dstPort) {
        return switchIntegrationService.getIslFlows(srcSwitch, srcPort, dstSwitch, dstPort);
    }

    /**
     * Gets the meters.
     *
     * @return the meters
     */
    public SwitchMeter getMeters(String switchId) {
        return switchIntegrationService.getMeters(switchId);
    }

    /**
     * Save or update switch name.
     *
     * @param switchId the switch id
     * @param switchName the switch name
     * @return the SwitchInfo
     */
    public SwitchInfo saveOrUpdateSwitchName(String switchId, String switchName) {
        SwitchNameEntity switchNameEntity = switchNameRepository.findBySwitchDpid(switchId);
        if (switchNameEntity == null) {
            switchNameEntity = new SwitchNameEntity();
        }
        switchNameEntity.setSwitchDpid(switchId);
        switchNameEntity.setSwitchName(switchName);
        switchNameEntity.setUpdatedDate(new Date());
        switchNameRepository.save(switchNameEntity);
        SwitchInfo switchInfo = new SwitchInfo();
        switchInfo.setSwitchId(switchId);
        switchInfo.setName(switchName);
        return switchInfo;
    }

    /**
 * Switch under maintenance.
     *
     * @param switchId
     *            the switch id
     * @param switchInfo
     *        the switch info
     * @return the SwitchInfo
     */
    public SwitchInfo updateMaintenanceStatus(String switchId, SwitchInfo switchInfo) {
        switchInfo = switchIntegrationService.updateMaintenanceStatus(switchId, switchInfo);
        return switchInfo;
        
    }
    
    /**
     * Updates the links under-maintenance status.
     *
     * @param linkUnderMaintenanceDto
     *           the isl maintenance dto
     * @return the isl link info
    */
    public List<IslLinkInfo> updateLinkMaintenanceStatus(LinkUnderMaintenanceDto linkUnderMaintenanceDto) {
        return switchIntegrationService.updateIslLinks(linkUnderMaintenanceDto);
    }
    
    
    /** Delete link.
     *
     * @param linkParametersDto
     *            the link parameters
     * @return the IslLinkInfo
     */
    public List<IslLinkInfo> deleteLink(LinkParametersDto linkParametersDto, Long userId) {
        if (userService.validateOtp(userId, linkParametersDto.getCode())) {
            List<IslLinkInfo> status = switchIntegrationService.deleteLink(linkParametersDto);
            return status;
        } else {
            return null;
        }
    }
    
    /** Update max bandwidth.
   *
   * @param srcSwitch the source switch
   * @param srcPort the source port
   * @param dstSwitch the destination switch
   * @param dstPort the destination port
   * @param linkMaxBandwidth the max bandwidth
   * @return the LinkMaxBandwidth
   */
    public LinkMaxBandwidth updateLinkBandwidth(String srcSwitch, String srcPort, String dstSwitch, String dstPort,
                LinkMaxBandwidth linkMaxBandwidth) {
        LinkMaxBandwidth linkBandwidthUpdate = switchIntegrationService
                .updateLinkBandwidth(srcSwitch, srcPort, dstSwitch, dstPort, linkMaxBandwidth);
        return linkBandwidthUpdate;
    }

}
