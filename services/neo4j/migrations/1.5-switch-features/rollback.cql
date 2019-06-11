DROP INDEX ON :vxlan(path_id)
DROP CONSTRAINT ON (switch_features:switch_features) ASSERT switch_features.entityId IS UNIQUE
DROP CONSTRAINT ON (vxlan:vxlan) ASSERT vxlan.vni IS UNIQUE
DROP CONSTRAINT ON (vxlan:vxlan) ASSERT vxlan.entityId IS UNIQUE