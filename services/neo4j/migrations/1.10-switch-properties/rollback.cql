MATCH (n:switch_properties) SET n:switch_features REMOVE n:switch_properties
MATCH (n:switch_features) SET n.support_bfd = false, n.support_vxlan_push_pop = false,
            n.support_vxlan_vni_match = false;