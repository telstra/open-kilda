import {
    AfterViewInit,
    Component,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Renderer2,
    SimpleChanges,
    ViewChild
} from '@angular/core';
import {Subject, Subscription} from 'rxjs';
import {DataTableDirective} from 'angular-datatables';
import {LoaderService} from 'src/app/common/services/loader.service';
import {Router} from '@angular/router';
import {Switch} from 'src/app/common/data-models/switch';
import {StoreSettingtService} from 'src/app/common/services/store-setting.service';
import {ClipboardService} from 'ngx-clipboard';
import {SwitchService} from 'src/app/common/services/switch.service';
import {CommonService} from 'src/app/common/services/common.service';
import {ToastrService} from 'ngx-toastr';
import {MessageObj} from 'src/app/common/constants/constants';

declare let jQuery: any;

@Component({
    selector: 'app-switch-datatable',
    templateUrl: './switch-datatable.component.html',
    styleUrls: ['./switch-datatable.component.css']
})
export class SwitchDatatableComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit {

    @ViewChild(DataTableDirective, {static: true}) datatableElement: DataTableDirective;
    @Input() data = [];
    @Input() switchFilterFlag: string;
    @Input() textSearch: any;
    dtOptions: any = {};
    dtTrigger: Subject<void> = new Subject();
    wrapperHide = false;
    hasStoreSetting = false;
    flowSubscription: Subscription[] = [];
    switch_id = false;
    commonname = false;
    name = false;
    address = false;
    hostname = false;
    poplocation = false;
    description = false;
    sumofflows = false;
    noofflows = false;
    state = false;
    enableExportBtn = false;
    clipBoardItems = [];

    constructor(private loaderService: LoaderService,
        private renderer: Renderer2,
        private router: Router,
        private commonService: CommonService,
        private toastr: ToastrService,
        private storeSwitchService: StoreSettingtService,
        private clipboardService: ClipboardService,
        public switchService: SwitchService
    ) {
        if (!this.commonService.hasPermission('menu_switches')) {
            this.toastr.error(MessageObj.unauthorised);
            this.router.navigate(['/home']);
        }
    }

    ngOnInit() {
        this.wrapperHide = false;
        const ref = this;
        this.dtOptions = {
            pageLength: 10,
            retrieve: true,
            autoWidth: false,
            dom: 'tpli',
            colResize: false,
            'aLengthMenu': [[10, 20, 35, 50, -1], [10, 20, 35, 50, 'All']],
            'responsive': true,
            drawCallback: function () {
                if (jQuery('#switchDataTable tbody tr').length < 10) {
                    jQuery('#switchDataTable_next').addClass('disabled');
                } else {
                    jQuery('#switchDataTable_next').removeClass('disabled');
                }
            },
            'aoColumns': [
                {sWidth: '10%'},
                {sWidth: '10%', 'sType': 'name', 'bSortable': true},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '10%'},
                {sWidth: '15%'},
                {sWidth: '25%'},
                {sWidth: '10%'}
            ],
            language: {
                searchPlaceholder: 'Search'
            },
            initComplete: function (settings, json) {
                setTimeout(function () {
                    ref.loaderService.hide();
                    ref.wrapperHide = true;
                }, this.data.length / 2);
            },
            columnDefs: [
                {targets: [4], visible: false},
                {targets: [9], visible: false}
            ]
        };

