#!/bin/bash

graphml_file=$1

cp ${graphml_file}{,.bak}

sed -i -E -e "s/><node/>\n<node/g" \
          -e "s/><edge/>\n<edge/g" \
          -e "s/><key/>\n<key/g" \
          -e "s/><graph/>\n<graph/g" ${graphml_file}

# Replace labelV/labelE data entry keys with labels/label.
# Fix the keys with int type to be long.
sed -i -E -e "s/(<node[^>]*)(><data key=\"label)V\">([^<]*)</\1 labels=\":\3\"\2s\">:\3</" \
          -e "s/(<edge[^>]*)(><data key=\"label)E\">([^<]*)</\1 label=\"\3\"\2\">\3</" \
          -e "s/(<key id=\"cost\" for=\"edge\" attr\.name=\"cost\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"up_count\" for=\"node\" attr\.name=\"up_count\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"down_count\" for=\"node\" attr\.name=\"down_count\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"dst_inner_vlan\" for=\"node\" attr\.name=\"dst_inner_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"dst_port\" for=\"edge\" attr\.name=\"dst_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"dst_port\" for=\"node\" attr\.name=\"dst_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"dst_vlan\" for=\"node\" attr\.name=\"dst_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"port_number\" for=\"node\" attr\.name=\"port_number\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"seq_id\" for=\"node\" attr\.name=\"seq_id\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"src_inner_vlan\" for=\"node\" attr\.name=\"src_inner_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"src_port\" for=\"edge\" attr\.name=\"src_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"src_port\" for=\"node\" attr\.name=\"src_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"src_vlan\" for=\"node\" attr\.name=\"src_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"vlan\" for=\"node\" attr\.name=\"vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"vni\" for=\"node\" attr\.name=\"vni\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"port\" for=\"node\" attr\.name=\"port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"discriminator\" for=\"node\" attr\.name=\"discriminator\")([^>]*)/\1/" \
          -e "s/(<key id=\"priority\" for=\"node\" attr\.name=\"priority\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"ttl\" for=\"node\" attr\.name=\"ttl\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"server42_port\" for=\"node\" attr\.name=\"server42_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"server42_vlan\" for=\"node\" attr\.name=\"server42_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"inbound_telescope_port\" for=\"node\" attr\.name=\"inbound_telescope_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"outbound_telescope_port\" for=\"node\" attr\.name=\"outbound_telescope_port\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"telescope_ingress_vlan\" for=\"node\" attr\.name=\"telescope_ingress_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"telescope_egress_vlan\" for=\"node\" attr\.name=\"telescope_egress_vlan\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"expiration_timeout\" for=\"node\" attr\.name=\"expiration_timeout\" attr\.type=\")int/\1long/" \
          -e "s/(<key id=\"features\" for=\"node\" attr\.name=\"features\" attr\.type=\"string\")>/\1 attr\.list=\"string\">/" \
          -e "s/(<key id=\"supported_transit_encapsulation\" for=\"node\" attr\.name=\"supported_transit_encapsulation\" attr\.type=\"string\")>/\1 attr\.list=\"string\">/" \
          -e "s/(<data[^<]*\[[^<]*)meters([^<]*\])/\1\"meters\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)inaccurate_meter([^<]*\])/\1\"inaccurate_meter\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)bfd([^<]*\])/\1\"bfd\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)bfd_review([^<]*\])/\1\"bfd_review\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)group_packet_out_controller([^<]*\])/\1\"group_packet_out_controller\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)reset_counts_flag([^<]*\])/\1\"reset_counts_flag\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)limited_burst_size([^<]*\])/\1\"limited_burst_size\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)noviflow_copy_field([^<]*\])/\1\"noviflow_copy_field\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)pktps_flag([^<]*\])/\1\"pktps_flag\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)match_udp_port([^<]*\])/\1\"match_udp_port\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)max_burst_coefficient_limitation([^<]*\])/\1\"max_burst_coefficient_limitation\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)multi_table([^<]*\])/\1\"multi_table\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)inaccurate_set_vlan_vid_action([^<]*\])/\1\"inaccurate_set_vlan_vid_action\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)noviflow_push_pop_vxlan([^<]*\])/\1\"noviflow_push_pop_vxlan\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)half_size_metadata([^<]*\])/\1\"half_size_metadata\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)noviflow_swap_eth_src_eth_dst([^<]*\])/\1\"noviflow_swap_eth_src_eth_dst\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)telescope([^<]*\])/\1\"telescope\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)TRANSIT_VLAN([^<]*\])/\1\"TRANSIT_VLAN\"\2/" \
          -e "s/(<data[^<]*\[[^<]*)VXLAN([^<]*\])/\1\"VXLAN\"\2/" ${graphml_file}
