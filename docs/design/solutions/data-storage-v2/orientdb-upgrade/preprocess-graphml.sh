#!/bin/bash

graphml_file=$1

cp "${graphml_file}"{,.bak}

# Fix the keys with string type in the feature toggles to be boolean.
# Fix the keys with string type in the isl to be short.
# Fix the multi-valued data entries - remove quote marks & squared parentheses.
sed -i -E -e "s/(<key id=\"flows_reroute_on_isl_discovery\" for=\"node\" attr\.name=\"flows_reroute_on_isl_discovery\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"create_flow\" for=\"node\" attr\.name=\"create_flow\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"update_flow\" for=\"node\" attr\.name=\"update_flow\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"delete_flow\" for=\"node\" attr\.name=\"delete_flow\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"use_bfd_for_isl_integrity_check\" for=\"node\" attr\.name=\"use_bfd_for_isl_integrity_check\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"floodlight_router_periodic_sync\" for=\"node\" attr\.name=\"floodlight_router_periodic_sync\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"flows_reroute_using_default_encap_type\" for=\"node\" attr\.name=\"flows_reroute_using_default_encap_type\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"collect_grpc_stats\" for=\"node\" attr\.name=\"collect_grpc_stats\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"server42_flow_rtt\" for=\"node\" attr\.name=\"server42_flow_rtt\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"flow_latency_monitoring_reactions\" for=\"node\" attr\.name=\"flow_latency_monitoring_reactions\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"server42_isl_rtt\" for=\"node\" attr\.name=\"server42_isl_rtt\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"modify_y_flow_enabled\" for=\"node\" attr\.name=\"modify_y_flow_enabled\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"sync_switch_on_connect\" for=\"node\" attr\.name=\"sync_switch_on_connect\" attr\.type=\")string/\1boolean/" \
          -e "s/(<key id=\"bfd_multiplier\" for=\"edge\" attr\.name=\"bfd_multiplier\" attr\.type=\")string/\1short/" \
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
          -e "s/(<data[^<]*\[[^<]*)\"groups\"([^<]*\])/\1groups\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"kilda_ovs_copy_field\"([^<]*\])/\1kilda_ovs_copy_field\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"kilda_ovs_swap_field\"([^<]*\])/\1kilda_ovs_swap_field\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"kilda_ovs_push_pop_match_vxlan\"([^<]*\])/\1kilda_ovs_push_pop_match_vxlan\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"lag\"([^<]*\])/\1lag\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"TRANSIT_VLAN\"([^<]*\])/\1TRANSIT_VLAN\2/" \
          -e "s/(<data[^<]*\[[^<]*)\"VXLAN\"([^<]*\])/\1VXLAN\2/" \
          -e "s/(<data[^<]*)\[([^<]*)\]/\1\2/" "${graphml_file}"
