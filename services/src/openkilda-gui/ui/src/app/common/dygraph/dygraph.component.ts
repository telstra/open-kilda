import { Component, Output, OnInit, EventEmitter, Input, ChangeDetectorRef, OnDestroy } from "@angular/core";
import { IslDataService } from "../services/isl-data.service";
import { DygraphService } from "../services/dygraph.service";
import * as _moment from 'moment';
declare var moment: any;
declare var Dygraph :any;

@Component({
  selector: "app-dygraph",
  templateUrl: "./dygraph.component.html",
  styleUrls: ["./dygraph.component.css"]
})
export class DygraphComponent implements OnInit, OnDestroy {


   @Output() zoomChange = new EventEmitter();
  // @Input() dataObj: any;
  // @Input() graphAPIFlag: boolean = false;
  dataObj: any;
  graphAPIFlag: boolean = false;
  message: {};
  data = [];
  highestYaXis = null;
  labels: any;
  options : any = Object.assign({}, {
    width: "auto",
    chartHeight:"380",
    legend: "onmouseover",
    noDataLabel:"Please wait",
    
  });

  jsonResponse: any;
  startDate: any;
  endDate: any;
  timezone: any;
  frequency: any;
  numOperator: number;
  count: number = 0;
  objectCount: number = 0;
  optionsObject = {};
  graphDataOptions: any;
  dateMessage:string;

  

  

  constructor(
    private islDataService: IslDataService,
    private dygraphService: DygraphService,
    private cdr: ChangeDetectorRef
  ) {
    /**capturing API changed data push*/
    dygraphService.flowPathGraph.subscribe(data => {
      this.plotFlowPathGraph(
        data.data,
        data.startDate,
        data.endDate,
        data.type,
        data.timezone,
        data.loadfromCookie
      );
      try {
        cdr.detectChanges();
      } catch (err) {}
    });
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
        startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
      } 
      
