export interface FlowMetricTsdb {
    metric: string;
    tags: Record<string, string>;
    aggregateTags: string[];
    dps: Record<string, number>;
}

