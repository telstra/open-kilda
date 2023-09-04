import { Component, Output, OnInit, EventEmitter, Input, ChangeDetectorRef, OnDestroy } from '@angular/core';
import { IslDataService } from '../services/isl-data.service';
import { DygraphService } from '../services/dygraph.service';
import * as _moment from 'moment';
declare var moment: any;
declare var Dygraph: any;

@Component({
  selector: 'app-dygraph',
  templateUrl: './dygraph.component.html',
  styleUrls: ['./dygraph.component.css']
})
export class DygraphComponent implements OnInit, OnDestroy {


   @Output() zoomChange = new EventEmitter();
  // @Input() dataObj: any;
  // @Input() graphAPIFlag: boolean = false;
  dataObj: any;
  graphAPIFlag = false;
  message: {};
  data = [];
  highestYaXis = null;
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
  frequency: any;
  numOperator: number;
  count = 0;
  objectCount = 0;
  optionsObject = {};
  graphDataOptions: any;
  dateMessage: string;

  constructor(
    private islDataService: IslDataService,
    private dygraphService: DygraphService,
    private cdr: ChangeDetectorRef
  ) {

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
        startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
      }

      const arr = [new Date(startTime), null, null];
      graphData.push(arr);
    }

    if (!jsonResponse) {
      const getValue = typeof data[0] !== 'undefined' ? data[0].dps : {};
      let fDps = [];
      let rDps = [];
      metric1 = typeof data[0] !== 'undefined' ? data[0].metric : '';
      if (data.length == 2) {
        const getVal = typeof data[1] !== 'undefined' ? data[1].dps : {};
        rDps = Object.keys(getVal);
        metric2 = data[1].metric;

        if (data[1].tags.direction) {
          metric2 = data[1].metric + '(' + data[1].tags.direction + ')';
        }
        if (data[0].tags.direction) {
          metric1 = data[0].metric + '(' + data[0].tags.direction + ')';
        }
      }

      fDps = Object.keys(getValue);
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
          if (getValue[i] == null || typeof getValue[i] == 'undefined') {
            getValue[i] = null;
          } else if (getValue[i] < 0) {
            getValue[i] = 0;
          }

          const temparr = [];
          temparr[0] = new Date(Number(this.numOperator * 1000));
          temparr[1] = getValue[i];
          if (data.length == 2) {
            if (getVal[i] == null || typeof getVal[i] == 'undefined') {
              getVal[i] = null;
            } else if (getVal[i] < 0) {
              getVal[i] = 0;
            }
            temparr[2] = getVal[i];
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
      const arr = [new Date(lastTime), null, null];
      graphData.push(arr);
      // graphData.shift();
    }

    return { data: graphData, labels: labels };
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


  plotMeterGraph(data, startDate, endDate, timezone) {
    const graph_data = this.dygraphService.computeMeterGraphData(
      data,
      startDate,
      endDate,
      timezone,
    );
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
  plotFlowGraph(data, startDate, endDate, timezone) {
    const graphData = this.dygraphService.constructGraphData(
      data,
      undefined,
      startDate,
      endDate,
      timezone
    );
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
  /** End : Flow Graph Path */


  ngOnDestroy() {
    this.cdr.detach();
  }
}
