export interface VictoriaData {
    metric: string;
    tags: Record<string, string>;
    status?: string;
    dps: Record<string, number>;
}

export interface VictoriaStatsRes {
    dataList: VictoriaData[];
    status: string;
}

export interface VictoriaStatsReq {
    metrics?: string[];
    statsType?: string;
    startDate: string;
    endDate: string;
    step: string;
    labels: Record<string, string>;
}
