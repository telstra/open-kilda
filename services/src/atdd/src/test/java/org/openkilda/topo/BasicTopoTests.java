/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topo;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;

import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

/**
 * BasicTopoTests is almost a "test the tests" class.
 *
 * Its initial purpose in life is to test the testing machinery, and help
 * determine what methods for testing are needed in the abstractions.
 */
public class BasicTopoTests {

	// /**
	// * @throws java.lang.Exception
	// */
	// @BeforeClass
	// public static void setUpBeforeClass() throws Exception {
	// }
	//
	// /**
	// * @throws java.lang.Exception
	// */
	// @AfterClass
	// public static void tearDownAfterClass() throws Exception {
	// }
	//
	// /**
	// * @throws java.lang.Exception
	// */
	// @Before
	// public void setUp() throws Exception {
	// }
	//
	// /**
	// * @throws java.lang.Exception
	// */
	// @After
	// public void tearDown() throws Exception {
	// }
	//

	/**
	 * testBasicMatch will verify that the test Topology using Guava Graphs and
	 * the Mock object will agree.
	 */
	@Test
	public void testBasicMatch() {
		URL url = Resources.getResource("topologies/topo.fullmesh.2.yml");
		String doc = "";
		try {
			doc = Resources.toString(url, Charsets.UTF_8);
			ITopology t1 = new Topology();
			IController ctrl = new MockController(t1);
			ITopology t2 = ctrl.getTopology();
			assertTrue(t1.equivalent(t2));
		} catch (IOException e) {
			fail("Unexpected Exception:" + e.getMessage());
		}
	}

}
