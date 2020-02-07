CREATE CONSTRAINT ON (`switch_features`:`switch_features`) ASSERT `switch_features`.`entityId` IS UNIQUE;
MATCH (n:switch_properties) SET n:switch_features REMOVE n:switch_properties;
MATCH (n:switch_features) SET n.support_bfd = false, n.support_vxlan_push_pop = false,
            n.support_vxlan_vni_match = false;
MATCH (n:switch_features) REMOVE n.multi_table;
DROP CONSTRAINT ON ( switch_properties:switch_properties ) ASSERT switch_properties.entityId IS UNIQUE;