package org.bitbucket.openkilda.smoke;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import org.bitbucket.openkilda.topo.*;
import org.junit.Test;

import java.io.IOException;
import java.net.URL;

/**
 * Created by carmine on 3/10/17.
 */
public class MininetSmokeTest {

    private void testIt(URL url) throws Exception {
        TestUtils.clearEverything();

        TopologyHelp.TestMininetCreate(
                Resources.toString(url, Charsets.UTF_8)
        );
    }

    @Test
    public void test5_20() throws Exception {
        testIt(Resources.getResource("topologies/rand-5-20.json"));
    }

}
