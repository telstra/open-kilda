import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import {FlowMetricTsdb} from '../data-models/flowMetricTsdb';
import {VictoriaData} from '../data-models/flowMetricVictoria';


@Injectable({
  providedIn: 'root'
})
export class DygraphService {
  numOperator: number;
  private flowGraphSource = new Subject<any>();
  private meterGraphSource = new Subject<any>();

  private metrices = [
    'bits:Bits/sec',
    'bytes:Bytes/sec',
    'packets:Packets/sec',
    'drops:Drops/sec',
    'errors:Errors/sec',
    'collisions:Collisions',
    'frameerror:Frame Errors',
    'overerror:Overruns',
    'crcerror:CRC Errors'
  ];
  flowGraph = this.flowGraphSource.asObservable();
  meterGraph = this.meterGraphSource.asObservable();

  constructor(private httpClient: HttpClient) {}

  getForwardGraphData(
      src_switch,
      src_port,
      dst_switch,
      dst_port,
      frequency,
      graph,
      menu,
      from,
      to
  ): Observable<any[]> {
    if (graph === 'latency') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/latency`
      );
    }
    if (graph === 'rtt') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/rtt`
      );
    }

    if (graph === 'source') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/switchid/${src_switch}/port/${src_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === 'target') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/switchid/${dst_switch}/port/${dst_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === 'isllossforward') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/losspackets/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === 'isllossreverse') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/losspackets/${dst_switch}/${dst_port}/${src_switch}/${src_port}/${from}/${to}/${frequency}/${menu}`
      );
    }
  }
  getBackwardGraphData(
      src_switch,
      src_port,
      dst_switch,
      dst_port,
      frequency,
      graph,
      from,
      to
  ): Observable<any[]> {
    if (graph === 'rtt') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/${dst_switch}/${dst_port}/${src_switch}/${src_port}/${from}/${to}/${frequency}/rtt`
      );
    }
    if (graph == 'latency') {
      return this.httpClient.get<any[]>(
          `${
              environment.apiEndPoint
          }/stats/isl/${dst_switch}/${dst_port}/${src_switch}/${src_port}/${from}/${to}/${frequency}/latency`
      );
    }

  }
  changeMeterGraphData(graphData) {
    this.meterGraphSource.next(graphData);
  }

  changeFlowGraphData(graphData) {
    this.flowGraphSource.next(graphData);
  }

  getFlowMetricData() {
    const metricArray = this.metrices;
    const tempArray = [];
    for (let i = 0; i < metricArray.length; i++) {
      if (
          metricArray[i].includes('bits') ||
          metricArray[i].includes('packets') ||
          metricArray[i].includes('bytes')
      ) {
        tempArray.push({
          label: metricArray[i].split(':')[1],
          value: metricArray[i].split(':')[0]
        });
      }
    }

    return tempArray;
  }

  getPortMetricData() {
    const metricArray = this.metrices;
    const tempArray = [];
    for (let i = 0; i < metricArray.length; i++) {
      if (
          metricArray[i].includes('bytes') ||
          metricArray[i].includes('latency')
      ) {
      } else {
        tempArray.push({
          label: metricArray[i].split(':')[1],
          value: metricArray[i].split(':')[0]
        });
      }
    }
    return tempArray;
  }

  getPacketsMetricData() {
    return [
      { label: 'Forward', value: 'forward' },
      { label: 'Reverse', value: 'reverse' }
    ];
  }

  getMetricDirections() {
    return [
      { label: 'Both', value: 'both' },
      { label: 'Forward', value: 'forward' },
      { label: 'Reverse', value: 'reverse' }
    ];
  }

  constructVictoriaGraphData(victoriaDataArr: VictoriaData[], startDate: string, endDate: string, timezone: string) {
    this.numOperator = 0;
    let fwdMetricDirectionLbl: string;
    let rvsMetricDirectionlbl: string;
    let labels: string[];
    const graphData: any[] = [];

    const addNullsToArray = (array: any[], count: number) => {
      const nulls = new Array(count).fill(null);
      return [...array, ...nulls];
    };

    // Handle start date
    const startTime = this.parseDate(startDate, timezone === 'UTC' ? new Date().getTimezoneOffset() : 0);
    if (startTime !== null) {
      graphData.push([startTime, ...addNullsToArray([], victoriaDataArr.length || 2)]);
    }

    const fwdTimeToValueMap = victoriaDataArr[0] && victoriaDataArr[0].timeToValue ? victoriaDataArr[0].timeToValue : {};
    const fwdTimeStamps = Object.keys(fwdTimeToValueMap);
    const rvsTimeToValueMap = victoriaDataArr[1] && victoriaDataArr[1].timeToValue ? victoriaDataArr[1].timeToValue : {};
    const rvsTimeStamps = Object.keys(rvsTimeToValueMap);
    fwdMetricDirectionLbl = victoriaDataArr[0] &&  victoriaDataArr[0].tags.direction
        ? `${victoriaDataArr[0].metric}(${victoriaDataArr[0].tags.direction})` : victoriaDataArr[0] ? victoriaDataArr[0].metric : '';
    rvsMetricDirectionlbl = victoriaDataArr[1] && victoriaDataArr[1].tags.direction
        ? `${victoriaDataArr[1].metric}(${victoriaDataArr[1].tags.direction})` : victoriaDataArr[1] ? victoriaDataArr[1].metric : '';

    const timeStamps = [...fwdTimeStamps, ...rvsTimeStamps].filter((v, i, a) => a.indexOf(v) === i).sort();

    if (timeStamps.length <= 0) {
      fwdMetricDirectionLbl = 'F';
      rvsMetricDirectionlbl = 'R';
    } else {
      for (const timeStamp of timeStamps) {
        this.numOperator = parseInt(timeStamp, 10);
        let fwdValue = fwdTimeToValueMap[timeStamp] !== undefined ? fwdTimeToValueMap[timeStamp] : null;
        let rvsValue = rvsTimeToValueMap[timeStamp] !== undefined ? rvsTimeToValueMap[timeStamp] : null;

        if (fwdValue !== null && fwdValue < 0) {
          fwdValue = 0;
        }

        if (rvsValue !== null && rvsValue < 0) {
          rvsValue = 0;
        }

        const tmpArr = [new Date(this.numOperator * 1000), fwdValue];

        if (rvsTimeStamps.length > 0) {
          tmpArr.push(rvsValue);
        }

        graphData.push(tmpArr);
        this.numOperator++;
      }
    }

    labels = [ 'Time', fwdMetricDirectionLbl, rvsMetricDirectionlbl ];

    // Handle end date
    const endTime = this.parseDate(endDate, timezone === 'UTC' ? graphData[graphData.length - 1][0].getTimezoneOffset() : 0);
    if (endTime !== null) {
      graphData.push([endTime, ...addNullsToArray([], victoriaDataArr.length || 2)]);
    }

    return { data: graphData, labels: labels };
  }

  constructGraphData(data: FlowMetricTsdb[], jsonResponse: boolean, startDate: string, endDate: string, timezone: string) {
    this.numOperator = 0;
    let metric1: string;
    let metric2: string;
    let labels: string[];
    const graphData: any[] = [];

    const addNullsToArray = (array: any[], count: number) => {
      const nulls = new Array(count).fill(null);
      return [...array, ...nulls];
    };

    // Handle start date
    const startTime = this.parseDate(startDate, timezone === 'UTC' ? new Date().getTimezoneOffset() : 0);
    if (startTime !== null) {
      graphData.push([startTime, ...addNullsToArray([], data.length || 2)]);
    }

    if (!jsonResponse) {
      const fDpsObject = data[0] && data[0].dps ? data[0].dps : {};
      const fDps = Object.keys(fDpsObject);
      const rDpsObject = data[1] && data[1].dps ? data[1].dps : {};
      const rDps = Object.keys(rDpsObject);
      metric1 = data[0] && data[0].tags && data[0].tags.direction
          ? `${data[0].metric}(${data[0].tags.direction})` : data[0] ? data[0].metric : '';
      metric2 = data[1] && data[1].tags && data[1].tags.direction
          ? `${data[1].metric}(${data[1].tags.direction})` : data[1] ? data[1].metric : '';

      const graphDps = [...fDps, ...rDps].filter((v, i, a) => a.indexOf(v) === i).sort();

      if (graphDps.length <= 0) {
        metric1 = 'F';
        metric2 = 'R';
      } else {
        for (const i of graphDps) {
          this.numOperator = parseInt(i, 10);
          let fDpsValue = fDpsObject[i] !== undefined ? fDpsObject[i] : null;
          let rDpsValue = rDpsObject[i] !== undefined ? rDpsObject[i] : null;

          if (fDpsValue !== null && fDpsValue < 0) {
            fDpsValue = 0;
          }

          if (rDpsValue !== null && rDpsValue < 0) {
            rDpsValue = 0;
          }

          const temparr = [new Date(this.numOperator * 1000), fDpsValue];

          if (rDps.length > 0) {
            temparr.push(rDpsValue);
          }

          graphData.push(temparr);
          this.numOperator++;
        }
      }

      labels = [ 'Time', metric1, metric2 ];
    } else {
      metric1 = 'F';
      metric2 = 'R';
      labels = [ 'Time', metric1, metric2 ];
    }

    // Handle end date
    const endTime = this.parseDate(endDate, timezone === 'UTC' ? graphData[graphData.length - 1][0].getTimezoneOffset() : 0);
    if (endTime !== null) {
      graphData.push([endTime, ...addNullsToArray([], data.length || 2)]);
    }

    return { data: graphData, labels: labels };
  }



  getColorCode(j, arr) {
    const chars = '0123456789ABCDE'.split('');
    let hex = '#';
    for (let i = 0; i < 6; i++) {
      hex += chars[Math.floor(Math.random() * 14)];
    }
    const colorCode = hex;
    if (arr.indexOf(colorCode) < 0) {
      return colorCode;
    } else {
      this.getColorCode(j, arr);
    }
  }
  getCookieBasedData(data, type) {
    const constructedData = {};
    for (let i = 0; i < data.length; i++) {
      const cookieId = data[i].tags && data[i].tags['cookie'] ? data[i].tags['cookie'] : null;
      if (cookieId) {
        const keyArray = Object.keys(constructedData);
        if (keyArray.indexOf(cookieId) > -1) {
          constructedData[cookieId].push(data[i]);
        } else {
          if (type == 'forward' && cookieId.charAt(0) == '4') {
            constructedData[cookieId] = [];
            constructedData[cookieId].push(data[i]);
          } else if (type == 'reverse' && cookieId.charAt(0) == '2' ) {
            constructedData[cookieId] = [];
            constructedData[cookieId].push(data[i]);
          }
        }
      }
    }

    return constructedData;
  }

  getCookieDataforFlowStats(data, type) {
    const constructedData = [];
    for (let i = 0; i < data.length; i++) {
      const cookieId = data[i].tags && data[i].tags['cookie'] ? data[i].tags['cookie'] : null;
      if (cookieId) {
        if (type == 'forward' && cookieId.charAt(0) == '4') {
          constructedData.push(data[i]);
        } else if (type == 'reverse' && cookieId.charAt(0) == '2' ) {
          constructedData.push(data[i]);
        }
      }
    }
    return constructedData;
  }

  private parseDate(date: string, offset: number): Date | null {
    if (date !== undefined && date !== null) {
      const parsedDate = new Date(date);
      const time = parsedDate.getTime() - (offset * 60 * 1000);
      return new Date(time);
    }
    return null;
  }

  constructVictoriaMeterGraphData(victoriaDataArr: VictoriaData[], startDate, endDate, timezone) {
    let arr;
    const graphData = [];
    const labels = ['Date'];
    const color = [];
    const meterChecked = {};

    const startTime = this.parseDate(startDate, timezone === 'UTC' ? new Date().getTimezoneOffset() : 0);
    if (startTime !== null) {
      arr = [new Date(startTime)];
      if (victoriaDataArr && victoriaDataArr.length) {
        for (let j = 0; j < victoriaDataArr.length; j++) {
          arr.push(null);
        }
      }
      graphData.push(arr);
    }

    /** process graph data */

    if (victoriaDataArr) {
      if (victoriaDataArr.length > 0) {

        /**getting all unique dps timestamps */
        let timestampArray = [];
        const dpsArray = [];
        for (let j = 0; j < victoriaDataArr.length; j++) {
          const dataValues = typeof victoriaDataArr[j] !== 'undefined' ? victoriaDataArr[j].timeToValue : null;

          let metric = typeof victoriaDataArr[j] !== 'undefined' ? victoriaDataArr[j].metric : '';
          metric = metric + '(switchid=' + victoriaDataArr[j].tags.switchid + ', meterid=' + victoriaDataArr[j].tags['meterid'] + ')';
          labels.push(metric);
          let colorCode = this.getColorCode(j, color);
          if (meterChecked && typeof(meterChecked[victoriaDataArr[j].tags['meterid']]) !== 'undefined'
              && typeof(meterChecked[victoriaDataArr[j].tags['meterid']][victoriaDataArr[j].tags.switchid]) !== 'undefined') {
            colorCode = meterChecked[victoriaDataArr[j].tags['meterid']][victoriaDataArr[j].tags.switchid];
            color.push(colorCode);
          } else {
            if (meterChecked && typeof(meterChecked[victoriaDataArr[j].tags['meterid']]) !== 'undefined') {
              meterChecked[victoriaDataArr[j].tags['meterid']][victoriaDataArr[j].tags.switchid] = colorCode;
              color.push(colorCode);
            } else {
              meterChecked[victoriaDataArr[j].tags['meterid']] = [];
              meterChecked[victoriaDataArr[j].tags['meterid']][victoriaDataArr[j].tags.switchid] = colorCode;
              color.push(colorCode);
            }

          }
          if (dataValues) {
            timestampArray = timestampArray.concat(Object.keys(dataValues));
            dpsArray.push(dataValues);
          }

        }

        timestampArray = Array.from(new Set(timestampArray)); /**Extracting unique timestamps */
        timestampArray.sort();

        for (let m = 0; m < timestampArray.length; m++) {
          const row = [];
          for (let n = 0; n < dpsArray.length; n++) {
            if (typeof dpsArray[n][timestampArray[m]] != 'undefined') {
              row.push(dpsArray[n][timestampArray[m]]);
            } else {
              row.push(null);
            }
          }
          row.unshift(new Date(Number(parseInt(timestampArray[m], 10) * 1000)));
          graphData.push(row);
        }
      }
    }

    const endTime = this.parseDate(endDate, timezone === 'UTC' ? graphData[graphData.length - 1][0].getTimezoneOffset() : 0);
    if (endTime !== null) {
      arr = [new Date(endTime)];
      if (victoriaDataArr && victoriaDataArr.length) {
        for (let j = 0; j < victoriaDataArr.length; j++) {
          arr.push(null);
        }
      }
      graphData.push(arr);
    }

    return { labels: labels, data: graphData, color: color };
  }

  constructMeterGraphData(data: FlowMetricTsdb[], startDate, endDate, timezone) {
    let arr;
    const graphData = [];
    const labels = ['Date'];
    const color = [];
    const meterChecked = {};

    const startTime = this.parseDate(startDate, timezone === 'UTC' ? new Date().getTimezoneOffset() : 0);
    if (startTime !== null) {
      arr = [new Date(startTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
      graphData.push(arr);
    }

    /** process graph data */

    if (data) {
      if (data.length > 0) {

        /**getting all unique dps timestamps */
        let timestampArray = [];
        const dpsArray = [];
        for (let j = 0; j < data.length; j++) {
          const dataValues = typeof data[j] !== 'undefined' ? data[j].dps : null;

          let metric = typeof data[j] !== 'undefined' ? data[j].metric : '';
          metric = metric + '(switchid=' + data[j].tags.switchid + ', meterid=' + data[j].tags['meterid'] + ')';
          labels.push(metric);
          let colorCode = this.getColorCode(j, color);
          if (meterChecked && typeof(meterChecked[data[j].tags['meterid']]) != 'undefined' && typeof(meterChecked[data[j].tags['meterid']][data[j].tags.switchid]) != 'undefined') {
            colorCode = meterChecked[data[j].tags['meterid']][data[j].tags.switchid];
            color.push(colorCode);
          } else {
            if (meterChecked && typeof(meterChecked[data[j].tags['meterid']]) != 'undefined') {
              meterChecked[data[j].tags['meterid']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            } else {
              meterChecked[data[j].tags['meterid']] = [];
              meterChecked[data[j].tags['meterid']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            }

          }
          if (dataValues) {
            timestampArray = timestampArray.concat(Object.keys(dataValues));
            dpsArray.push(dataValues);
          }

        }

        timestampArray = Array.from(new Set(timestampArray)); /**Extracting unique timestamps */
        timestampArray.sort();

        for (let m = 0; m < timestampArray.length; m++) {
          const row = [];
          for (let n = 0; n < dpsArray.length; n++) {
            if (typeof dpsArray[n][timestampArray[m]] != 'undefined') {
              row.push(dpsArray[n][timestampArray[m]]);
            } else {
              row.push(null);
            }
          }
          row.unshift(new Date(Number(parseInt(timestampArray[m], 10) * 1000)));
          graphData.push(row);
        }
      }
    }

    const endTime = this.parseDate(endDate, timezone === 'UTC' ? graphData[graphData.length - 1][0].getTimezoneOffset() : 0);
    if (endTime !== null) {
      arr = [new Date(endTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
      graphData.push(arr);
    }

    return { labels: labels, data: graphData, color: color };
  }

  computeFlowPathGraphData(data, startDate, endDate, type, timezone) {
    const maxtrixArray = [];
    const labels = ['Date'];
    const color = [];
    const cookiesChecked = {};
    if (typeof startDate !== 'undefined' && startDate != null) {
      const dat = new Date(startDate);
      let startTime = dat.getTime();
      const usedDate = new Date();
      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      const arr = [new Date(startTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
      maxtrixArray.push(arr);
    }

    if (data) {
      if (data.length > 0) {

        /**getting all unique dps timestamps */
        let timestampArray = [];
        const dpsArray = [];
        for (let j = 0; j < data.length; j++) {
          const dataValues = typeof data[j] !== 'undefined' ? data[j].dps : null;

          let metric = typeof data[j] !== 'undefined' ? data[j].metric : '';
          metric = metric + '(switchid=' + data[j].tags.switchid + ', cookie=' + data[j].tags['cookie'] + ')';
          labels.push(metric);
          let colorCode = this.getColorCode(j, color);
          if (cookiesChecked && typeof(cookiesChecked[data[j].tags['cookie']]) != 'undefined' && typeof(cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid]) != 'undefined') {
            colorCode = cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid];
            color.push(colorCode);
          } else {
            if (cookiesChecked && typeof(cookiesChecked[data[j].tags['cookie']]) != 'undefined') {
              cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            } else {
              cookiesChecked[data[j].tags['cookie']] = [];
              cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            }

          }
          if (dataValues) {
            timestampArray = timestampArray.concat(Object.keys(dataValues));
            dpsArray.push(dataValues);
          }

        }

        timestampArray = Array.from(new Set(timestampArray)); /**Extracting unique timestamps */
        timestampArray.sort();

        for (let m = 0; m < timestampArray.length; m++) {
          const row = [];
          for (let n = 0; n < dpsArray.length; n++) {
            if (typeof dpsArray[n][timestampArray[m]] != 'undefined') {
              row.push(dpsArray[n][timestampArray[m]]);
            } else {
              row.push(null);
            }
          }
          row.unshift(new Date(Number(parseInt(timestampArray[m], 10) * 1000)));
          maxtrixArray.push(row);
        }
      }
    }
    if (typeof endDate !== 'undefined' && endDate != null) {
      const dat = new Date(endDate);
      let lastTime = dat.getTime();
      const usedDate =
          maxtrixArray && maxtrixArray.length
              ? new Date(maxtrixArray[maxtrixArray.length - 1][0])
              : new Date();
      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      const arr = [new Date(lastTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }

      maxtrixArray.push(arr);
    }
    return { labels: labels, data: maxtrixArray, color: color };
  }

  computeFlowGraphDataForISL(data, startDate, endDate, timezone, direction) {
    const maxtrixArray = [];
    const labels = ['Date'];
    const color = [];
    const cookiesChecked = {};
    if (typeof startDate !== 'undefined' && startDate != null) {
      const dat = new Date(startDate);
      let startTime = dat.getTime();
      const usedDate = new Date();
      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      const arr = [new Date(startTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
      maxtrixArray.push(arr);
    }

    if (data) {
      if (data.length > 0) {

        /**getting all unique dps timestamps */
        let timestampArray = [];
        const dpsArray = [];
        for (let j = 0; j < data.length; j++) {
          const dataValues = typeof data[j] !== 'undefined' ? data[j].dps : null;

          let metric = typeof data[j] !== 'undefined' ? data[j].metric : '';
          metric = metric + '(flowid=' + data[j].tags['flowid'] + ')';
          labels.push(metric);
          let colorCode = this.getColorCode(j, color);
          if (cookiesChecked && typeof(cookiesChecked[data[j].tags['flowid']]) != 'undefined'
              && typeof(cookiesChecked[data[j].tags['flowid']][data[j].tags.switchid]) != 'undefined') {
            colorCode = cookiesChecked[data[j].tags['flowid']][data[j].tags.switchid];
            color.push(colorCode);
          } else {
            if (cookiesChecked && typeof(cookiesChecked[data[j].tags['flowid']]) != 'undefined') {
              cookiesChecked[data[j].tags['flowid']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            } else {
              cookiesChecked[data[j].tags['flowid']] = [];
              cookiesChecked[data[j].tags['flowid']][data[j].tags.switchid] = colorCode;
              color.push(colorCode);
            }
          }
          if (dataValues) {
            timestampArray = timestampArray.concat(Object.keys(dataValues));
            dpsArray.push(dataValues);
          }

        }

        timestampArray = Array.from(new Set(timestampArray)); /**Extracting unique timestamps */
        timestampArray.sort();

        for (let m = 0; m < timestampArray.length; m++) {
          const row = [];
          for (let n = 0; n < dpsArray.length; n++) {
            if (typeof dpsArray[n][timestampArray[m]] != 'undefined') {
              row.push(dpsArray[n][timestampArray[m]]);
            } else {
              row.push(null);
            }
          }
          row.unshift(new Date(Number(parseInt(timestampArray[m], 10) * 1000)));
          maxtrixArray.push(row);
        }
      }
    }
    if (typeof endDate !== 'undefined' && endDate != null) {
      const dat = new Date(endDate);
      let lastTime = dat.getTime();
      const usedDate =
          maxtrixArray && maxtrixArray.length
              ? new Date(maxtrixArray[maxtrixArray.length - 1][0])
              : new Date();
      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      const arr = [new Date(lastTime)];
      if (data && data.length) {
        for (let j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }

      maxtrixArray.push(arr);
    }
    return { labels: labels, data: maxtrixArray, color: color };
  }

  legendFormatter(data) {
    if (data.x == null) {
      return '<br>' + data.series.map(function(series) { return series.dashHTML + ' ' + series.labelHTML; }).join('<br>');
    }

    let html = data.xHTML;
    data.series.forEach(function(series) {
      if (!series.isVisible) { return; }
      let labeledData = '';
      if (series.yHTML && series.yHTML != 'undefined' && series.yHTML != null) {
        labeledData = '<span style=\'color:' + series.color + '\'>' + series.labelHTML + ': ' + series.yHTML + '</span>';
      }
      if (labeledData.trim() != '') {
        if (series.isHighlighted) {
          labeledData = '<b>' + labeledData + '</b>';
        }
        html += '<br>' + labeledData;
      }

    });
    return html;
  }

}
