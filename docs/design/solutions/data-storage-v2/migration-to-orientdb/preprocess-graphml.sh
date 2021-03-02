#!/bin/bash

graphml_file=$1

cp ${graphml_file}{,.bak}

# Add labelV, labelE and discriminator keys definition.
# Replace labels/label data entry keys with labelV/labelE.
# Fix the keys with long type to be int.
# Fix the multi-valued data entries - remove quote marks & squared parentheses.
sed -i -E -e "s/(<graphml[^>]*>)/\1<key id=\"labelV\" for=\"node\" attr\.name=\"labelV\" attr\.type=\"string\"><\/key><key id=\"labelE\" for=\"edge\" attr\.name=\"labelE\" attr\.type=\"string\"><\/key><key id=\"discriminator\" for=\"node\" attr\.name=\"discriminator\"><\/key>/" \
          -e "s/(<node[^<]*<data key=\")labels\">:/\1labelV\">/" \
          -e "s/(<edge[^<]*<data key=\"label)\"/\1E\"/" \
          -e "s/(<key id=\"cost\" for=\"edge\" attr\.name=\"cost\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"up_count\" for=\"node\" attr\.name=\"up_count\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"down_count\" for=\"node\" attr\.name=\"down_count\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"dst_inner_vlan\" for=\"node\" attr\.name=\"dst_inner_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"dst_port\" for=\"edge\" attr\.name=\"dst_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"dst_port\" for=\"node\" attr\.name=\"dst_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"dst_vlan\" for=\"node\" attr\.name=\"dst_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"port_number\" for=\"node\" attr\.name=\"port_number\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"seq_id\" for=\"node\" attr\.name=\"seq_id\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"src_inner_vlan\" for=\"node\" attr\.name=\"src_inner_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"src_port\" for=\"edge\" attr\.name=\"src_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"src_port\" for=\"node\" attr\.name=\"src_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"src_vlan\" for=\"node\" attr\.name=\"src_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"vlan\" for=\"node\" attr\.name=\"vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"vni\" for=\"node\" attr\.name=\"vni\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"port\" for=\"node\" attr\.name=\"port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"discriminator\" for=\"node\" attr\.name=\"discriminator\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"priority\" for=\"node\" attr\.name=\"priority\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"ttl\" for=\"node\" attr\.name=\"ttl\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"server42_port\" for=\"node\" attr\.name=\"server42_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"server42_vlan\" for=\"node\" attr\.name=\"server42_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"inbound_telescope_port\" for=\"node\" attr\.name=\"inbound_telescope_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"outbound_telescope_port\" for=\"node\" attr\.name=\"outbound_telescope_port\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"telescope_ingress_vlan\" for=\"node\" attr\.name=\"telescope_ingress_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"telescope_egress_vlan\" for=\"node\" attr\.name=\"telescope_egress_vlan\" attr\.type=\")long/\1int/" \
          -e "s/(<key id=\"expiration_timeout\" for=\"node\" attr\.name=\"expiration_timeout\" attr\.type=\")long/\1int/" \
          -e "s/(<data[^<]*\[[^<]*)\"meters\"([^<]*\])/\1meters\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"inaccurate_meter\"([^<]*\])/\1inaccurate_meter\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"bfd\"([^<]*\])/\1bfd\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"bfd_review\"([^<]*\])/\1bfd_review\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"group_packet_out_controller\"([^<]*\])/\1group_packet_out_controller\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"reset_counts_flag\"([^<]*\])/\1reset_counts_flag\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"limited_burst_size\"([^<]*\])/\1limited_burst_size\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"noviflow_copy_field\"([^<]*\])/\1noviflow_copy_field\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"pktps_flag\"([^<]*\])/\1pktps_flag\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"match_udp_port\"([^<]*\])/\1match_udp_port\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"max_burst_coefficient_limitation\"([^<]*\])/\1max_burst_coefficient_limitation\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"multi_table\"([^<]*\])/\1multi_table\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"inaccurate_set_vlan_vid_action\"([^<]*\])/\1inaccurate_set_vlan_vid_action\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"noviflow_push_pop_vxlan\"([^<]*\])/\1noviflow_push_pop_vxlan\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"half_size_metadata\"([^<]*\])/\1half_size_metadata\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"noviflow_swap_eth_src_eth_dst\"([^<]*\])/\1noviflow_swap_eth_src_eth_dst\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"telescope\"([^<]*\])/\1telescope\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"TRANSIT_VLAN\"([^<]*\])/\1TRANSIT_VLAN\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"VXLAN\"([^<]*\])/\1VXLAN\2/" \
          -e "s/(<data[^<]*)\[([^<]*)\]/\1\2/" ${graphml_file}
