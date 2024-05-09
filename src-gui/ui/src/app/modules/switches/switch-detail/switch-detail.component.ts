import {AfterViewInit, Component, OnDestroy, OnInit} from '@angular/core';
import {SwitchService} from '../../../common/services/switch.service';
import {SwitchidmaskPipe} from '../../../common/pipes/switchidmask.pipe';
import {ToastrService} from 'ngx-toastr';
import {ActivatedRoute, NavigationEnd, Router} from '@angular/router';
import {filter} from 'rxjs/operators';
import {LoaderService} from '../../../common/services/loader.service';
import {ClipboardService} from 'ngx-clipboard';
import {Title} from '@angular/platform-browser';
import {CommonService} from '../../../common/services/common.service';
import {StoreSettingtService} from 'src/app/common/services/store-setting.service';
import {FormBuilder, FormGroup} from '@angular/forms';
import {NgbModal} from '@ng-bootstrap/ng-bootstrap';
import {ModalconfirmationComponent} from 'src/app/common/components/modalconfirmation/modalconfirmation.component';
import {
    IslmaintenancemodalComponent
} from 'src/app/common/components/islmaintenancemodal/islmaintenancemodal.component';
import {ModalComponent} from '../../../common/components/modal/modal.component';
import {OtpComponent} from '../../../common/components/otp/otp.component';
import {MessageObj} from 'src/app/common/constants/constants';
import {SwitchupdatemodalComponent} from 'src/app/common/components/switchupdatemodal/switchupdatemodal.component';

@Component({
    selector: 'app-switch-detail',
    templateUrl: './switch-detail.component.html',
    styleUrls: ['./switch-detail.component.css']
})
export class SwitchDetailComponent implements OnInit, AfterViewInit, OnDestroy {
    switchDetail: any = {};
    inventorySwitch: any = {};
    switchId = null;
    switchNameForm: FormGroup;
    loadswitchFlows = false;
    name: string;
    switchFlows: any = [];
    openedTab = 'port';
    isSwitchNameEdit = false;
    evacuate = false;
    underMaintenance: boolean;
    flowBandwidthSum: any = 0;
    flowBandwidthFlag = false;
    currentRoute = 'switch-details';
    switchFlowFlag: any = 'controller';
    clipBoardItems = {
        sourceSwitchName: '',
        sourceSwitch: '',
        inventorySourceSwitch: '',
        targetSwitchName: ''
    };
    hasStoreSetting;
    settingSubscriber = null;

    isLoaderActive = true;

    statusDiscrepancy = false;
    discrepancyData = {
        status: {
            controller: '-',
            inventory: '-'
        }
    };


    constructor(private switchService: SwitchService,
                private maskPipe: SwitchidmaskPipe,
                private toastr: ToastrService,
                private router: Router,
                private route: ActivatedRoute,
                private loaderService: LoaderService,
                private clipboardService: ClipboardService,
                private titleService: Title,
                private commonService: CommonService,
                private storeSwitchService: StoreSettingtService,
                private formBuilder: FormBuilder,
                private modalService: NgbModal,
    ) {

        this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1';
    }

    ngOnInit() {
        this.titleService.setTitle('OPEN KILDA - View Switch');

        this.route.params.subscribe(params => {
            this.switchId = params['id'];
            const filter = sessionStorage.getItem('switchFilterFlag');
            this.switchFlowFlag = filter;
            localStorage.removeItem('portLoaderEnabled');
            this.getSwitchDetail(params['id'], filter);
        });

        if (this.router.url.includes('/port')) {
            this.router.navigated = false;
            this.router.navigate([this.router.url]);
        }
        this.router.events
            .pipe(filter(event => event instanceof NavigationEnd)).pipe(filter(event => event instanceof NavigationEnd))
            .subscribe(event => {
                const tempRoute: any = event;
                if (tempRoute.url.includes('/port')) {
                    this.currentRoute = 'port-details';
                } else {
                    this.currentRoute = 'switch-details';
                }
            });
        this.switchNameForm = this.formBuilder.group({
            name: ['']
        });
    }

