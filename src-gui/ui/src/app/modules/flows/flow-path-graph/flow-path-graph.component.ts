import { Component, OnInit, Input, AfterViewInit, OnDestroy } from '@angular/core';
import { DygraphService } from '../../../common/services/dygraph.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { SwitchidmaskPipe } from '../../../common/pipes/switchidmask.pipe';
import { ClipboardService } from 'ngx-clipboard';
import { MessageObj } from 'src/app/common/constants/constants';
import {StatsService} from '../../../common/services/stats.service';
import {VictoriaData, VictoriaStatsReq, VictoriaStatsRes} from '../../../common/data-models/flowMetricVictoria';
declare var moment: any;

@Component({
  selector: 'app-flow-path-graph',
  templateUrl: './flow-path-graph.component.html',
  styleUrls: ['./flow-path-graph.component.css']
})
export class FlowPathGraphComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() data?: any;
  @Input('path-type') type?: string;
  cookieData: any;
  selectedCookie: any;

  filterForm: FormGroup;
  flowMetrics = [];
  timezoneData = [{label: 'UTC', value: 'UTC'}, {label: 'My Timezone', value: 'My'}];
  highestYaXis = null;
  labels: any;
  graph_data: any = [];
  options: any = Object.assign({}, {
    width: 'auto',
    chartHeight: '380',
    legend: 'onmouseover',
    noDataLabel: 'Please wait',

  });
  constructor(
    private statsService: StatsService,
    private dygraphService: DygraphService,
    private formBuilder: FormBuilder,
    private toaster: ToastrService,
    private switchMask: SwitchidmaskPipe,
    private clipBoardService: ClipboardService
  ) {}

  ngOnInit() {

    this.flowMetrics = this.dygraphService.getFlowMetricData();
    const dateRange = this.getDateRange();

    this.filterForm = this.formBuilder.group({
      timezone: ['My'],
      startDate: [dateRange.from],
      endDate: [dateRange.to],
      metric: ['bits']
    });

    this.changeFilter();
  }


  getDateRange(): any {

    const fromStartDate = moment()
      .subtract(4, 'hour')
      .format('YYYY/MM/DD HH:mm:ss');
    const toEndDate = moment().format('YYYY/MM/DD HH:mm:ss');

    const utcStartDate = moment().subtract(4, 'hour').utc().format('YYYY/MM/DD HH:mm:ss');
    const utcToEndDate = moment().utc().format('YYYY/MM/DD HH:mm:ss');

    return { from : fromStartDate, to : toEndDate , utcStartDate : utcStartDate,  utcToEndDate : utcToEndDate };
  }

  ngAfterViewInit() {}

  changeDate(input, event) {
    this.filterForm.controls[input].setValue(event.target.value);
    setTimeout(() => {
      this.changeFilter();
    }, 0);
  }

  changeTimeZone() {
    const formdata = this.filterForm.value;
    const timezone = formdata.timezone;
    const dateaRange = this.getDateRange();

    if (timezone == 'UTC') {
      this.filterForm.controls['startDate'].setValue(dateaRange.utcStartDate);
      this.filterForm.controls['endDate'].setValue(dateaRange.utcToEndDate);
    } else {
      this.filterForm.controls['startDate'].setValue(dateaRange.from);
      this.filterForm.controls['endDate'].setValue(dateaRange.to);
    }

    this.changeFilter();
  }

  changeFilter() {
    this.getFlowPathStatsData();
  }

  getFlowPathStatsData() {
    const formData = this.filterForm.value;

    const fromDate = new Date(formData.startDate);
    const toDate = new Date(formData.endDate);
    if (moment(fromDate).isAfter(toDate)) {
      this.toaster.error('Start date can not be after End date', 'error');
      return;
    }


    let fromStartDate, toEndDate, startDate, endDate;
    if (typeof fromDate !== 'undefined' && typeof toDate !== 'undefined') {
      if (formData.timezone == 'UTC') {
        startDate = moment(fromDate).format('YYYY-MM-DD-HH:mm:ss');
        endDate = moment(toDate).format('YYYY-MM-DD-HH:mm:ss');
        fromStartDate = moment(fromDate).format('YYYY/MM/DD HH:mm:ss');
        toEndDate = moment(toDate).format('YYYY/MM/DD HH:mm:ss');
      } else {
        startDate = moment(fromDate)
          .utc()
          .format('YYYY-MM-DD-HH:mm:ss');
        endDate = moment(toDate)
          .utc()
          .format('YYYY-MM-DD-HH:mm:ss');
        fromStartDate = moment(fromDate).format('YYYY/MM/DD HH:mm:ss');
        toEndDate = moment(toDate).format('YYYY/MM/DD HH:mm:ss');
      }
    } else {
      if (formData.timezone == 'UTC') {
        startDate = moment()
          .subtract(4, 'hour')
          .format('YYYY-MM-DD-HH:mm:ss');
        endDate = moment().format('YYYY-MM-DD-HH:mm:ss');
        fromStartDate = moment()
          .subtract(4, 'hour')
          .format('YYYY/MM/DD HH:mm:ss');
        toEndDate = moment().format('YYYY/MM/DD HH:mm:ss');
      } else {
        startDate = moment()
          .subtract(4, 'hour')
          .utc()
          .format('YYYY-MM-DD-HH:mm:ss');
        endDate = moment()
          .utc()
          .format('YYYY-MM-DD-HH:mm:ss');
        fromStartDate = moment()
          .subtract(4, 'hour')
          .format('YYYY/MM/DD HH:mm:ss');
        toEndDate = moment().format('YYYY/MM/DD HH:mm:ss');
      }
    }

    const switches = [];

    if (this.type == 'forward') {
      this.data.flowpath_forward.forEach(element => {
        switches.push(this.switchMask.transform(element.switch_id, 'legacy'));
      });
    } else {
      this.data.flowpath_reverse.forEach(element => {
        switches.push(this.switchMask.transform(element.switch_id, 'legacy'));
      });
    }
    const metric = formData.metric;
    const requestPayload: VictoriaStatsReq = {
      metrics: [metric],
      statsType: 'flowRawPacket',
      startDate: startDate,
      endDate: endDate,
      step: '30s',
      labels: {
        flowid: this.data.flowid,
        direction: this.type,
        cookie: '*',
        switchid: '*'
      }
    };

    this.statsService.getFlowPathStats(requestPayload).subscribe(
      response => handleResponse(response.dataList),
      error => handleResponse([]));

    const handleResponse = (response: VictoriaData[]) => {
      const dataforgraph = this.dygraphService.getCookieDataforFlowStats(response, this.type);
      const cookieBasedData = this.dygraphService.getCookieBasedData(response, this.type);
      this.cookieData = Object.keys(cookieBasedData);
      const data = (dataforgraph && dataforgraph.length) ? dataforgraph : [] ;
      this.plotFlowPathGraph(data, fromDate, toDate, this.type, formData.timezone);
    };
  }


  plotFlowPathGraph(data: VictoriaData[], startDate, endDate, type, timezone) {
    const graph_data = this.dygraphService.computeFlowPathGraphData(
      data,
      startDate,
      endDate,
      type,
      timezone
    );
    const graphData =  graph_data['data'];
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
    this.graph_data = graphData;
    if (timezone == 'UTC') {
      if (type == 'forward') {
        this.options = Object.assign(this.options, {
          labels: labels,
          labelsUTC: true,
          series: series,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoom
        });
      } else if (type == 'reverse') {
        this.options = Object.assign(this.options, {
          labels: labels,
          series: series,
          labelsUTC: true,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoom
        });
      }
    } else {
      if (type == 'forward') {
        this.options = Object.assign(this.options, {
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoom
        });
      } else if (type == 'reverse') {
        this.options = Object.assign(this.options, {
          labels: labels,
          series: series,
          labelsUTC: false,
          legend: 'onmouseover',
          connectSeparatedPoints: true,
          legendFormatter: this.dygraphService.legendFormatter,
          zoomCallback: this.zoom
        });
      }
    }
  }

  ngOnDestroy() {

  }

  zoom = (minX, maxX, yRanges) => {
    const formdata = this.filterForm.value;

    if (formdata.timezone == 'UTC') {
      const startDate = moment(new Date(minX)).utc().format('YYYY/MM/DD HH:mm:ss');
      const endDate = moment( new Date(maxX)).utc().format('YYYY/MM/DD HH:mm:ss');

      this.filterForm.controls['startDate'].setValue(startDate);
      this.filterForm.controls['endDate'].setValue(endDate);
    } else {
      const startDate = moment(new Date(minX)).format('YYYY/MM/DD HH:mm:ss');
      const endDate = moment( new Date(maxX)).format('YYYY/MM/DD HH:mm:ss');

      this.filterForm.controls['startDate'].setValue(startDate);
      this.filterForm.controls['endDate'].setValue(endDate);
    }

  }

  copyToClipCookie(data) {
    this.clipBoardService.copyFromContent(data);
    this.toaster.success(MessageObj.copied_to_clipboard);
  }

  changeMetric() {
    this.changeFilter();
  }
}
