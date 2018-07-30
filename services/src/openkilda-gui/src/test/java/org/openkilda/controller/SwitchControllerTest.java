package org.openkilda.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
import org.openkilda.model.PortInfo;
import org.openkilda.model.SwitchInfo;
import org.openkilda.service.SwitchService;
import org.openkilda.test.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@RunWith(MockitoJUnitRunner.class)
public class SwitchControllerTest {

	private MockMvc mockMvc;

	@Mock
	private SwitchService serviceSwitch;

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
		List<SwitchInfo> switchesInfo = new ArrayList<>();
		try {
	        Mockito.when(serviceSwitch.getSwitches()).thenReturn(switchesInfo);
			mockMvc.perform(get("/switch").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception exception) {
			assertTrue(false);
		}
	}

	@Test
	public void testGetSwichLinkDetails() {
        List<SwitchInfo> switchesInfo = new ArrayList<>();
		try {
	        Mockito.when(serviceSwitch.getSwitches()).thenReturn(switchesInfo);
			mockMvc.perform(get("/switch/links").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
			assertTrue(true);
		} catch (Exception e) {
			assertTrue(false);
		}
	}
}
