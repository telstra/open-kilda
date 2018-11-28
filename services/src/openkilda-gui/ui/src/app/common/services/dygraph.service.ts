import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { environment } from "../../../environments/environment";
import { Observable, Subject, BehaviorSubject } from "rxjs";
import { catchError } from "rxjs/operators";


@Injectable({
  providedIn: "root"
})
export class DygraphService {
  numOperator: number;

  private flowPathGraphSource = new Subject<any>(); /*  */;
  private flowGraphSource = new Subject<any>();



  private metrices = [
    "bits:Bits/sec",
    "bytes:Bytes/sec",
    "packets:Packets/sec",
    "drops:Drops/sec",
    "errors:Errors/sec",
    "collisions:Collisions",
    "frameerror:Frame Errors",
    "overerror:Overruns",
    "crcerror:CRC Errors"
  ];

  flowPathGraph = this.flowPathGraphSource.asObservable();
  flowGraph = this.flowGraphSource.asObservable();
 

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
    if (graph === "latency") {
      return this.httpClient.get<any[]>(
        `${
          environment.apiEndPoint
        }/stats/isl/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/latency`
      );
    }

    if (graph === "source") {
      return this.httpClient.get<any[]>(
        `${
          environment.apiEndPoint
        }/stats/switchid/${src_switch}/port/${src_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === "target") {
      return this.httpClient.get<any[]>(
        `${
          environment.apiEndPoint
        }/stats/switchid/${dst_switch}/port/${dst_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === "isllossforward") {
      return this.httpClient.get<any[]>(
        `${
          environment.apiEndPoint
        }/stats/isl/losspackets/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/${menu}`
      );
    }

    if (graph === "isllossreverse") {
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
    return this.httpClient.get<any[]>(
      `${
        environment.apiEndPoint
      }/stats/isl/${dst_switch}/${dst_port}/${src_switch}/${src_port}/${from}/${to}/${frequency}/latency`
    );
  }

  changeFlowPathGraphData(pathGraphData) {
    this.flowPathGraphSource.next(pathGraphData);
  }

  changeFlowGraphData(graphData) {
    this.flowGraphSource.next(graphData);
  }

  getFlowMetricData() {
    let metricArray = this.metrices;
    let tempArray = [];
    for (var i = 0; i < metricArray.length; i++) {
      if (
        metricArray[i].includes("bits") ||
        metricArray[i].includes("packets") ||
        metricArray[i].includes("bytes")
      ) {
        tempArray.push({
          label: metricArray[i].split(":")[1],
          value: metricArray[i].split(":")[0]
        });
      }
    }

    return tempArray;
  }

  getPortMetricData() {
    let metricArray = this.metrices;
    let tempArray = [];
    for (var i = 0; i < metricArray.length; i++) {
      if (
        metricArray[i].includes("bytes") ||
        metricArray[i].includes("latency")
      ) {
      } else {
        tempArray.push({
          label: metricArray[i].split(":")[1],
          value: metricArray[i].split(":")[0]
        });
      }
    }
    return tempArray;
  }

  getPacketsMetricData() {
    return [
      { label: "Forward", value: "forward" },
      { label: "Reverse", value: "reverse" }
    ];
  }

  constructGraphData(data, jsonResponse, startDate, endDate, timezone) {
    this.numOperator = 0;
    var metric1 = "";
    var metric2 = "";
    var direction1 = "";
    var direction2 = "";
    var labels = ["Time", "X", "Y"];
    var graphData = [];
    if (typeof startDate !== "undefined" && startDate != null) {
      var dat = new Date(startDate);
      var startTime = dat.getTime();
      var usedDate = new Date();

      if (typeof timezone !== "undefined" && timezone == "UTC") {
        startTime = startTime - (usedDate.getTimezoneOffset() * 60 * 1000);
      } 
      var arr = [new Date(startTime)];
      if(data && data.length){
        for(let i=0; i < data.length; i++){
          arr.push(null);
        }
      }else{
        arr.push(null); arr.push(null);
      }
      graphData.push(arr);
    }

    if (!jsonResponse) {
      var getValue = typeof data[0] !== "undefined" ? data[0].dps : 0;
      metric1 = typeof data[0] !== "undefined" ? data[0].metric : "";
      if (data.length == 2) {
        var getVal = data[1].dps;
        metric2 = data[1].metric;

        if (data[1].tags.direction) {
          metric2 = data[1].metric + "(" + data[1].tags.direction + ")";
        }
        if (data[0].tags.direction) {
          metric1 = data[0].metric + "(" + data[0].tags.direction + ")";
        }
      }
      if (!getValue) {
        metric1 = "F";
        metric2 = "R";
      } else {
        for (let i in getValue) {
          this.numOperator = parseInt(i);
          if (getValue[i] < 0 || getValue[i] == null) {
            continue;
          }
          if ( data.length == 2 &&
            typeof getVal[i] !== "undefined" &&
            (getVal[i] < 0 || getVal[i] == null)
          ) {
            continue;
          }
          var temparr = [];
          temparr[0] = new Date(Number(this.numOperator * 1000));
          temparr[1] = getValue[i];
          if (data.length == 2) {
            temparr[2] = getVal[i];
          }
          graphData.push(temparr);
          this.numOperator++;
        }
      }
      if (metric1 && metric2) {
        labels = ["Time", metric1, metric2];
      } else if (metric1) {
        labels = ["Time", metric1];
      } else {
        labels = ["Time", metric2];
      }
    } else {
      metric1 = "F";
      metric2 = "R";
      labels = ["Time", metric1, metric2];
    }
    if (typeof endDate !== "undefined" && endDate != null) {
      var dat = new Date(endDate);
      var lastTime = dat.getTime();
      var usedDate =
        graphData && graphData.length
          ? new Date(graphData[graphData.length - 1][0])
          : new Date();
      if (typeof timezone !== "undefined" && timezone == "UTC") {
        lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      var arr = [new Date(lastTime)];
      if(data && data.length){
        for(let i=0; i < data.length; i++){
          arr.push(null);
        }
      }else{
        arr.push(null); arr.push(null);
      }
      graphData.push(arr);
    }
    return { data: graphData, labels: labels };
  }

  
  getColorCode(j, arr) {
    var chars = "0123456789ABCDE".split("");
    var hex = "#";
    for (var i = 0; i < 6; i++) {
      hex += chars[Math.floor(Math.random() * 14)];
    }
    var colorCode = hex;
    if (arr.indexOf(colorCode) < 0) {
      return colorCode;
    } else {
      this.getColorCode(j, arr);
    }
  }
  getCookieBasedData(data,type) {
    var constructedData = {};
    for(var i=0; i < data.length; i++){
       var cookieId = data[i].tags && data[i].tags['cookie'] ? data[i].tags['cookie']: null;
       if(cookieId){
         var keyArray = Object.keys(constructedData);
         if(keyArray.indexOf(cookieId) > -1){
           constructedData[cookieId].push(data[i]);
         }else{
           if(type == 'forward' && cookieId.charAt(0) == '4'){
             constructedData[cookieId]=[];
             constructedData[cookieId].push(data[i]);
           }else if(type == 'reverse' && cookieId.charAt(0) == '2' ){
             constructedData[cookieId]=[];
             constructedData[cookieId].push(data[i]);
           }
         }
       }
     }
     
     return constructedData;
  }

  getCookieDataforFlowStats(data,type) {
    var constructedData = [];
    for(var i=0; i < data.length; i++){
       var cookieId = data[i].tags && data[i].tags['cookie'] ? data[i].tags['cookie']: null;
       if(cookieId){
           if(type == 'forward' && cookieId.charAt(0) == '4'){
             constructedData.push(data[i]);
           }else if(type == 'reverse' && cookieId.charAt(0) == '2' ){
             constructedData.push(data[i]);
           }
        }
     }
     return constructedData;
  }
  computeFlowPathGraphData(data, startDate, endDate, type, timezone,loadfromcookie) {
    let newMainArray = [];
    var labels =["Date"];
    var color = [];
    let cookiesChecked = {};
    if (typeof startDate !== "undefined" && startDate != null) {
      var dat = new Date(startDate);
      var startTime = dat.getTime();
      var usedDate = new Date();
      if (typeof timezone !== "undefined" && timezone == "UTC") {
        startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      var arr = [new Date(startTime)];
      if(data && data.length){
        for (var j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
       newMainArray.push(arr);
    }
    let lastHighestMetricLength = 0;
    if (data) {
      if (data.length > 0) {
        let mainArray = [];
        for (let j = 0; j < data.length; j++) {
          var dataValues = typeof data[j] !== "undefined" ? data[j].dps : null;
          var metric = typeof data[j] !== "undefined" ? data[j].metric : "";
          if (metric !== "pen.flow.packets") {
            metric = metric + "(switchid=" + data[j].tags.switchid + ", cookie="+data[j].tags['cookie']+")";
            labels.push(metric);
            var colorCode = this.getColorCode(j, color);
            if(cookiesChecked && typeof(cookiesChecked[data[j].tags['cookie']])!='undefined' && typeof(cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid])!='undefined'){
              colorCode = cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid]; 
              color.push(colorCode);
            }else{
              if(cookiesChecked && typeof(cookiesChecked[data[j].tags['cookie']])!='undefined'){
                cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid]=colorCode;
                color.push(colorCode);
              }else{
                cookiesChecked[data[j].tags['cookie']] = [];
              cookiesChecked[data[j].tags['cookie']][data[j].tags.switchid]=colorCode;
              color.push(colorCode);
              }
              
            }
            if(dataValues){
              for(let timestamp in dataValues){
                if(mainArray[timestamp]){
                  mainArray[timestamp][j]=dataValues[timestamp];
                }else{
                  mainArray[timestamp] = [];
                  mainArray[timestamp][j]=dataValues[timestamp];
                }
                if(lastHighestMetricLength < mainArray[timestamp].length){
                  lastHighestMetricLength =  mainArray[timestamp].length;
                }
              }
            }
          }
        }

        let index=0;
        for(let timestamp in mainArray){
          let tempArray = mainArray[timestamp];
          tempArray.unshift(new Date(Number(parseInt(timestamp) * 1000)));
          if(tempArray.length < (lastHighestMetricLength+1)){
            for(let j= tempArray.length; j < lastHighestMetricLength+1; j++){
              tempArray.push(null);
            }
          }
          newMainArray.push(tempArray);
        }
      }
    }
    if (typeof endDate !== "undefined" && endDate != null) {
      var dat = new Date(endDate);
      var lastTime = dat.getTime();
      var usedDate =
      newMainArray && newMainArray.length
          ? new Date(newMainArray[newMainArray.length - 1][0])
          : new Date();
      if (typeof timezone !== "undefined" && timezone == "UTC") {
        lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }
      var arr = [new Date(lastTime)];
      if(data && data.length){
        for (var j = 0; j < data.length; j++) {
          arr.push(null);
        }
      }
     
      newMainArray.push(arr);
    }
    return { labels: labels, data: newMainArray, color: color };
  }

  legendFormatter(data) {
    if (data.x == null) {
      return '<br>' + data.series.map(function(series) { return series.dashHTML + ' ' + series.labelHTML }).join('<br>');
    }
  
    var html = data.xHTML;
    data.series.forEach(function(series) {
      if (!series.isVisible) return;
      var labeledData ="<span style='color:"+series.color+"'>"+series.labelHTML + ': ' + series.yHTML+"</span>";
      if (series.isHighlighted) {
        labeledData = '<b>' + labeledData + '</b>';
      }
       html += '<br>' + labeledData;
    });
    return html;
  }

}
