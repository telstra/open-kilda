# About OpenTSDB and VictoriaMetrics read queries

OpenKilda introduces a new time-series database (TSDB) for storing metrics called VictoriaMetrics. It is a high-performance, cost-effective, and scalable TSDB. It is fully compatible with Prometheus and Graphite. It is a drop-in replacement for Prometheus and Graphite. One of the key differences is that OpenTSDB uses a custom query language called [TSL](http://opentsdb.net/docs/build/html/user_guide/query/index.html), while Victoria Metrics uses [MetricsQL](https://docs.victoriametrics.com/MetricsQL.html) which is backwards-compatible with PromQL (Prometheus).

Both databases use a similar data model for storing the metrics - a time-series is a set of key-value pairs, where the key is a metric name and the value is a set of labels. However, the query languages, even though they are similar, and both read queries are based on metric names and labels, are still different.

## Querying examples

The following examples demonstrate how to query the total number of requests for a specific endpoint in the last hour and how to query the average CPU usage for a specific host over the last 24 hours. The examples use the same metric names and labels, but the query languages are different.

**OpenTSDB Query Examples:**

1. Querying the total number of requests for a specific tag_a in the last hour:
```
start=1h-ago&m=sum:requests{tag_a=tag_a_value}
```

2. Querying the average CPU usage for a specific host over the last 24 hours:
```
start=24h-ago&m=avg:cpu{host=webserver01}
```

**Victoria Metrics Query Examples:**

1. Querying the total number of requests for a specific tag_a in the last hour:
```
query=sum(requests{tag_a="tag_a_value"})&time=1h
```

2. Querying the average CPU usage for a specific host over the last 24 hours:
```
query=avg_over_time(cpu{host="webserver01"}[24h])
```

These queries demonstrate how to use the query language for OpenTSDB and Victoria Metrics to retrieve time-series data for a specific metric and time range. The queries use different syntax and functions, but they achieve the same result of retrieving time-series data for a specific metric and time range.