      var arr = [new Date(startTime), null, null];
      graphData.push(arr);
    }

    if (!jsonResponse) {
      var getValue = typeof data[0] !== "undefined" ? data[0].dps : {};
      var fDps = [];
      var rDps = [];
      metric1 = typeof data[0] !== "undefined" ? data[0].metric : "";
      if (data.length == 2) {
        var getVal = typeof data[1] !== "undefined" ? data[1].dps : {};
        rDps = Object.keys(getVal);
        metric2 = data[1].metric;

        if (data[1].tags.direction) {
          metric2 = data[1].metric + "(" + data[1].tags.direction + ")";
        }
        if (data[0].tags.direction) {
          metric1 = data[0].metric + "(" + data[0].tags.direction + ")";
        }
      }

      fDps = Object.keys(getValue);
      var graphDps = fDps.concat(rDps);
      graphDps.sort();
      graphDps = graphDps.filter((v, i, a) => a.indexOf(v) === i);
      
      if (graphDps.length <= 0 ) {
        metric1 = "F";
        metric2 = "R";
      } else {

        for(var index=0;index < graphDps.length; index++){
          let i = graphDps[index];
          this.numOperator = parseInt(i);
          if (getValue[i] == null || typeof getValue[i] == 'undefined') {
            getValue[i] = null;
          }else if(getValue[i] < 0){
            getValue[i]=0;
          }
        
          var temparr = [];
          temparr[0] = new Date(Number(this.numOperator * 1000));
          temparr[1] = getValue[i];
          if (data.length == 2) {
            if (getVal[i] == null || typeof getVal[i] == 'undefined') {
              getVal[i]= null;
            }else if(getVal[i] < 0){
              getVal[i]=0;
            }
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
      var arr = [new Date(lastTime), null, null];
      graphData.push(arr);
      //graphData.shift();
    }
    
  
    return { data: graphData, labels: labels };
  }

  ngOnInit() {
    this.islDataService.currentMessage.subscribe(message => {
      this.message = message;
      if (this.count >= 1) {
        this.drawGraphCall(message);
      }
      this.count++;
    });
    this.islDataService.currentOptionsObject.subscribe(message => {
      this.optionsObject = message;
      this.graphDataOptions = message;
      if (this.objectCount >= 1) {
        this.drawGraphCall(message);
      }
      this.objectCount++;
    });

    this.dygraphService.flowGraph.subscribe(data => {
      this.plotFlowGraph(
        data.data,
        data.startDate,
        data.endDate,
        data.timezone
      );
      try {
        this.cdr.detectChanges();
      } catch (err) {}
    });

    this.dygraphService.meterGraph.subscribe(data => {
      this.plotMeterGraph(
        data.data,
        data.startDate,
        data.endDate,
        data.timezone);
      try {
        this.cdr.detectChanges();
      } catch (err) {}
    })
  }



  drawGraphCall(dataObj) {
   this.timezone = dataObj.timezone;
   this.jsonResponse = undefined;

   let processedResponse = this.dygraphService.constructGraphData(
    dataObj.data,
    this.jsonResponse,
    dataObj.startDate,
    dataObj.endDate,
    dataObj.timezone
  );
   this.data = processedResponse.data;
   this.labels = processedResponse.labels;

  if (this.timezone == "UTC") {
    this.options = Object.assign(this.options, {
    labels: this.labels,
    drawPoints: false,
    animatedZooms: true,
    labelsUTC: true,
    colors: ["#1C227C","#A1CD24"] ,
    legend: "onmouseover",
    valueRange:[0,null],
    connectSeparatedPoints:true,
    legendFormatter:this.dygraphService.legendFormatter,
      zoomCallback: this.zoomCallbackHandler
    });
  }else{
    
    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: false,
      colors: ["#1C227C","#A1CD24"],
      legend: "onmouseover",
      valueRange:[0,null],
      connectSeparatedPoints:true,
      legendFormatter:this.dygraphService.legendFormatter,
      zoomCallback: this.zoomCallbackHandler
    });
  }

    
   
  }



  zoomCallbackHandler =  (minX, maxX, yRanges) =>{
    this.zoomChange.emit({ minX:minX, maxX:maxX, yRanges:yRanges});
  }

  /** Start : Flow Graphs */

   
  

  plotFlowPathGraph(data, startDate, endDate, type, timezone,loadfromCookie) {
    var graph_data = this.dygraphService.computeFlowPathGraphData(
      data,
      startDate,
      endDate,
      type,
      timezone,
      loadfromCookie
    );
    var graphData = graph_data["data"];
    var labels = graph_data["labels"];
    var series = {};
    var colors = graph_data["color"];
    if (labels && labels.length) {
      for (var k = 0; k < labels.length; k++) {
        if (k != 0) {
          series[labels[k]] = { color: colors[k - 1] };
        }
      }
    }
 

    this.data = graphData;

    if (timezone == "UTC") {
      if (type == "forward") {
        this.options = Object.assign(this.options,{
          labels: labels,
          labelsUTC: true,
          series: series,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
      } else if (type == "reverse") {
        this.options = Object.assign(this.options,{
          labels: labels,
          series: series,
          labelsUTC: true,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
      }
    } else {
      if (type == "forward") {
        this.options = Object.assign(this.options,{
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
      } else if (type == "reverse") {
        this.options = Object.assign(this.options, {
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
      }
    }
  }

  plotMeterGraph(data, startDate, endDate, timezone){
    var graph_data = this.dygraphService.computeMeterGraphData(
      data,
      startDate,
      endDate,
      timezone,
    );
    var graphData = graph_data["data"];
    var labels = graph_data["labels"];
    var series = {};
    var colors = graph_data["color"];
    if (labels && labels.length) {
      for (var k = 0; k < labels.length; k++) {
        if (k != 0) {
          series[labels[k]] = { color: colors[k - 1] };
        }
      }
    }
 

    this.data = graphData;

    if (timezone == "UTC") {
        this.options = Object.assign(this.options,{
          labels: labels,
          labelsUTC: true,
          series: series,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
      
    } else {
       this.options = Object.assign(this.options,{
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: "onmouseover",
          connectSeparatedPoints:true,
          legendFormatter:this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
    }
  }
  plotFlowGraph(data, startDate, endDate, timezone) {
    let graphData = this.dygraphService.constructGraphData(
      data,
      undefined,
      startDate,
      endDate,
      timezone
    );
    try{
    this.data = graphData["data"];
    this.labels = graphData["labels"];

    if (timezone == "UTC") {
      this.options = Object.assign(this.options,{
        labels: this.labels,
        animatedZooms: true,
        labelsUTC: true,
        colors: ["#1C227C", "#A1CD24"],
        connectSeparatedPoints:true,
        legendFormatter:this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });

    } else {
      this.options =  Object.assign(this.options,{
        labels: this.labels,
        animatedZooms: true,
        labelsUTC: false,
        colors: ["#1C227C", "#A1CD24"],
        connectSeparatedPoints:true,
        legendFormatter:this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });
    }
    }catch(err){}
  }
  /** End : Flow Graph Path */


  ngOnDestroy(){
    this.cdr.detach();
  }
}
