MATCH (n:flow_event) WHERE apoc.convert.toInteger(n.timestamp)=n.timestamp SET n.timestamp=apoc.date.toISO8601(n.timestamp)
MATCH (n:flow_history) WHERE apoc.convert.toInteger(n.timestamp)=n.timestamp set n.timestamp=apoc.date.toISO8601(n.timestamp)
MATCH (n:port_history) WHERE apoc.convert.toInteger(n.time)=n.time set n.time=apoc.date.toISO8601(n.time)
