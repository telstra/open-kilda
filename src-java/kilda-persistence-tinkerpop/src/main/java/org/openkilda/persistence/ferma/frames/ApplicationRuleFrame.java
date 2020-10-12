/* Copyright 2020 Telstra Open Source
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

package org.openkilda.persistence.ferma.frames;

import org.openkilda.model.ApplicationRule.ApplicationRuleData;
import org.openkilda.model.SwitchId;
import org.openkilda.model.cookie.ExclusionCookie;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.ExclusionCookieConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.annotations.Property;

public abstract class ApplicationRuleFrame extends KildaBaseVertexFrame implements ApplicationRuleData {
    public static final String FRAME_LABEL = "application_rule";
    public static final String FLOW_ID_PROPERTY = "flow_id";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String COOKIE_PROPERTY = "cookie";
    public static final String SRC_IP_PROPERTY = "src_ip";
    public static final String SRC_PORT_PROPERTY = "src_port";
    public static final String DST_IP_PROPERTY = "dst_ip";
    public static final String DST_PORT_PROPERTY = "dst_port";
    public static final String PROTO_PROPERTY = "proto";
    public static final String ETH_TYPE_PROPERTY = "eth_type";
    public static final String METADATA_PROPERTY = "metadata";

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract String getFlowId();

    @Override
    @Property(FLOW_ID_PROPERTY)
    public abstract void setFlowId(String flowId);

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setSwitchId(SwitchId switchId);

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(ExclusionCookieConverter.class)
    public abstract ExclusionCookie getCookie();

    @Override
    @Property(COOKIE_PROPERTY)
    @Convert(ExclusionCookieConverter.class)
    public abstract void setCookie(ExclusionCookie cookie);

    @Override
    @Property(SRC_IP_PROPERTY)
    public abstract String getSrcIp();

    @Override
    @Property(SRC_IP_PROPERTY)
    public abstract void setSrcIp(String srcIp);

    @Override
    @Property(SRC_PORT_PROPERTY)
    public abstract int getSrcPort();

    @Override
    @Property(SRC_PORT_PROPERTY)
    public abstract void setSrcPort(int srcPort);

    @Override
    @Property(DST_IP_PROPERTY)
    public abstract String getDstIp();

    @Override
    @Property(DST_IP_PROPERTY)
    public abstract void setDstIp(String dstIp);

    @Override
    @Property(DST_PORT_PROPERTY)
    public abstract int getDstPort();

    @Override
    @Property(DST_PORT_PROPERTY)
    public abstract void setDstPort(int dstPort);

    @Override
    @Property(PROTO_PROPERTY)
    public abstract String getProto();

    @Override
    @Property(PROTO_PROPERTY)
    public abstract void setProto(String proto);

    @Override
    @Property(ETH_TYPE_PROPERTY)
    public abstract String getEthType();

    @Override
    @Property(ETH_TYPE_PROPERTY)
    public abstract void setEthType(String ethType);

    @Override
    @Property(METADATA_PROPERTY)
    public abstract long getMetadata();

    @Override
    @Property(METADATA_PROPERTY)
    public abstract void setMetadata(long metadata);

    @Override
    @Property("expiration_timeout")
    public abstract int getExpirationTimeout();

    @Override
    @Property("expiration_timeout")
    public abstract void setExpirationTimeout(int expirationTimeout);
}
