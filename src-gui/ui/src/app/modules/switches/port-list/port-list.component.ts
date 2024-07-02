import {AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild} from '@angular/core';
import {SwitchService} from '../../../common/services/switch.service';
import {SwitchidmaskPipe} from '../../../common/pipes/switchidmask.pipe';
import {ToastrService} from 'ngx-toastr';
import {ActivatedRoute, Router} from '@angular/router';
import {DataTableDirective} from 'angular-datatables';
import {Subject, Subscription} from 'rxjs';
import {LoaderService} from '../../../common/services/loader.service';
import {Title} from '@angular/platform-browser';
import {CommonService} from 'src/app/common/services/common.service';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {CreateLagPortComponent} from '../create-lag-port/create-lag-port.component';
import {MessageObj} from 'src/app/common/constants/constants';
import {StatsService} from '../../../common/services/stats.service';
import {PortInfo} from '../../../common/data-models/port-info';

@Component({
    selector: 'app-port-list',
    templateUrl: './port-list.component.html',
    styleUrls: ['./port-list.component.css']
})
export class PortListComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    @ViewChild(DataTableDirective, {static: true})
    dtElement: DataTableDirective;
    @Input() switch = null;
    @Input() loadinterval = false;
    isLoaderActive = false;
    dtOptions: any = {};
    dtTrigger: Subject<void> = new Subject();

    currentActivatedRoute: string;
    switch_id: string;
    switchPortDataSet: PortInfo[];
    anyData: any;
    portListTimerId: any;
    portFlowData: any = {};
    portListSubscriber = null;
    portFlowSubscription: Subscription[] = [];
    loadPorts = false;
    switchFilterFlag: string = sessionStorage.getItem('switchFilterFlag') || 'controller';
    hasStoreSetting;

    constructor(private switchService: SwitchService,
                private statsService: StatsService,
                private toastr: ToastrService,
                private maskPipe: SwitchidmaskPipe,
                private router: Router,
                private loaderService: LoaderService,
                private titleService: Title,
                private modalService: NgbModal,
                private route: ActivatedRoute,
                private commonService: CommonService,
    ) {
        this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
    }

    ngOnInit() {
        const ref = this;
        this.switch_id = this.switch;
        this.dtOptions = {
            paging: false,
            retrieve: true,
            autoWidth: false,
            colResize: false,
            dom: 'tpli',
            initComplete: function (settings, json) {
                if (localStorage.getItem('portLoaderEnabled')) {
                    setTimeout(() => {
                        ref.loaderService.hide();
                    }, 2000);
                    localStorage.removeItem('portLoaderEnabled');
                }
            },
            'aLengthMenu': [[10, 20, 35, 50, -1], [10, 20, 35, 50, 'All']],
            'aoColumns': [
                {sWidth: '5%'},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '13%'},
                {sWidth: '13%'},
                {sWidth: '13%'},
                {sWidth: '13%'},
                {sWidth: '5%'},
                {sWidth: '5%'},
                {sWidth: '5%'},
                {sWidth: '8%'}],
            language: {
                searchPlaceholder: 'Search'
            },
            columnDefs: [
                {targets: [1], visible: this.hasStoreSetting},
            ]
        };
        this.initPortListData();
        this.initSwitchPortList();
    }

    fulltextSearch(e: any) {
        const value = e.target.value;
        this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
            if (this.hasStoreSetting) {
                dtInstance.columns([1]).visible(true);
            } else {
                dtInstance.columns([1]).visible(false);
            }

            dtInstance.search(value)
                .draw();
        });
    }

    showPortDetail(item) {
        const portDataObject = item;
        const portDataObjectKey = 'portDataObject_' + this.switch_id + '_' + item.port_number;
        localStorage.setItem(portDataObjectKey, JSON.stringify(portDataObject));
        this.currentActivatedRoute = 'port-details';
        this.router.navigate(['/switches/details/' + this.switch_id + '/port/' + item.port_number]);
    }

    initSwitchPortList() {
        this.portListTimerId = setInterval(() => {
            if (this.loadinterval) {
                this.initPortListData();
            }
        }, 30000);
    }

    initPortListData() {
        if (this.loadPorts) {
            return;
        }
        if (localStorage.getItem('portLoaderEnabled')) {
            this.loaderService.show('Loading Ports');
        }
        this.loadPorts = true;
        this.portListSubscriber = this.statsService.getSwitchPortsStats(this.maskPipe.transform(this.switch_id, 'legacy')).subscribe((data: PortInfo[]) => {
            this.rerender();
            this.ngAfterViewInit();
            this.loadPorts = false;
            localStorage.setItem('switchPortDetail', JSON.stringify(data));
            this.switchPortDataSet = data;
            for (let i = 0; i < this.switchPortDataSet.length; i++) {
                if (this.switchPortDataSet[i].port_number === '' || this.switchPortDataSet[i].port_number === undefined) {
                    this.switchPortDataSet[i].port_number = '-';
                }
                if (this.switchPortDataSet[i].interfacetype === '' || this.switchPortDataSet[i].interfacetype === undefined) {
                    this.switchPortDataSet[i].interfacetype = '-';
                }
                if (typeof (this.switchPortDataSet[i].stats) !== 'undefined') {
                    if (this.switchPortDataSet[i].stats['tx-bytes'] === '' || this.switchPortDataSet[i].stats['tx-bytes'] === undefined) {
                        this.switchPortDataSet[i].stats['tx-bytes'] = '-';
                    } else {
                        this.switchPortDataSet[i].stats['tx-bytes'] = this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['tx-bytes']);
                    }

                    if (this.switchPortDataSet[i].stats['rx-bytes'] === '' || this.switchPortDataSet[i].stats['rx-bytes'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-bytes'] = '-';
                    } else {
                        this.switchPortDataSet[i].stats['rx-bytes'] = this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['rx-bytes']);
                    }

                    if (this.switchPortDataSet[i].stats['tx-packets'] === '' || this.switchPortDataSet[i].stats['tx-packets'] === undefined) {
                        this.switchPortDataSet[i].stats['tx-packets'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-packets'] === '' || this.switchPortDataSet[i].stats['rx-packets'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-packets'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['tx-dropped'] === '' || this.switchPortDataSet[i].stats['tx-dropped'] === undefined) {
                        this.switchPortDataSet[i].stats['tx-dropped'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-dropped'] === '' || this.switchPortDataSet[i].stats['rx-dropped'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-dropped'] = '-';
                    }


                    if (this.switchPortDataSet[i].stats['tx-errors'] === '' || this.switchPortDataSet[i].stats['tx-errors'] === undefined) {
                        this.switchPortDataSet[i].stats['tx-errors'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-errors'] === '' || this.switchPortDataSet[i].stats['rx-errors'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-errors'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['collisions'] === '' || this.switchPortDataSet[i].stats['collisions'] === undefined) {
                        this.switchPortDataSet[i].stats['collisions'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-frame-error'] === '' || this.switchPortDataSet[i].stats['rx-frame-error'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-frame-error'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-over-error'] === '' || this.switchPortDataSet[i].stats['rx-over-error'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-over-error'] = '-';
                    }

                    if (this.switchPortDataSet[i].stats['rx-crc-error'] === '' || this.switchPortDataSet[i].stats['rx-crc-error'] === undefined) {
                        this.switchPortDataSet[i].stats['rx-crc-error'] = '-';
                    }
                } else {
                    this.switchPortDataSet[i]['stats'] = {};
                }
            }
            const ports: Array<number> = this.switchPortDataSet.filter(obj => !isNaN(parseInt(obj.port_number, 10)))
                .map(obj => parseInt(obj.port_number, 10));
            if (ports.length !== 0) {
                this.initPortFlowData(this.switch_id, ports);
            }
        }, error => {
            // this.toastr.error("No Switch Port data",'Error');
        });
    }

    initPortFlowData(switchId: string, ports: Array<number>) {
        ports.forEach(port => {
            this.portFlowData[port] = {};
            this.portFlowData[port].sumflowbandwidth = 0;
            this.portFlowData[port].noofflows = 0;
        });
        if (switchId) {
            const subscriptionPortFlows = this.switchService.getSwitchFlowsForPorts(switchId, ports)
                .subscribe((data: any) => {
                    if (data) {
                        const flowsByPortData = data.flows_by_port;
                        const flowsByPort = new Map(Object.entries(flowsByPortData));
                        flowsByPort.forEach((flows: Array<any>, port) => {
                            const portNumber: number = Number(port);
                            if (flows.length) {
                                this.portFlowData[portNumber].sumflowbandwidth =
                                    flows.reduce((accumulator, flow) => accumulator + flow.maximum_bandwidth / 1000, 0)
                                        .toFixed(3);
                                this.portFlowData[portNumber].noofflows = flows.length;
                            }
                        });
                    }

                }, error => {
                    ports.forEach(port => {
                        this.portFlowData[port] = {};
                        this.portFlowData[port].sumflowbandwidth = 0;
                        this.portFlowData[port].noofflows = 0;
                    });

                });
            this.portFlowSubscription.push(subscriptionPortFlows);
        }
    }

    ngAfterViewInit(): void {
        try {
            this.dtTrigger.next();
        } catch (err) {

        }
    }

    ngOnDestroy(): void {
        // Unsubscribe the event
        this.dtTrigger.unsubscribe();
        clearInterval(this.portListTimerId);
        if (this.portListSubscriber) {
            this.portListSubscriber.unsubscribe();
            this.portListSubscriber = null;
        }
        if (this.portFlowSubscription && this.portFlowSubscription.length) {
            this.portFlowSubscription.forEach((subscription) => subscription.unsubscribe());
            this.portFlowSubscription = [];
        }
        this.loadPorts = false;
    }

    rerender(): void {
        try {
            this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
                // Destroy the table first
                try {

                    dtInstance.destroy();
                    this.dtTrigger.next();
                } catch (err) {
                }
            });
        } catch (err) {
        }

        this.initiateInterval();
    }

    initiateInterval() {
        const interval = setInterval(() => {
            this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
                if (this.hasStoreSetting) {
                    dtInstance.columns([1]).visible(true);
                } else {
                    dtInstance.columns([1]).visible(false);
                }
                clearInterval(interval);
            });
        }, 1000);

    }

    ngOnChanges(change: SimpleChanges) {
        if (change.loader) {
            if (change.loadinterval.currentValue) {
                this.loadinterval = change.loadinterval.currentValue;
            }
        }
    }

    createLagPort() {
        const modalRef = this.modalService.open(CreateLagPortComponent, {size: 'lg', windowClass: 'modal-port slideInUp'});
        modalRef.componentInstance.emitService.subscribe((createLagPort: CreateLagPortModel) => {
            this.loaderService.show(MessageObj.apply_changes);
            this.switchService.createLagLogicalPort(createLagPort, this.switch_id).subscribe(res => {
                if (res) {
                    this.toastr.success(MessageObj.create_lag_port, 'Success');
                    this.loaderService.hide();
                    modalRef.componentInstance.activeModal.close(true);
                    this.initPortListData();
                }

            }, error => {
                this.loaderService.hide();
                modalRef.componentInstance.activeModal.close(true);
                const message = `${error.error['error-auxiliary-message'] + ','} ${error.error['error-description']}`;
                this.toastr.error(message, 'Error');
            });
        });
    }
}

