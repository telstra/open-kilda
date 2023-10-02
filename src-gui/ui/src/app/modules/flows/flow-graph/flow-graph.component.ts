import {AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges} from '@angular/core';
import {DygraphService} from '../../../common/services/dygraph.service';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {FlowsService} from '../../../common/services/flows.service';
import {ToastrService} from 'ngx-toastr';
import {CommonService} from 'src/app/common/services/common.service';

declare var moment: any;

@Component({
    selector: 'app-flow-graph',
    templateUrl: './flow-graph.component.html',
    styleUrls: ['./flow-graph.component.css']
})
export class FlowGraphComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    @Input()
    flowId;

    autoReloadTimerId = null;
    flowMetrics = [];
    packetMetrics = [];
    metersDirection = [];
    getautoReloadValues = this.commonService.getAutoreloadValues();
    filterForm: FormGroup;

    constructor(
        private dygraphService: DygraphService,
        private formBuiler: FormBuilder,
        private flowService: FlowsService,
        private toaster: ToastrService,
        private commonService: CommonService
    ) {

    }

    ngOnChanges(change: SimpleChanges) {
        if (change.flowId && change.flowId.currentValue) {
            this.flowId = change.flowId.currentValue;
            this.ngOnInit();
            this.loadGraphData();
        }
    }

    ngOnInit() {
        const dateRange = this.getDateRange();
        this.filterForm = this.formBuiler.group({
            timezone: ['LOCAL'],
            fromDate: [dateRange.from],
            toDate: [dateRange.to],
            download_sample: ['30s'],
            graph: ['flow'],
            metric: ['packets'],
            direction: ['forward'],
            meterdirection: ['both'],
            auto_reload: [''],
            auto_reload_time: ['', Validators.compose([Validators.pattern('[0-9]*')])],
            victoriaSource: [false]
        });

        this.flowMetrics = this.dygraphService.getFlowMetricData();
        this.packetMetrics = this.dygraphService.getPacketsMetricData();
        this.metersDirection = this.dygraphService.getMetricDirections();
    }

    getDateRange(): any {
        const date = new Date();
        const yesterday = new Date(date.getTime());
        yesterday.setDate(date.getDate() - 1);
        const fromStartDate = moment(yesterday).format('YYYY/MM/DD HH:mm:ss');
        const toEndDate = moment(date).format('YYYY/MM/DD HH:mm:ss');

        const utcStartDate = moment(yesterday).utc().format('YYYY/MM/DD HH:mm:ss');
        const utcToEndDate = moment(date).utc().format('YYYY/MM/DD HH:mm:ss');

        return {from: fromStartDate, to: toEndDate, utcStartDate: utcStartDate, utcToEndDate: utcToEndDate};
    }

    changeDate(input, event) {
        this.filterForm.controls[input].setValue(event.target.value);
        setTimeout(() => {
            this.loadGraphData();
        }, 0);
    }

    ngAfterViewInit() {
        this.filterForm.get('auto_reload').valueChanges.subscribe(value => {
            if (value) {
                this.filterForm
                    .get('auto_reload_time')
                    .setValidators([Validators.required, Validators.pattern('^[0-9]*')]);
            } else {
                this.filterForm
                    .get('auto_reload_time')
                    .setValidators([Validators.pattern('^[0-9]*')]);
                if (this.autoReloadTimerId) {
                    clearInterval(this.autoReloadTimerId);
                }
            }
            this.filterForm.get('auto_reload_time').setValue('');
            this.filterForm.get('auto_reload_time').updateValueAndValidity();
        });

        this.filterForm.get('auto_reload_time').valueChanges.subscribe(value => {
        });
    }

    startAutoReload() {
        const autoReloadTime = Number(
            this.filterForm.controls['auto_reload_time'].value
        );
        if (this.filterForm.controls['auto_reload']) {
            if (this.autoReloadTimerId) {
                clearInterval(this.autoReloadTimerId);
            }
            if (autoReloadTime) {
                this.autoReloadTimerId = setInterval(() => {
                    this.loadGraphData();
                }, 1000 * autoReloadTime);
            }
        } else {
            if (this.autoReloadTimerId) {
                clearInterval(this.autoReloadTimerId);
            }
        }
    }

    changeTimeZone() {
        const formdata = this.filterForm.value;
        const timezone = formdata.timezone;
        const dateaRange = this.getDateRange();

        if (timezone == 'UTC') {
            this.filterForm.controls['fromDate'].setValue(dateaRange.utcStartDate);
            this.filterForm.controls['toDate'].setValue(dateaRange.utcToEndDate);
        } else {
            this.filterForm.controls['fromDate'].setValue(dateaRange.from);
            this.filterForm.controls['toDate'].setValue(dateaRange.to);
        }

        this.loadGraphData();
    }

    loadGraphData() {
        const formdata = this.filterForm.value;
        console.log('loadGraphData() called, isVictoriaSource:' + formdata.victoriaSource);
        const flowid = this.flowId;
        const autoReloadTime = Number(
            this.filterForm.controls['auto_reload_time'].value
        );

        const direction = (formdata.graph == 'flowmeter') ? formdata.meterdirection : formdata.direction;
        const downsampling = formdata.download_sample;
        const metric = formdata.metric;
        const timezone = formdata.timezone;
        if (this.filterForm.controls['auto_reload']) {
            formdata.toDate = new Date(new Date(formdata.toDate).getTime() + (autoReloadTime * 1000));
        }
        let convertedStartDate = moment(new Date(formdata.fromDate)).utc().format('YYYY-MM-DD-HH:mm:ss');
        let convertedEndDate = moment(new Date(formdata.toDate)).utc().format('YYYY-MM-DD-HH:mm:ss');


        const startDate = moment(new Date(formdata.fromDate));
        const endDate = moment(new Date(formdata.toDate));

        if (
            moment(new Date(formdata.fromDate)).isAfter(new Date(formdata.toDate))
        ) {
            this.toaster.error('Start date can not be after End date', 'Error');
            return;
        }
        if (formdata.timezone == 'UTC') {
            convertedStartDate = moment(new Date(formdata.fromDate)).format('YYYY-MM-DD-HH:mm:ss');
            convertedEndDate = moment(new Date(formdata.toDate)).format('YYYY-MM-DD-HH:mm:ss');

        }

        const handleSuccessForFlow = (res: any) => {
            this.dygraphService.changeFlowGraphData({
                data: res,
                startDate: startDate,
                endDate: endDate,
                timezone: timezone
            });
        };
        const handleErrorForFlow = (res: any) => {
            this.dygraphService.changeFlowGraphData({
                data: [],
                startDate: startDate,
                endDate: endDate,
                timezone: timezone
            });
            const statsDbName = formdata.victoriaSource ? 'Victoria DB' : 'OpenTSDB';
            const errorMsg = res && res.error && res.error.message ? res.error.message
                : `Something went wrong while accessing ${statsDbName}`;
            this.toaster.error(errorMsg, 'Error');
        };

        const handleSuccessMeter = (res: any) => {
            this.dygraphService.changeMeterGraphData({
                data: res,
                startDate: startDate,
                endDate: endDate,
                timezone: timezone
            });
        };
        const handleErrorMeter = (error: any) => {
            this.dygraphService.changeMeterGraphData({
                data: [],
                startDate: startDate,
                endDate: endDate,
                timezone: timezone
            });
            const statsDbName = formdata.victoriaSource ? 'Victoria DB' : 'OpenTSDB';
            const errorMsg = error && error.message ? error.message : `Something went wrong while accessing ${statsDbName}`;
            this.toaster.error(errorMsg, 'Error');
        };

        if (formdata.graph == 'flow') {
            if (formdata.victoriaSource) {
                this.flowService.getFlowGraphVictoriaData(
                    'flow',
                    flowid,
                    convertedStartDate,
                    convertedEndDate,
                    downsampling,
                    [metric])
                    .subscribe(handleSuccessForFlow, handleErrorForFlow);
            } else {
                this.flowService.getFlowGraphData(flowid, convertedStartDate, convertedEndDate, downsampling, metric)
                    .subscribe(handleSuccessForFlow, handleErrorForFlow);
            }
        } else if (formdata.graph == 'flowmeter') {
            if (formdata.victoriaSource) {
                this.flowService.getFlowGraphVictoriaData(
                    'meter',
                    flowid,
                    convertedStartDate,
                    convertedEndDate,
                    downsampling,
                    [metric])
                    .subscribe(res => {
                        console.log(res);
                    });
            } else {
                this.flowService
                    .getMeterGraphData(
                        flowid,
                        convertedStartDate,
                        convertedEndDate,
                        downsampling,
                        metric,
                        direction
                    )
                    .subscribe(handleSuccessMeter, handleErrorMeter);
            }
        } else { // packet loss
            if (formdata.victoriaSource) {
                this.flowService.getFlowGraphVictoriaData(
                    'flow',
                    flowid,
                    convertedStartDate,
                    convertedEndDate,
                    downsampling,
                    [metric, 'ingress_packets'],
                    direction)
                    .subscribe(handleSuccessForFlow, handleErrorForFlow);
            } else {
                this.flowService
                    .getFlowPacketGraphData(
                        flowid,
                        convertedStartDate,
                        convertedEndDate,
                        downsampling,
                        direction
                    )
                    .subscribe(handleSuccessForFlow, handleErrorForFlow);
            }
        }
    }

    get f() {
        return this.filterForm.controls;
    }


    zoom = (event) => {

        const formdata = this.filterForm.value;

        if (formdata.timezone == 'UTC') {
            const startDate = moment(new Date(event.minX)).utc().format('YYYY/MM/DD HH:mm:ss');
            const endDate = moment(new Date(event.maxX)).utc().format('YYYY/MM/DD HH:mm:ss');

            this.filterForm.controls['fromDate'].setValue(startDate);
            this.filterForm.controls['toDate'].setValue(endDate);
        } else {
            const startDate = moment(new Date(event.minX)).format('YYYY/MM/DD HH:mm:ss');
            const endDate = moment(new Date(event.maxX)).format('YYYY/MM/DD HH:mm:ss');

            this.filterForm.controls['fromDate'].setValue(startDate);
            this.filterForm.controls['toDate'].setValue(endDate);
        }

    };

    ngOnDestroy() {
        if (this.autoReloadTimerId) {
            clearInterval(this.autoReloadTimerId);
        }
    }


}
