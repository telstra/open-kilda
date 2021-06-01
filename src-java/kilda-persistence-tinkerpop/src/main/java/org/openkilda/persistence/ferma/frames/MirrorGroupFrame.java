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

import org.openkilda.model.GroupId;
import org.openkilda.model.MirrorDirection;
import org.openkilda.model.MirrorGroup.MirrorGroupData;
import org.openkilda.model.MirrorGroupType;
import org.openkilda.model.PathId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.converters.Convert;
import org.openkilda.persistence.ferma.frames.converters.GroupIdConverter;
import org.openkilda.persistence.ferma.frames.converters.MirrorDirectionConverter;
import org.openkilda.persistence.ferma.frames.converters.MirrorGroupTypeConverter;
import org.openkilda.persistence.ferma.frames.converters.PathIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;

import com.syncleus.ferma.FramedGraph;
import com.syncleus.ferma.annotations.Property;

import java.util.List;
import java.util.Optional;

public abstract class MirrorGroupFrame extends KildaBaseVertexFrame implements MirrorGroupData {
    public static final String FRAME_LABEL = "mirror_group";
    public static final String SWITCH_ID_PROPERTY = "switch_id";
    public static final String PATH_ID_PROPERTY = "path_id";
    public static final String GROUP_ID_PROPERTY = "group_id";
    public static final String MIRROR_GROUP_TYPE_PROPERTY = "mirror_group_type";
    public static final String MIRROR_DIRECTION_PROPERTY = "mirror_direction";

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract SwitchId getSwitchId();

    @Override
    @Property(SWITCH_ID_PROPERTY)
    @Convert(SwitchIdConverter.class)
    public abstract void setSwitchId(SwitchId switchId);

    @Override
    @Property(GROUP_ID_PROPERTY)
    @Convert(GroupIdConverter.class)
    public abstract GroupId getGroupId();

    @Override
    @Property(GROUP_ID_PROPERTY)
    @Convert(GroupIdConverter.class)
    public abstract void setGroupId(GroupId groupId);

    @Override
    @Property("flow_id")
    public abstract String getFlowId();

    @Override
    @Property("flow_id")
    public abstract void setFlowId(String flowId);

    @Override
    @Property(MIRROR_GROUP_TYPE_PROPERTY)
    @Convert(MirrorGroupTypeConverter.class)
    public abstract MirrorGroupType getMirrorGroupType();

    @Override
    @Property(MIRROR_GROUP_TYPE_PROPERTY)
    @Convert(MirrorGroupTypeConverter.class)
    public abstract void setMirrorGroupType(MirrorGroupType mirrorGroupType);

    @Override
    @Property(MIRROR_DIRECTION_PROPERTY)
    @Convert(MirrorDirectionConverter.class)
    public abstract MirrorDirection getMirrorDirection();

    @Override
    @Property(MIRROR_DIRECTION_PROPERTY)
    @Convert(MirrorDirectionConverter.class)
    public abstract void setMirrorDirection(MirrorDirection mirrorDirection);

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract PathId getPathId();

    @Override
    @Property(PATH_ID_PROPERTY)
    @Convert(PathIdConverter.class)
    public abstract void setPathId(PathId pathId);

    public static Optional<MirrorGroupFrame> load(FramedGraph graph, String switchId, String pathId) {
        List<? extends MirrorGroupFrame> mirrorGroupFrames = graph.traverse(input -> input.V()
                .hasLabel(FRAME_LABEL)
                .has(SWITCH_ID_PROPERTY, switchId)
                .has(PATH_ID_PROPERTY, pathId))
                .toListExplicit(MirrorGroupFrame.class);
        return mirrorGroupFrames.isEmpty() ? Optional.empty() : Optional.of(mirrorGroupFrames.get(0));
    }
}