    maskControllerSwitchId(switchType, e) {
        if (e.target.checked) {
            this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id, 'legacy');
        } else {
            this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id, 'kilda');
        }
        this.clipBoardItems.sourceSwitch = this.switchDetail.switch_id;
    }

    maskInventorySwitchId(switchType, e) {
        if (e.target.checked) {
            this.switchDetail.inventory_switch_detail["switch-id"] = this.maskPipe.transform(this.switchDetail.inventory_switch_detail["switch-id"], 'legacy');
        } else {
            this.switchDetail.inventory_switch_detail["switch-id"] = this.maskPipe.transform(this.switchDetail.inventory_switch_detail["switch-id"], 'kilda');
        }
        this.clipBoardItems.inventorySourceSwitch = this.switchDetail.inventory_switch_detail["switch-id"];
    }

    deleteSwitch() {
        const is2FaEnabled = localStorage.getItem('is2FaEnabled');
        const self = this;
        const modalReff = this.modalService.open(ModalconfirmationComponent);
        modalReff.componentInstance.title = 'Delete Switch';
        modalReff.componentInstance.content = 'Are you sure you want to perform delete action ?';
        modalReff.result.then((response) => {
            if (response && response == true) {
                if (is2FaEnabled == 'true') {
                    const modalRef = this.modalService.open(OtpComponent);
                    modalRef.componentInstance.emitService.subscribe(
                        otp => {
                            if (otp) {
                                this.loaderService.show(MessageObj.deleting_switch);
                                this.switchService.deleteSwitch(
                                    this.switchId,
                                    {code: otp},
                                    response => {
                                        modalRef.close();
                                        this.toastr.success(MessageObj.switch_deleted, 'Success!');
                                        this.loaderService.hide();
                                        const switchDetailsKey = 'switchDetailsKey_' + this.switchId;
                                        localStorage.removeItem(switchDetailsKey);
                                        localStorage.removeItem('SWITCHES_LIST');
                                        localStorage.removeItem('switchPortDetail');
                                        this.router.navigate(['/switches']);
                                    },
                                    error => {
                                        this.loaderService.hide();
                                        this.toastr.error(
                                            error['error-auxiliary-message'],
                                            'Error!'
                                        );

                                    }
                                );
                            } else {
                                this.toastr.error(MessageObj.otp_not_detected, 'Error!');
                            }
                        },
                        error => {
                        }
                    );
                } else {
                    const modalRef2 = this.modalService.open(ModalComponent);
                    modalRef2.componentInstance.title = 'Warning';
                    modalRef2.componentInstance.content = MessageObj.not_authorised_to_delete_switch;
                }
            }
        });
    }

    toggleTab(tab, enableLoader = false) {
        this.openedTab = tab;
        if (tab == 'flows') {
            if (this.switchFlows && this.switchFlows.length) {

            } else {
                this.loadSwitchFlows(this.switchDetail.switch_id, true);
            }
        } else if (enableLoader) {
            this.isLoaderActive = true;
        } else {
            this.isLoaderActive = false;
        }
    }

    refreshSwitchFlows() {
        this.loadSwitchFlows(this.switchDetail.switch_id, true);
    }

    loadSwitchFlows(switchId, loader) {
        if (loader) {
            this.loaderService.show('Loading Flows..');
        }
        const filter = this.switchFlowFlag == 'inventory';
        this.loadswitchFlows = false;
        this.flowBandwidthFlag = true;
        this.flowBandwidthSum = 0;
        this.switchService.getSwitchFlows(switchId, filter, null).subscribe(data => {
            this.switchFlows = data;
            if (this.switchFlows && this.switchFlows.length) {
                for (const flow of this.switchFlows) {
                    this.flowBandwidthSum = parseFloat(this.flowBandwidthSum) + (flow.maximum_bandwidth / 1000);
                }
            } else {
                if (this.switchFlows == null) {
                    this.switchFlows = [];
                }
            }
            if (this.flowBandwidthSum && parseFloat(this.flowBandwidthSum)) {
                this.flowBandwidthSum = parseFloat(this.flowBandwidthSum).toFixed(3);
            }
            this.loadswitchFlows = true;
            this.loaderService.hide();
            this.flowBandwidthFlag = false;
        }, error => {
            this.loaderService.hide();
            this.switchFlows = [];
            this.flowBandwidthFlag = false;
            this.loadswitchFlows = true;
        });
    }

    copyToClip(event, copyItem) {
        this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
    }

    editSwitchName() {
        this.isSwitchNameEdit = true;
    }

    cancelSwitchName() {
        this.isSwitchNameEdit = false;
    }

    saveSwitchName() {
        const modalReff = this.modalService.open(ModalconfirmationComponent);
        modalReff.componentInstance.title = 'Confirmation';
        modalReff.componentInstance.content = 'Are you sure you want to update switch name ?';
        modalReff.result.then((response) => {
            if (response && response == true) {
                this.isSwitchNameEdit = false;
                const self = this;
                this.loaderService.show(MessageObj.saving_switchname);
                const name = this.switchNameForm.controls['name'].value;
                this.switchService.saveSwitcName(name, this.switchId).subscribe((response) => {
                    self.loaderService.hide();
                    self.name = response.name;
                    self.switchDetail.name = response.name;
                    const switchDetailsKey = 'switchDetailsKey_' + this.switchId;
                    const retrievedSwitchObject = JSON.parse(localStorage.getItem(switchDetailsKey));
                    localStorage.removeItem(switchDetailsKey);
                    retrievedSwitchObject.name = response.name;
                    localStorage.setItem(switchDetailsKey, JSON.stringify(retrievedSwitchObject));
                    localStorage.removeItem('SWITCHES_LIST');
                }, (error) => {
                    this.toastr.error(error.error['error-message']);
                    this.loaderService.hide();
                });
            }
        });
    }

    ngAfterViewInit() {

    }

    getSwitchDetail(switchId, filter) {
        this.loaderService.show(MessageObj.loading_switch_detail);

        this.settingSubscriber = this.storeSwitchService.switchSettingReceiver.subscribe(setting => {
                this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1';
                let switchDetail = null;
                let inventorySwitchDetail = null;
                if (filter == 'controller') {
                    const switchData = JSON.parse(localStorage.getItem('SWITCHES_LIST')) || {};
                    const switchList = typeof (switchData.list_data) != 'undefined' ? switchData.list_data : [];
                    if (switchList && switchList.length) {
                        switchList.forEach(swDetail => {
                            if (swDetail.switch_id == switchId) {
                                switchDetail = swDetail;
                                inventorySwitchDetail = swDetail.inventory_switch_detail;
                                return;
                            }
                        });
                    }
                } else {
                    const switchData = JSON.parse(localStorage.getItem('SWITCHES_LIST_ALL')) || {};
                    const switchList = typeof (switchData.list_data) != 'undefined' ? switchData.list_data : [];
                    if (switchList && switchList.length) {
                        switchList.forEach(swDetail => {
                            if (swDetail.switch_id == switchId) {
                                switchDetail = swDetail;
                                inventorySwitchDetail = swDetail.inventory_switch_detail;
                                return;
                            }
                        });
                    }
                }
                if (switchDetail && (switchDetail.switch_id || inventorySwitchDetail["switch-id"])) {
                    this.setSwitchDetails(switchDetail, inventorySwitchDetail);
                } else {
                    this.switchService.getSwitchDetails(switchId, filter).subscribe((retrievedSwitchObject: any) => {
                            if (retrievedSwitchObject == null || retrievedSwitchObject.length == 0) {
                                this.loaderService.hide();
                                this.toastr.error(MessageObj.no_switch_found, 'Error');
                                this.router.navigate([
                                    '/switches'
                                ]);
                            } else {
                                this.setSwitchDetails(retrievedSwitchObject[0], retrievedSwitchObject[0].inventory_switch_detail);
                            }
                        }, err => {
                            this.loaderService.hide();
                            this.toastr.error(MessageObj.no_switch_found, 'Error');
                            this.router.navigate(['/switches']);
                        }
                    );
                }
            }
        );
        const query = {_: new Date().getTime()};
        this.storeSwitchService.checkSwitchStoreDetails(query);
    }


    private setSwitchDetails(switchDetail: any, inventorySwitch: any) {
        this.switchDetail = switchDetail;
        this.inventorySwitch = inventorySwitch;
        const sw_id = switchDetail.switch_id || inventorySwitch['switch-id'];
        this.name = switchDetail.name || inventorySwitch.name;
        this.switchNameForm.controls['name'].setValue(this.name);

        this.underMaintenance = switchDetail['under_maintenance'];
        this.clipBoardItems = Object.assign(this.clipBoardItems, {
            sourceSwitchName: this.name,
            sourceSwitch: sw_id,
            inventorySourceSwitch: inventorySwitch?.['switch-id'],
            targetSwitchName: switchDetail.hostname
        });
        this.loaderService.hide();
        this.loadSwitchFlows(sw_id, false);

        if (this.hasStoreSetting) {
            if (switchDetail.state == inventorySwitch?.status) {
                return;
            }
            this.statusDiscrepancy = true;
            if (switchDetail.state != null) {
                this.discrepancyData.status.controller = switchDetail.state;
            }
            if (inventorySwitch?.status != null) {
                this.discrepancyData.status.inventory = inventorySwitch.status;
            }
        }
    }

    editSwitchLocation() {
        const self = this;
        const locationData = this.switchDetail.location;
        locationData['pop'] = this.switchDetail.pop;
        const modalRef = this.modalService.open(SwitchupdatemodalComponent);
        modalRef.componentInstance.title = 'Update Switch Location';
        modalRef.componentInstance.data = locationData;
        modalRef.result.then((response) => {
        }, error => {
        });
        modalRef.componentInstance.emitService.subscribe(
            data => {
                this.loaderService.show(MessageObj.apply_changes);
                this.switchService.updateSwitch(data, this.switchId).subscribe((response) => {
                    this.toastr.success(MessageObj.switch_updated_success, 'Success');
                    this.loaderService.hide();
                    modalRef.componentInstance.activeModal.close(true);
                    this.switchDetail.pop = response.pop;
                    this.switchDetail.location = response.location;

                }, error => {
                    this.loaderService.hide();
                    const message = (error && error.error && typeof error.error['error-auxiliary-message'] != 'undefined') ? error.error['error-auxiliary-message'] : MessageObj.switch_updated_error;
                    this.toastr.error(message, 'Error');
                });
            },
            error => {
            }
        );
    }

    switchMaintenance(e) {
        const modalRef = this.modalService.open(IslmaintenancemodalComponent);
        modalRef.componentInstance.title = 'Confirmation';
        modalRef.componentInstance.isMaintenance = !this.underMaintenance;
        modalRef.componentInstance.content = 'Are you sure ?';
        this.underMaintenance = e.target.checked;
        modalRef.result.then((response) => {
            if (!response) {
                this.underMaintenance = false;
            }
        }, error => {
            this.underMaintenance = false;
        });
        modalRef.componentInstance.emitService.subscribe(
            evacuate => {
                const data = {'under_maintenance': e.target.checked, 'evacuate': evacuate};
                this.loaderService.show(MessageObj.apply_changes);
                this.switchService.switchMaintenance(data, this.switchId).subscribe((response) => {
                    this.toastr.success(MessageObj.maintenance_mode_changed, 'Success');
                    this.loaderService.hide();
                    this.underMaintenance = e.target.checked;
                    if (evacuate) {
                        location.reload();
                    }
                }, error => {
                    this.loaderService.hide();
                    this.toastr.error(MessageObj.error_im_maintenance_mode, 'Error');
                });
            },
            error => {
            }
        );

    }

    isControllerSwitch() {
        return this.switchDetail?.switch_id != null;
    }

    isInventorySwitch() {
        return this.switchDetail.inventory_switch_detail != null;
    }

    canChangeSwitchName() {
        return !this.isSwitchNameEdit
            && this.commonService.hasPermission('sw_switch_update_name')
            && this.isControllerSwitch();
    }

    canEvacuate() {
        return this.commonService.hasPermission('sw_switch_maintenance') && !this.isInventorySwitch();
    }

    evacuateSwitch(e) {
        const modalRef = this.modalService.open(ModalconfirmationComponent);
        modalRef.componentInstance.title = 'Confirmation';
        this.evacuate = e.target.checked;
        if (this.evacuate) {
            modalRef.componentInstance.content = 'Are you sure you want to evacuate all flows?';
        } else {
            modalRef.componentInstance.content = 'Are you sure ?';
        }
        modalRef.result.then((response) => {
            if (response && response == true) {
                const data = {'under_maintenance': this.underMaintenance, 'evacuate': e.target.checked};
                this.switchService.switchMaintenance(data, this.switchId).subscribe((serverResponse) => {
                    this.toastr.success(MessageObj.flows_evacuated, 'Success');
                    location.reload();
                }, error => {
                    this.toastr.error(MessageObj.error_flows_evacuated, 'Error');
                });
            } else {
                this.evacuate = false;
            }
        }, error => {
            this.evacuate = false;
        });


    }

    ngOnDestroy() {
        if (this.settingSubscriber) {
            this.settingSubscriber.unsubscribe();
            this.settingSubscriber = null;
        }
    }
}


