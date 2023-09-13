export interface TimeToValueMap {
    [timestamp: string]: number;
}

export interface VictoriaData {
    metric: string;
    tags: Record<string, string>;
    status: string;
    timeToValue: TimeToValueMap;
}

export interface VictoriaStatsRes {
    dataList: VictoriaData[];
    status: string;
}
