import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { catchError } from 'rxjs/operators';
import * as moment from 'moment';


@Injectable({
  providedIn: 'root'
})
export class DygraphService {
  numOperator: number;

  private flowPathGraphSource = new Subject<any>(); /*  */
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

  flowPathGraph = this.flowPathGraphSource.asObservable();
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

  changeFlowPathGraphData(pathGraphData) {
    this.flowPathGraphSource.next(pathGraphData);
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

  constructGraphData(data, jsonResponse, startDate, endDate, timezone) {
    this.numOperator = 0;
    let metric1 = '';
    let metric2 = '';
    const direction1 = '';
    const direction2 = '';
    let labels = ['Time', 'X', 'Y'];
    const graphData = [];
    if (typeof startDate !== 'undefined' && startDate != null) {
      const dat = new Date(startDate);
      let startTime = dat.getTime();
      const usedDate = new Date();

      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        startTime = startTime - (usedDate.getTimezoneOffset() * 60 * 1000);
      }
      const arr = [new Date(startTime)];
      if (data && data.length) {
        for (let i = 0; i < data.length; i++) {
          arr.push(null);
        }
      } else {
        arr.push(null); arr.push(null);
      }
      graphData.push(arr);
    }

    if (!jsonResponse) {
      const fDpsObject = typeof data[0] !== 'undefined' ? data[0].dps : {};
      let fDps = [];
      let rDps = [];
      let rDpsObject;
      metric1 = typeof data[0] !== 'undefined' ? data[0].metric : '';
      if (data.length == 2) {
        rDpsObject = typeof data[1] !== 'undefined' ? data[1].dps : {};
        rDps = Object.keys(rDpsObject);
        metric2 = data[1].metric;

        if (data[1].tags.direction) {
          metric2 = data[1].metric + '(' + data[1].tags.direction + ')';
        }
        if (data[0].tags.direction) {
          metric1 = data[0].metric + '(' + data[0].tags.direction + ')';
        }
      }

      fDps = Object.keys(fDpsObject);
      let graphDps = fDps.concat(rDps);
      graphDps.sort();
      graphDps = graphDps.filter((v, i, a) => a.indexOf(v) === i);

      if (graphDps.length <= 0 ) {
        metric1 = 'F';
        metric2 = 'R';
      } else {

        for (let index = 0; index < graphDps.length; index++) {
          const i = graphDps[index];
          this.numOperator = parseInt(i);
          if (fDpsObject[i] == null || typeof fDpsObject[i] == 'undefined') {
            fDpsObject[i] = null;
          } else if (fDpsObject[i] < 0) {
            fDpsObject[i] = 0;
          }

          const temparr = [];
          temparr[0] = new Date(Number(this.numOperator * 1000));
          temparr[1] = fDpsObject[i];
          if (data.length == 2) {
            if (rDpsObject[i] == null || typeof rDpsObject[i] == 'undefined') {
              rDpsObject[i] = null;
            } else if (rDpsObject[i] < 0) {
              rDpsObject[i] = 0;
            }
            temparr[2] = rDpsObject[i];
          }
          graphData.push(temparr);
          this.numOperator++;
        }
      }
      if (metric1 && metric2) {
        labels = ['Time', metric1, metric2];
      } else if (metric1) {
        labels = ['Time', metric1];
      } else {
        labels = ['Time', metric2];
      }
    } else {
      metric1 = 'F';
      metric2 = 'R';
      labels = ['Time', metric1, metric2];
    }

    if (typeof endDate !== 'undefined' && endDate != null) {
      const dat = new Date(endDate);
      let lastTime = dat.getTime();
      const usedDate =
        graphData && graphData.length
          ? new Date(graphData[graphData.length - 1][0])
          : new Date();
      if (typeof timezone !== 'undefined' && timezone == 'UTC') {
        lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      const arr = [new Date(lastTime)];
      if (data && data.length) {
        for (let i = 0; i < data.length; i++) {
          arr.push(null);
        }
      } else {
        arr.push(null); arr.push(null);
      }
      graphData.push(arr);
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

  computeMeterGraphData(data, startDate, endDate, timezone) {
    const maxtrixArray = [];
    const labels = ['Date'];
    const color = [];
    const meterChecked = {};
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
          row.unshift(new Date(Number(parseInt(timestampArray[m]) * 1000)));
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
  computeFlowPathGraphData(data, startDate, endDate, type, timezone, loadfromcookie) {
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
          row.unshift(new Date(Number(parseInt(timestampArray[m]) * 1000)));
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
            // metric = metric + "(switchid=" + data[j].tags.switchid + ", direction="+ direction +", flowid="+data[j].tags['flowid']+")";
            metric = metric + '(flowid=' + data[j].tags['flowid'] + ')';
            labels.push(metric);
            let colorCode = this.getColorCode(j, color);
            if (cookiesChecked && typeof(cookiesChecked[data[j].tags['flowid']]) != 'undefined' && typeof(cookiesChecked[data[j].tags['flowid']][data[j].tags.switchid]) != 'undefined') {
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
          row.unshift(new Date(Number(parseInt(timestampArray[m]) * 1000)));
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
