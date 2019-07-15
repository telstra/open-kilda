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

package org.openkilda.floodlight.utils;

import com.google.common.collect.ImmutableList;
import org.easymock.EasyMockSupport;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.EthType;

import java.util.Arrays;
import java.util.List;

public class OfAdapterVlanTransformationTest extends EasyMockSupport {
    private OFFactory ofFactory;

    @Before
    public void setUp() throws Exception {
        ofFactory = new OFFactoryVer13();
    }

    @Test
    public void noInNoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(), ImmutableList.of());
        verifyActions(transform);
    }

    @Test
    public void oneInNoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1), ImmutableList.of());
        verifyActions(transform,
                      ofFactory.actions().popVlan());
    }

    @Test
    public void twoInNoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1, 2), ImmutableList.of());
        verifyActions(transform,
                      ofFactory.actions().popVlan(),
                      ofFactory.actions().popVlan());
    }

    @Test
    public void twoInOneOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1, 2), ImmutableList.of(3));
        verifyActions(transform,
                      ofFactory.actions().popVlan(),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 3));
    }

    @Test
    public void twoInOutOutOneMatch() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(5, 2), ImmutableList.of(5));
        verifyActions(transform,
                      ofFactory.actions().popVlan());
    }

    @Test
    public void twoInTwoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1, 2), ImmutableList.of(3, 4));
        verifyActions(transform,
                      ofFactory.actions().popVlan(),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 3),
                      ofFactory.actions().pushVlan(EthType.VLAN_FRAME),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 4));
    }

    @Test
    public void oneInTwoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1), ImmutableList.of(3, 4));
        verifyActions(transform,
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 3),
                      ofFactory.actions().pushVlan(EthType.VLAN_FRAME),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 4));
    }

    @Test
    public void oneInTwoOutOneMatch() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(5), ImmutableList.of(5, 4));
        verifyActions(transform,
                      ofFactory.actions().pushVlan(EthType.VLAN_FRAME),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 4));
    }

    @Test
    public void noInTwoOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(), ImmutableList.of(3, 4));
        verifyActions(transform,
                      ofFactory.actions().pushVlan(EthType.VLAN_FRAME),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 3),
                      ofFactory.actions().pushVlan(EthType.VLAN_FRAME),
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 4));
    }

    @Test
    public void oneInOneOut() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(1), ImmutableList.of(3));
        verifyActions(transform,
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 3));
    }

    @Test
    public void oneInOneOutOneMatch() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(5), ImmutableList.of(5));
        verifyActions(transform);
    }

    @Test
    public void twoInTwoOutOneMatch() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(5, 2), ImmutableList.of(5, 4));
        verifyActions(transform,
                      OfAdapter.INSTANCE.setVlanIdAction(ofFactory, 4));
    }

    @Test
    public void twoInTwoOutTwoMatch() {
        List<OFAction> transform = OfAdapter.INSTANCE.makeVlanReplaceActions(
                ofFactory, ImmutableList.of(5, 6), ImmutableList.of(5, 6));
        verifyActions(transform);
    }

    private void verifyActions(List<OFAction> actual, OFAction... expected) {
        Assert.assertEquals("produced actions sequence do not match expectation",
                            Arrays.asList(expected), actual);
    }
}
