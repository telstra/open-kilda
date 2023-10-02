import { Component, Output, OnInit, EventEmitter, Input, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { IslDataService } from '../services/isl-data.service';
import { DygraphService } from '../services/dygraph.service';
import {FlowMetricTsdb} from '../data-models/flowMetricTsdb';
import {VictoriaStatsRes} from '../data-models/flowMetricVictoria';
@Component({
  selector: 'app-dygraph',
  templateUrl: './dygraph.component.html',
  styleUrls: ['./dygraph.component.css']
})
export class DygraphComponent implements OnInit, OnDestroy {


   @Output() zoomChange = new EventEmitter();
  message: {};
  data = [];
  labels: any;
  options: any = Object.assign({}, {
    width: 'auto',
    chartHeight: '380',
    legend: 'onmouseover',
    noDataLabel: 'Please wait',
  });

  jsonResponse: any;
  startDate: any;
  endDate: any;
  timezone: any;
  count = 0;
  objectCount = 0;
  optionsObject = {};
  graphDataOptions: any;
  constructor(
    private islDataService: IslDataService,
    private dygraphService: DygraphService,
    private cdr: ChangeDetectorRef
  ) {

  }
  ngOnInit() {

    this.islDataService.currentMessage.subscribe(message => {
      this.message = message;
      if (this.count >= 1) {
        this.options = Object.assign({}, {
          width: 'auto',
          chartHeight: '380',
          legend: 'onmouseover',
          noDataLabel: 'Please wait',
        });
        this.drawGraphCall(message);
      }
      this.count++;
    });
    this.islDataService.currentOptionsObject.subscribe(message => {
      this.optionsObject = message;
      this.graphDataOptions = message;
      if (this.objectCount >= 1) {
        this.options = Object.assign({}, {
          width: 'auto',
          chartHeight: '380',
          legend: 'onmouseover',
          noDataLabel: 'Please wait',
        });
        this.drawGraphCall(message);
      }
      this.objectCount++;
    });

    this.islDataService.IslFlowGraph.subscribe(message => {
      this.message = message;
      if (this.count >= 1) {
        this.options = Object.assign({}, {
          width: 'auto',
          chartHeight: '380',
          legend: 'onmouseover',
          noDataLabel: 'Please wait',
        });
        this.plotISLFlowGraph(message);
      }
      this.count++;
    });

    this.islDataService.IslFlowStackedGraph.subscribe(message => {
      this.message = message;
      if (this.count >= 1) {
        this.options = Object.assign({}, {
          width: 'auto',
          chartHeight: '380',
          legend: 'onmouseover',
          noDataLabel: 'Please wait',
        });
        this.plotISLStackedFlowGraph(message);
      }
      this.count++;
    });

    this.dygraphService.flowGraph.subscribe(data => {
      this.options = Object.assign({}, {
        width: 'auto',
        chartHeight: '380',
        legend: 'onmouseover',
        noDataLabel: 'Please wait',
      });
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
      this.options = Object.assign({}, {
        width: 'auto',
        chartHeight: '380',
        legend: 'onmouseover',
        noDataLabel: 'Please wait',
      });
      this.plotMeterGraph(
        data.data,
        data.startDate,
        data.endDate,
        data.timezone);
      try {
        this.cdr.detectChanges();
      } catch (err) {}
    });
  }



  drawGraphCall(dataObj) {
   this.timezone = dataObj.timezone;
   this.jsonResponse = undefined;

   const processedResponse = this.dygraphService.constructGraphData(
    dataObj.data,
    this.jsonResponse,
    dataObj.startDate,
    dataObj.endDate,
    dataObj.timezone
  );
   this.data = processedResponse.data;
   this.labels = processedResponse.labels;

  if (this.timezone == 'UTC') {
    this.options = Object.assign(this.options, {
    labels: this.labels,
    drawPoints: false,
    animatedZooms: true,
    labelsUTC: true,
    colors: ['#1C227C', '#A1CD24'] ,
    legend: 'onmouseover',
    valueRange: [0, null],
    connectSeparatedPoints: true,
    legendFormatter: this.dygraphService.legendFormatter,
      zoomCallback: this.zoomCallbackHandler
    });
  } else {

    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: false,
      colors: ['#1C227C', '#A1CD24'],
      legend: 'onmouseover',
      valueRange: [0, null],
      connectSeparatedPoints: true,
      legendFormatter: this.dygraphService.legendFormatter,
      zoomCallback: this.zoomCallbackHandler
    });
  }

  }

  plotISLFlowGraph(dataObj) {
    this.timezone = dataObj.timezone;
    this.jsonResponse = undefined;
    this.labels = dataObj.labels;
    this.data = dataObj.data;
  if (this.timezone == 'UTC') {
    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: true,
      series: dataObj.series,
      legend: 'onmouseover',
      valueRange: [0, null],
      connectSeparatedPoints: true,
      legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });
  } else {
    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: false,
      series: dataObj.series,
      legend: 'onmouseover',
      valueRange: [0, null],
      connectSeparatedPoints: true,
      legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });
     }
  }

  plotISLStackedFlowGraph(dataObj) {
    this.timezone = dataObj.timezone;
    this.jsonResponse = undefined;
    this.labels = dataObj.labels;
    this.data = dataObj.data;
  if (this.timezone == 'UTC') {
    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: true,
      series: dataObj.series,
      legend: 'onmouseover',
      stackedGraph: true,
      valueRange: [0, null],
      connectSeparatedPoints: true,
      legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler,
        axes: {
          x: {
            drawGrid: false
          }
        }
      });
  } else {
    this.options = Object.assign(this.options, {
      labels: this.labels,
      drawPoints: false,
      animatedZooms: true,
      labelsUTC: false,
      series: dataObj.series,
      legend: 'onmouseover',
      stackedGraph: true,
      valueRange: [0, null],
      connectSeparatedPoints: true,
      legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler,
        axes: {
          x: {
            drawGrid: false
          }
        }
      });
     }
  }

  zoomCallbackHandler =  (minX, maxX, yRanges) => {
    this.zoomChange.emit({ minX: minX, maxX: maxX, yRanges: yRanges});
  }

  /** Start : Flow Graphs */


  plotMeterGraph(data: VictoriaStatsRes | FlowMetricTsdb[], startDate, endDate, timezone) {
    let graph_data;
    if (this.isVictoriaStatsRes(data)) {
      graph_data = this.dygraphService.constructVictoriaMeterGraphData(
          data.dataList,
          startDate,
          endDate,
          timezone
      );
    } else {
      graph_data = this.dygraphService.constructMeterGraphData(
          data,
          startDate,
          endDate,
          timezone,
      );
    }

    const graphData = graph_data['data'];
    const labels = graph_data['labels'];
    const series = {};
    const colors = graph_data['color'];
    if (labels && labels.length) {
      for (let k = 0; k < labels.length; k++) {
        if (k != 0) {
          series[labels[k]] = { color: colors[k - 1] };
        }
      }
    }

    this.data = graphData;

    if (timezone == 'UTC') {
        this.options = Object.assign(this.options, {
          labels: labels,
          labelsUTC: true,
          series: series,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });

    } else {
       this.options = Object.assign(this.options, {
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoomCallbackHandler
        });
    }
  }
  plotFlowGraph(data: VictoriaStatsRes | FlowMetricTsdb[], startDate: string, endDate: string, timezone: string) {
    let graphData;
    if (this.isVictoriaStatsRes(data)) {
      graphData = this.dygraphService.constructVictoriaGraphData(
          data.dataList,
          startDate,
          endDate,
          timezone
      );
    } else {
      graphData = this.dygraphService.constructGraphData(
          data,
          undefined,
          startDate,
          endDate,
          timezone
      );
    }
    try {
    this.data = graphData['data'];
    this.labels = graphData['labels'];

    if (timezone == 'UTC') {
      this.options = Object.assign(this.options, {
        labels: this.labels,
        animatedZooms: true,
        labelsUTC: true,
        colors: ['#1C227C', '#A1CD24'],
        connectSeparatedPoints: true,
        legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });

    } else {
      this.options =  Object.assign(this.options, {
        labels: this.labels,
        animatedZooms: true,
        labelsUTC: false,
        colors: ['#1C227C', '#A1CD24'],
        connectSeparatedPoints: true,
        legendFormatter: this.dygraphService.legendFormatter,
        zoomCallback: this.zoomCallbackHandler
      });
    }
    } catch (err) {}
  }

  private isVictoriaStatsRes(data: any): data is VictoriaStatsRes {
    return data && data.dataList !== undefined && data.status !== undefined;
  }
  /** End : Flow Graph Path */


  ngOnDestroy() {
    this.cdr.detach();
  }
}
