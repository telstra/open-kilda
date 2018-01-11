package org.openkilda.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.openkilda.model.response.PortInfo;
import org.openkilda.model.response.SwitchRelationData;
import org.openkilda.service.impl.ServiceSwitchImpl;
import org.openkilda.test.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class SwitchControllerTest {

	private MockMvc mockMvc;

	@Mock
	private ServiceSwitchImpl processSwitchDetails;

	@InjectMocks
	private SwitchController switchController;

	private static final String switchUuid = "de:ad:be:ef:00:00:00:03";

	@Before
	public void init() {
		MockitoAnnotations.initMocks(this);
		mockMvc = MockMvcBuilders.standaloneSetup(switchController).build();
	}

	@Test
	public void testGetAllSwitchesDetails() {

		SwitchRelationData switchDataList = new SwitchRelationData();

		Mockito.when(processSwitchDetails.getswitchdataList()).thenReturn(switchDataList);
		try {
			mockMvc.perform(get("/switch").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception exception) {
			assertTrue(false);
		}
	}

	@Test
	public void testGetSwichPortDetails() {

		List<PortInfo> portResponse = new ArrayList<PortInfo>();

		Mockito.when(processSwitchDetails.getPortResponseBasedOnSwitchId(switchUuid)).thenReturn(portResponse);

		try {
			mockMvc.perform(get("/switch/{switchId}/ports", switchUuid).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception e) {
			assertTrue(false);
		}

	}

	@Test
	public void testGetSwichLinkDetails() {

		SwitchRelationData response = new SwitchRelationData();

		Mockito.when(processSwitchDetails.getswitchdataList()).thenReturn(response);

		try {
			mockMvc.perform(get("/switch/links").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception e) {
			assertTrue(false);
		}

	}

	@Test
	public void testGetSwichFlowDetails() {

		SwitchRelationData switchDataList = new SwitchRelationData();

		Mockito.when(processSwitchDetails.getswitchdataList()).thenReturn(switchDataList);

		try {
			mockMvc.perform(get("/switch/flows").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception e) {
			assertTrue(false);
		}
	}

}
