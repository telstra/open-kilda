package org.openkilda.wfm.topology.stats;

import org.junit.*;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class FlowResultTest {
    private static final long LEGACY_FORWARD_COOKIE = 0x10400000005d803L;
    private static final long LEGACY_REVERSE_COOKIE = 0x18400000005d803L;
    private static final long FORWARD_COOKIE = 0x4000000000000001L;
    private static final long REVERSE_COOKIE = 0x2000000000000001L;
    private static final long BAD_COOKIE =     0x235789abcd432425L;
    private static final String FLOW_ID = "f3459085345454";
    private static final String SRC_SWITCH = "de:ad:be:ef:00:00:00:02";
    private static final String DST_SWITCH = "de:ad:be:ef:00:00:00:04";

    private FlowResult flowResult;
    private static Map<String, Object> queryMap;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void oneTimeSetUp() {
        queryMap = new HashMap<>();
        queryMap.put("cookie", FORWARD_COOKIE);
        queryMap.put("flowid", FLOW_ID);
        queryMap.put("src_switch", SRC_SWITCH);
        queryMap.put("dst_switch", DST_SWITCH);
    }

    @AfterClass
    public static void oneTimeTearDown() {
    }

    @Before
    public void setupEach() throws Exception {
        flowResult = new FlowResult(queryMap);
    }

    @After
    public void teardownEach() {
    }

    @Test
    public void directionUpdateTest() {
        assertTrue(flowResult.getDirection().equals("forward"));
    }

    @Test
    public void isLegacyCookieTest() {
        assertTrue(flowResult.isLegacyCookie(LEGACY_FORWARD_COOKIE));
        assertTrue(flowResult.isLegacyCookie(LEGACY_REVERSE_COOKIE));
        assertFalse(flowResult.isLegacyCookie(FORWARD_COOKIE));
        assertFalse(flowResult.isLegacyCookie(REVERSE_COOKIE));

        assertFalse(flowResult.isLegacyCookie(BAD_COOKIE));
    }

    @Test
    public void isKildaCookieTest() {
        assertTrue(flowResult.isKildaCookie(FORWARD_COOKIE));
        assertTrue(flowResult.isKildaCookie(REVERSE_COOKIE));
        assertFalse(flowResult.isKildaCookie(LEGACY_FORWARD_COOKIE));
        assertFalse(flowResult.isKildaCookie(LEGACY_REVERSE_COOKIE));
        assertFalse(flowResult.isKildaCookie(BAD_COOKIE));
    }

    @Test
    public void getKildaDirectionTest() throws Exception {
        assertTrue(flowResult.getKildaDirection(FORWARD_COOKIE).equals("forward"));
        assertTrue(flowResult.getKildaDirection(REVERSE_COOKIE).equals("reverse"));

        thrown.expect(Exception.class);
        thrown.expectMessage(LEGACY_FORWARD_COOKIE + " is not a Kilda flow");
        flowResult.getKildaDirection(LEGACY_FORWARD_COOKIE);
    }

    @Test
    public void getLegacyDirectionTest() throws Exception {
        assertTrue(flowResult.getLegacyDirection(LEGACY_FORWARD_COOKIE).equals("forward"));
        assertTrue(flowResult.getLegacyDirection(LEGACY_REVERSE_COOKIE).equals("reverse"));

        thrown.expect(Exception.class);
        thrown.expectMessage(FORWARD_COOKIE +  " is not a legacy flow");
        flowResult.getLegacyDirection(FORWARD_COOKIE);
    }

    @Test
    public void findDirectionTest() throws Exception {
        assertTrue(flowResult.findDirection(LEGACY_FORWARD_COOKIE).equals("forward"));
        assertTrue(flowResult.findDirection(REVERSE_COOKIE).equals("reverse"));

        thrown.expect(Exception.class);
        thrown.expectMessage(BAD_COOKIE + " is not a Kilda flow");
        flowResult.findDirection(BAD_COOKIE);
    }
}