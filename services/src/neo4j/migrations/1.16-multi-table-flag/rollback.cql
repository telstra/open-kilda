MATCH (n:flow) REMOVE n.dst_with_multi_table, n.src_with_multi_table;
MATCH (n:path_segment) REMOVE n.dst_with_multi_table, n.src_with_multi_table;