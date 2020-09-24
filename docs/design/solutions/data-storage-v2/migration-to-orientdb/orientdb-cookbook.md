# OrientDB cookbook

Useful recipes/queries to extract data using Tinkerpop or OrientDB queries

### Finding the shortest path between 2 switches
```
SELECT expand(path) FROM (
  SELECT shortestPath($from, $to) AS path 
  LET 
    $from = (SELECT FROM switch WHERE name=$start_switch), 
    $to = (SELECT FROM switch WHERE name=$end_switch) 
  UNWIND path
)
```

Or in *Gremlin*:
```
g.V().hasLabel("switch").has("name", "$start_switch").repeat(__.outE("isl").has("status", "active").inV().hasLabel("switch").simplePath()).until(__.has("name", "$end_switch").or().loops().is(10)).has("name", "$end_switch").path()
```

### Showing a flow path
```
SELECT EXPAND(UNIONALL(@rid, out("source"), out("destination"), in("owns"))) FROM path_segment WHERE in("owns").flow_id = $flow_id
```

### Finding flows which go over a link
```
SELECT EXPAND(in("owns").in("owns")) FROM path_segment WHERE src_switch_id = $src_switch AND src_port = $src_port AND dst_switch_id = $dst_switch AND dst_port = $dst_port
```

### Finding flows which go over a switch
```
SELECT EXPAND(in("owns")) FROM flow_path WHERE src_switch_id = $switch OR dst_switch_id = $switch OR out("owns").src_switch_id = $switch OR out("owns").dst_switch_id = $switch 
```

### Finding flows which terminate on a switch
```
SELECT FROM flow WHERE src_switch_id = $switch OR dst_switch_id = $switch 
```
