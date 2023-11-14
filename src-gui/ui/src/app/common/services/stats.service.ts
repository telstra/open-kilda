import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {CookieManagerService} from './cookie-manager.service';
import {Observable} from 'rxjs';
import {VictoriaStatsReq, VictoriaStatsRes} from '../data-models/flowMetricVictoria';
import {environment} from '../../../environments/environment';
import * as moment from 'moment';
import {PortInfo} from '../data-models/port-info';
import {concatMap, map} from 'rxjs/operators';


@Injectable({
    providedIn: 'root'
})
export class StatsService {

    constructor(private httpClient: HttpClient, private cookieManager: CookieManagerService) {

    }

    getFlowGraphVictoriaData(statsType: string,
                             flowid: string,
                             convertedStartDate: string,
                             convertedEndDate: string,
                             downsampling: string,
                             metrics: string[],
                             direction?: string): Observable<VictoriaStatsRes> {
        const url = `${environment.apiEndPoint}/stats/flowgraph/${statsType}`;

        // Construct form data
        const formData = new FormData();
        formData.append('flowId', flowid);
        formData.append('startDate', convertedStartDate);
        formData.append('endDate', convertedEndDate);
        formData.append('step', downsampling);
        metrics.forEach(metric => {
            formData.append('metric', metric);
        });
        if (direction && direction.trim() !== '') {
            formData.append('direction', direction);
        }

        // Make the POST request
        return this.httpClient.post<VictoriaStatsRes>(url, formData);
    }