        this.fetchSwitchFlowDataObj();

    }

    async fetchSwitchFlowDataObj() {
        if (!this.data || this.data.length === 0) {
            return;
        }
        let processComplete = 1;
        this.data.forEach((switchDetail, i) => {
            this.flowSubscription[i] = this.switchService.getSwitchFlows(
                this.switchService.extractSwitchId(switchDetail),
                !!switchDetail['inventory_switch_detail'], null)
                .subscribe(flows => {
                    const flowsData: any = flows;
                    switchDetail['sumofbandwidth'] = 0;
                    switchDetail['noofflows'] = 0;
                    if (flowsData && flowsData.length) {
                        for (const flow of flowsData) {
                            switchDetail['sumofbandwidth'] = switchDetail['sumofbandwidth'] + (flow.maximum_bandwidth / 1000);
                        }
                        if (switchDetail['sumofbandwidth']) {
                            switchDetail['sumofbandwidth'] = switchDetail['sumofbandwidth'].toFixed(3);
                        }
                        switchDetail['noofflows'] = flowsData.length;
                    }
                }, error => {
                    switchDetail['sumofbandwidth'] = 0;
                    switchDetail['noofflows'] = 0;
                }, () => {
                    processComplete = processComplete + 1;
                    if (this.data.length === processComplete) {
                        this.enableExportBtn = true;
                    }
                });
        });
    }

    exportCsv(val) {
        let headings = ['Switch ID', 'Name', 'Address', 'Hostname', 'Pop Location', 'Sum(Bandwidth) of Flows(Mbps)', 'No Of Flows', 'Description', 'State', 'Evacuate', 'Hardware', 'Location', 'Manufacturer', 'Version', 'Port', 'Serial Number', 'Software', 'Under Maintenance'];

        if (val) {
            headings = ['Switch ID', 'Name', 'Address', 'Hostname', 'Pop Location', 'Sum(Bandwidth) of Flows(Mbps)', 'No Of Flows', 'Description', 'State'];
        }
        const switchService = this.switchService;
        const lineArray = [];
        lineArray.push(headings);
        this.data.forEach(function (switchDetail) {
            const line = [];

            line.push('"' + (switchService.extractSwitchId(switchDetail) || '-') + '"');
            line.push('"' + ((switchDetail.name) ? switchDetail.name : '-') + '"');
            line.push('"' + ((switchDetail.address) ? switchDetail.address : '-') + '"');
            line.push('"' + ((switchDetail.hostname) ? switchDetail.hostname : '-') + '"');
            line.push('"' + (switchDetail['inventory_switch_detail']?.['pop-location'] || '-') + '"');
            line.push('"' + ((switchDetail.sumofbandwidth || switchDetail.sumofbandwidth === 0) ? switchDetail.sumofbandwidth : '-') + '"');
            line.push('"' + ((switchDetail.noofflows || switchDetail.noofflows === 0) ? switchDetail.noofflows : '-') + '"');
            line.push('"' + (switchDetail.description || switchDetail.inventory_switch_detail?.description || '-') + '"');
            line.push('"' + (switchService.extractState(switchDetail) || '-') + '"');
            if (!val) {
                const locationString = 'longitude:' + ((switchDetail.location.longitude) ? switchDetail.location.longitude : '-')
                    + ', latitude:' + ((switchDetail.location.latitude) ? switchDetail.location.latitude : '-')
                    + ', city:' + ((switchDetail.location.city) ? switchDetail.location.city : '-') + ', street:'
                    + ((switchDetail.location.street) ? switchDetail.location.street : '-') + ', Country:'
                    + ((switchDetail.location.country) ? switchDetail.location.country : '-');

                line.push('"' + ((switchDetail.evacuate) ? switchDetail.evacuate : 'false') + '"');
                line.push('"' + ((switchDetail.hardware) ? switchDetail.hardware : '-') + '"');
                line.push('"' + locationString + '"');
                line.push('"' + ((switchDetail.manufacturer || switchDetail.inventory_switch_detail?.manufacturer || '-') + '"'));
                line.push('"' + ((switchDetail.of_version) ? switchDetail.of_version : '-') + '"');
                line.push('"' + ((switchDetail.port) ? switchDetail.port : '-') + '"');
                line.push('"' + ((switchDetail.serial_number) ? switchDetail.serial_number : '-') + '"');
                line.push('"' + ((switchDetail.software) ? switchDetail.software : '-') + '"');
                line.push('"' + ((switchDetail.under_maintenance) ? switchDetail.under_maintenance : 'false') + '"');
            }

            const csvLine = line.join(',');
            lineArray.push(csvLine);
        });
        const fileName = 'OPEN KILDA - Switches';
        const csvContent = lineArray.join('\n');
        const blob = new Blob(['\ufeff' + csvContent], {
            type: 'text/csv;charset=utf-8;'
        });
        const dwldLink = document.createElement('a');
        const url = URL.createObjectURL(blob);
        const isSafariBrowser = navigator.userAgent.indexOf('Safari') !== -1;
        if (isSafariBrowser) {
            dwldLink.setAttribute('target', '_blank');
        }
        dwldLink.setAttribute('href', url);
        dwldLink.setAttribute('download', fileName + '.csv');
        dwldLink.style.visibility = 'hidden';
        document.body.appendChild(dwldLink);
        dwldLink.click();
        document.body.removeChild(dwldLink);
    }


    ngAfterViewInit(): void {
        this.dtTrigger.next();
        this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
            dtInstance.columns().every(function () {
                const that = this;
                $('input', this.header()).on('keyup change', function () {
                    if (that.search() !== this['value']) {
                        that.search(this['value']).draw();
                    }
                });
            });
        });
        this.checkSwitchSettings();
    }

    ngOnDestroy(): void {
        this.dtTrigger.unsubscribe();
        if (this.flowSubscription && this.flowSubscription.length) {
            this.flowSubscription.forEach((subscription) => subscription.unsubscribe());
            this.flowSubscription = [];
        }

    }

    fulltextSearch(value: any) {
        if (this.dtTrigger) {
            this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
                dtInstance.search(value)
                    .draw();
            });
        }
    }

    ngOnChanges(change: SimpleChanges) {
        if (change.data) {
            if (change.data.currentValue) {
                this.data = change.data.currentValue;
                this.clipBoardItems = this.data;
            }
        }
        if (typeof (change.textSearch) !== 'undefined' && change.textSearch.currentValue) {
            this.fulltextSearch(change.textSearch.currentValue);
        }
    }

    showSwitch(switchObj: Switch) {
        const swId = switchObj.switch_id || switchObj['inventory_switch_detail']?.['switch-id'];
        const switchDetailsJSON = {
            'switch_id': swId,
            'name': switchObj.name || switchObj['inventory_switch_detail']?.name,
            'common-name': switchObj['inventory_switch_detail']?.['common-name'],
            'address': switchObj.address,
            'hostname': switchObj.hostname,
            'description': switchObj.description || switchObj['inventory_switch_detail']?.['description'],
            'state': switchObj.state || switchObj['inventory_switch_detail']?.['status']
        };
        const switchDetailsKey = 'switchDetailsKey_' + swId;
        localStorage.setItem(switchDetailsKey, JSON.stringify(switchDetailsJSON));
        sessionStorage.setItem('switchFilterFlag', this.switchFilterFlag);
        this.router.navigate(['/switches/details/' + swId]);
    }

    checkValue(value) {
        if (value === '' || value === undefined) {
            return '-';
        } else {
            return value;
        }
    }

    toggleSearch(e, inputContainer) {
        event.stopPropagation();
        this[inputContainer] = !this[inputContainer];
        if (this[inputContainer]) {
            setTimeout(() => {
                this.renderer.selectRootElement('#' + inputContainer).focus();
            });
        } else {
            setTimeout(() => {
                this.renderer.selectRootElement('#' + inputContainer).value = '';
                jQuery('#' + inputContainer).trigger('change');
            });
        }
    }

    stopPropagationmethod(e) {
        event.stopPropagation();

        if (e.key === 'Enter') {
            return false;
        }
    }

    descrepancyString(row) {
        const text = [];
        if (this.switchService.isControllerSwitch(row)) {
            text.push('controller:true');
        } else {
            text.push('controller:false');
        }

        if (this.switchService.isInventorySwitch(row)) {
            text.push('inventory:true');
        } else {
            text.push('inventory:false');
        }
        return text.join(', ');
    }

    checkSwitchSettings() {

        this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1';
        if (this.hasStoreSetting) {
            this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
                dtInstance.columns([4]).visible(true);
            });
        } else {
            this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
                dtInstance.columns([4]).visible(false);
            });

        }
    }

    copyToClip(event: any, copyItem: string, index: string | number) {
        let copyData;
        if (copyItem == 'name') {
            copyData = (this.clipBoardItems[index]?.inventory_switch_detail?.['common-name']) ?
                this.checkValue(this.clipBoardItems[index].inventory_switch_detail['common-name']) :
                this.checkValue(this.clipBoardItems[index][copyItem]);
        } else {
            copyData = this.checkValue(this.clipBoardItems[index][copyItem]);
        }

        this.clipboardService.copyFromContent(copyData);
    }

    extractState(switchDetail: any): string {
        return this.switchService.extractState(switchDetail);
    }

    hasDiscrepancy(switchDetail: any): boolean {
        return this.switchService.hasDiscrepancy(switchDetail);
    }

    extractSwitchId(switchDetail: any): string {
        return this.switchService.extractSwitchId(switchDetail);
    }

    isControllerSwitch(switchDetail: any): boolean {
        return this.switchService.isControllerSwitch(switchDetail);
    }
}
