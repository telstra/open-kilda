match (s:switch)
MERGE (s)-[:has]->(:switch_features {
support_bfd: false,
support_vxlan_push_pop: false,
support_vxlan_vni_match: false
})


DROP CONSTRAINT ON (switch_features:switch_features) ASSERT switch_features.entityId IS UNIQUE