    getFlowPathStats(jsonPayload: VictoriaStatsReq): Observable<VictoriaStatsRes> {
        return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, jsonPayload);
    }

    getSwitchPortsStats(switchId: string): Observable<PortInfo[]> {
        const startDate = moment().utc().subtract(30, 'minutes').format('YYYY-MM-DD-HH:mm:ss');
        const endDate = moment().utc().format('YYYY-MM-DD-HH:mm:ss');

        const requestPayload: VictoriaStatsReq = {
            statsType: 'switchPort',
            startDate: startDate,
            endDate: endDate,
            step: '30s',
            labels: {
                port: '*',
                switchid: switchId
            }
        };
        return this.httpClient.post<PortInfo[]>(
            `${environment.apiEndPoint}/stats/switchports`, requestPayload);
    }

    getPortGraphData(src_switch: string,
                     src_port: string,
                     frequency: string,
                     metric: string,
                     from: string,
                     to: string): Observable<VictoriaStatsRes> {
        const requestPayload: VictoriaStatsReq = {
            metrics: [metric],
            statsType: 'port',
            startDate: from,
            endDate: to,
            step: frequency,
            labels: {
                port: src_port,
                switchid: src_switch
            }
        };
        return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
    }

    getForwardGraphData(
        src_switch: string,
        src_port: string,
        dst_switch: string,
        dst_port: string,
        frequency: string,
        graph: string,
        metric: string,
        from: string,
        to: string
    ): Observable<VictoriaStatsRes> {
        if (graph === 'latency') {
            const requestPayload: VictoriaStatsReq = {
                metrics: ['latency'],
                statsType: 'isl',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    src_port: src_port,
                    src_switch: src_switch,
                    dst_port: dst_port,
                    dst_switch: dst_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }
        if (graph === 'rtt') {
            const requestPayload: VictoriaStatsReq = {
                metrics: ['rtt'],
                statsType: 'isl',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    src_port: src_port,
                    src_switch: src_switch,
                    dst_port: dst_port,
                    dst_switch: dst_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }

        if (graph === 'source') {
            const requestPayload: VictoriaStatsReq = {
                metrics: [metric],
                statsType: 'port',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    port: src_port,
                    switchid: src_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }

        if (graph === 'target') {
            const requestPayload: VictoriaStatsReq = {
                metrics: [metric],
                statsType: 'port',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    port: dst_port,
                    switchid: dst_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }
    }

    getBackwardGraphData(
        src_switch: string,
        src_port: string,
        dst_switch: string,
        dst_port: string,
        frequency: string,
        graph: string,
        from: string,
        to: string
    ): Observable<VictoriaStatsRes> {
        if (graph === 'rtt') {
            const requestPayload: VictoriaStatsReq = {
                metrics: ['rtt'],
                statsType: 'isl',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    src_port: dst_port,
                    src_switch: dst_switch,
                    dst_port: src_port,
                    dst_switch: src_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }
        if (graph === 'latency') {
            const requestPayload: VictoriaStatsReq = {
                metrics: ['latency'],
                statsType: 'isl',
                startDate: from,
                endDate: to,
                step: frequency,
                labels: {
                    src_port: dst_port,
                    src_switch: dst_switch,
                    dst_port: src_port,
                    dst_switch: src_switch
                }
            };
            return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayload);
        }
    }

    getIslLossGraphData(src_switch: string,
                        src_port: string,
                        dst_switch: string,
                        dst_port: string,
                        step: string,
                        graph: string,
                        metric: any,
                        from: string,
                        to: string): Observable<VictoriaStatsRes> {
        const metricList: string[] = [];
        switch (metric) {
            case 'bits':
                metricList.push('switch.rx-bits');
                metricList.push('switch.tx-bits');
                break;
            case 'bytes':
                metricList.push('switch.rx-bytes');
                metricList.push('switch.tx-bytes');
                break;
            case 'packets':
                metricList.push('switch.rx-packets');
                metricList.push('switch.tx-packets');
                break;
            case 'drops':
                metricList.push('switch.rx-dropped');
                metricList.push('switch.tx-dropped');
                break;
            case 'errors':
                metricList.push('switch.rx-errors');
                metricList.push('switch.tx-errors');
                break;
            case 'collisions':
                metricList.push('switch.collisions');
                break;
            case 'frameerror':
                metricList.push('switch.rx-frame-error');
                break;
            case 'overerror':
                metricList.push('switch.rx-over-error');
                break;
            case 'crcerror':
                metricList.push('switch.rx-crc-error');
                break;
        }
        if (graph === 'isllossforward') {
            const requestPayloadRx: VictoriaStatsReq = {
                metrics: [metricList[0]],
                startDate: from,
                endDate: to,
                step: step,
                labels: {
                    switchid: dst_switch,
                    port: dst_port
                }
            };
            if (metricList.length === 2) {
                const res1$ = this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadRx);
                const requestPayloadTx: VictoriaStatsReq = {
                    metrics: [metricList[1]],
                    startDate: from,
                    endDate: to,
                    step: step,
                    labels: {
                        switchid: src_switch,
                        port: src_port
                    }
                };
                const res2$ = this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadTx);
                return res1$.pipe(
                    concatMap(res1 => {
                        return res2$.pipe(
                            map(res2 => {
                                res1.dataList = res1.dataList.concat(res2.dataList);
                                return res1;
                            })
                        );
                    })
                );
            } else if (metricList.length === 1) {
                return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadRx);
            }
        }

        if (graph === 'isllossreverse') {
            const requestPayloadRx: VictoriaStatsReq = {
                metrics: [metricList[0]],
                startDate: from,
                endDate: to,
                step: step,
                labels: {
                    switchid: src_switch,
                    port: src_port
                }
            };
            if (metricList.length === 2) {
                const res1$ = this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadRx);
                const requestPayloadTx: VictoriaStatsReq = {
                    metrics: [metricList[1]],
                    startDate: from,
                    endDate: to,
                    step: step,
                    labels: {
                        switchid: dst_switch,
                        port: dst_port
                    }
                };
                const res2$ = this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadTx);
                return res1$.pipe(
                    concatMap(res1 => {
                        return res2$.pipe(
                            map(res2 => {
                                res1.dataList = res1.dataList.concat(res2.dataList);
                                return res1;
                            })
                        );
                    })
                );
            } else if (metricList.length === 1) {
                return this.httpClient.post<VictoriaStatsRes>(`${environment.apiEndPoint}/stats/common`, requestPayloadRx);
            }
        }
    }
}
