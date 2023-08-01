import { Component, OnInit, AfterViewInit, OnDestroy, NgModuleRef } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ToastrService } from 'ngx-toastr';
import { Router, NavigationEnd, ActivatedRoute} from "@angular/router";
import { filter } from "rxjs/operators";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { ClipboardService } from "ngx-clipboard";
import { Title } from '@angular/platform-browser';
import { CommonService } from '../../../common/services/common.service';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';
import { FormGroup, FormBuilder } from '@angular/forms';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModalconfirmationComponent } from 'src/app/common/components/modalconfirmation/modalconfirmation.component';
import { IslmaintenancemodalComponent } from 'src/app/common/components/islmaintenancemodal/islmaintenancemodal.component';
import { ModalComponent } from '../../../common/components/modal/modal.component';
import { OtpComponent } from "../../../common/components/otp/otp.component"
import { MessageObj } from 'src/app/common/constants/constants';
import { SwitchupdatemodalComponent } from 'src/app/common/components/switchupdatemodal/switchupdatemodal.component';

@Component({
  selector: 'app-switch-detail',
  templateUrl: './switch-detail.component.html',
  styleUrls: ['./switch-detail.component.css']
})
export class SwitchDetailComponent implements OnInit, AfterViewInit,OnDestroy {
  switchDetail:any = {};
  switch_id: string;
  switchNameForm:FormGroup;
  loadswitchFlows= false;
  name: string;
  address: string;
  hostname: string;
  description: string;
  state: string;
  switchFlows:any=[];
  openedTab : string = 'port';
  isSwitchNameEdit = false;
  isStorageDBType= false;
  evacuate:boolean = false;;
  underMaintenance:boolean;
  flowBandwidthSum:any = 0;
  flowBandwidthFlag:boolean = false;
  currentRoute: string = 'switch-details';  
  switchFlowFlag :any = 'controller';
  clipBoardItems = {
    sourceSwitchName:"",
    sourceSwitch:"",
    targetSwitchName:""
  };
  switchId = null;
  hasStoreSetting;
  settingSubscriber =null;

  descrepancyData = {
    status:{
      controller: "-",
      inventory:"-"
    }
  }
  
  isLoaderActive = true;

  statusDescrepancy = false;


  constructor(private switchService:SwitchService,
        private maskPipe: SwitchidmaskPipe,
        private toastr: ToastrService,
        private router: Router,
        private route: ActivatedRoute,
        private loaderService: LoaderService,
        private clipboardService: ClipboardService,
        private titleService: Title,
        private commonService: CommonService,
        private storeSwitchService:StoreSettingtService,
        private formBuilder:FormBuilder,
        private modalService:NgbModal,
        ) {

      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
    }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View Switch');

    this.route.params.subscribe(params => {
      this.switchId = params['id'];
      var filter = localStorage.getItem("switchFilterFlag");
      this.switchFlowFlag = filter;
      localStorage.removeItem('portLoaderEnabled');
      this.getSwitchDetail(params['id'],filter);
    });

    if(this.router.url.includes("/port")){
         this.router.navigated = false;
        this.router.navigate([this.router.url]);
    }
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd)).pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        let tempRoute : any = event;
        if(tempRoute.url.includes("/port")){
          this.currentRoute = 'port-details';
        }
        else{
          this.currentRoute = 'switch-details';
        }
      });
      this.switchNameForm = this.formBuilder.group({
        name: [""]
      });   
  }

  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id,'legacy');
    } else {
      this.switchDetail.switch_id = this.maskPipe.transform(this.switchDetail.switch_id,'kilda');
    }
    this.clipBoardItems.sourceSwitch = this.switchDetail.switch_id;
  }

  deleteSwitch(){
    let is2FaEnabled  = localStorage.getItem('is2FaEnabled')
    var self = this;
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Delete Switch";
    modalReff.componentInstance.content = 'Are you sure you want to perform delete action ?';    
    modalReff.result.then((response) => {
      if(response && response == true){
        if(is2FaEnabled == 'true'){
          const modalRef = this.modalService.open(OtpComponent);
          modalRef.componentInstance.emitService.subscribe(
            otp => {
              if (otp) {
                this.loaderService.show(MessageObj.deleting_switch);
                this.switchService.deleteSwitch(
                  this.switchId,
                  { code: otp },
                  response => {
                    modalRef.close();
                    this.toastr.success(MessageObj.switch_deleted, "Success!");
                    this.loaderService.hide();
                    const switchDetailsKey = 'switchDetailsKey_' + this.switchId;
                    localStorage.removeItem(switchDetailsKey);
                    localStorage.removeItem('SWITCHES_LIST');
                    localStorage.removeItem('switchPortDetail');
                    this.router.navigate(["/switches"]);
                  },
                  error => {
                    this.loaderService.hide();
                    this.toastr.error(
                      error["error-auxiliary-message"],
                      "Error!"
                    );
                    
                  }
                );
              } else {
                this.toastr.error(MessageObj.otp_not_detected, "Error!");
              }
            },
            error => {
            }
          );
        }else{
          const modalRef2 = this.modalService.open(ModalComponent);
          modalRef2.componentInstance.title = "Warning";
          modalRef2.componentInstance.content = MessageObj.not_authorised_to_delete_switch;
        }        
      }
    });
  }

  toggleTab(tab, enableLoader = false){
    this.openedTab = tab;
    if(tab == 'flows'){
      if(this.switchFlows && this.switchFlows.length){

      }else{
        this.loadSwitchFlows(this.switchDetail.switch_id,true);
      }        
    }else if(enableLoader){
      this.isLoaderActive = true;
    }else{
      this.isLoaderActive = false;
    }
  }
  refreshSwitchFlows(){
    this.loadSwitchFlows(this.switchDetail.switch_id,true);
  }
  loadSwitchFlows(switchId,loader){
    if(loader){
      this.loaderService.show('Loading Flows..');
    }
    var filter = this.switchFlowFlag =='inventory' ;
    this.loadswitchFlows = false;
    this.flowBandwidthFlag = true;
    this.flowBandwidthSum = 0;
    this.switchService.getSwitchFlows(switchId,filter,null).subscribe(data=>{
      this.switchFlows = data;
      if(this.switchFlows && this.switchFlows.length){
        for(let flow of this.switchFlows){
            this.flowBandwidthSum = parseFloat(this.flowBandwidthSum) + (flow.maximum_bandwidth / 1000);
        }
      }else{
        if(this.switchFlows == null){
          this.switchFlows = [];
        }
      }
      if(this.flowBandwidthSum && parseFloat(this.flowBandwidthSum)){
        this.flowBandwidthSum = parseFloat(this.flowBandwidthSum).toFixed(3);
      }
      this.loadswitchFlows = true;
      this.loaderService.hide();
      this.flowBandwidthFlag = false;
    },error=>{
      this.loaderService.hide();
      this.switchFlows = [];
      this.flowBandwidthFlag = false;
      this.loadswitchFlows = true;
    })
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  editSwitchName(){
    this.isSwitchNameEdit = true;
  }

  cancelSwitchName(){
      this.isSwitchNameEdit = false;
  }

  saveSwitchName(){
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to update switch name ?';
    modalReff.result.then((response) => {
      if(response && response == true){
          this.isSwitchNameEdit = false;
          var self =this;
          this.loaderService.show(MessageObj.saving_switchname);
          let name = this.switchNameForm.controls['name'].value;
          let switchId = this.switch_id;
          this.switchService.saveSwitcName(name,switchId).subscribe((response)=>{
            self.loaderService.hide();
            self.name = response.name;
            self.switchDetail.name = response.name;
            const switchDetailsKey = 'switchDetailsKey_' + this.switch_id;
            const retrievedSwitchObject = JSON.parse(localStorage.getItem(switchDetailsKey));
            localStorage.removeItem(switchDetailsKey);
            retrievedSwitchObject.name = response.name;
            localStorage.setItem(switchDetailsKey, JSON.stringify(retrievedSwitchObject));
            localStorage.removeItem('SWITCHES_LIST');
          },(error)=>{ 
            this.toastr.error(error.error['error-message']);
            this.loaderService.hide();
          })
        }
      });
  }

  ngAfterViewInit(){
    
  }

  getSwitchDetail(switchId,filter){

    this.loaderService.show(MessageObj.loading_switch_detail);

    this.settingSubscriber = this.storeSwitchService.switchSettingReceiver.subscribe(setting=>{
      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;

      let switchDetail = null;
      if (filter == 'controller') {
        var switchData = JSON.parse(localStorage.getItem('SWITCHES_LIST')) || {};
        let switchList = typeof(switchData.list_data) != 'undefined' ? switchData.list_data: [];
        if (switchList && switchList.length) {
          switchList.forEach(element => { 
            if (element.switch_id == switchId) {
              switchDetail = element;
              return;
            }
          });
        }
      }else{
        var switchData = JSON.parse(localStorage.getItem('SWITCHES_LIST_ALL')) || {};
        let switchList = typeof(switchData.list_data) != 'undefined' ? switchData.list_data: [];
        if (switchList && switchList.length) {
          switchList.forEach(element => { 
            if (element.switch_id == switchId) {
              switchDetail = element;
              return;
            }
          });
        }
      }
      if(switchDetail && switchDetail.switch_id){
        this.switchDetail = switchDetail;
        this.switch_id =switchDetail.switch_id;
        this.switchNameForm.controls['name'].setValue(switchDetail.name);
        this.name =switchDetail.name;
        this.address =switchDetail.address;
        this.hostname =switchDetail.hostname;
        this.description =switchDetail.description;
        this.state =switchDetail.state;
        this.underMaintenance = switchDetail["under_maintenance"];
        this.clipBoardItems = Object.assign(this.clipBoardItems,{
            
            sourceSwitchName: switchDetail.name,
            sourceSwitch: this.switch_id,
            targetSwitchName: switchDetail.hostname
            
          });
          this.loaderService.hide();
          if(switchDetail['discrepancy'] && (switchDetail['discrepancy']['status'])){
            if(switchDetail['discrepancy']['status']){
              this.statusDescrepancy  = true;
              this.descrepancyData.status.controller = (typeof(switchDetail['discrepancy']['status-value']['controller-status'])!='undefined') ?  switchDetail['discrepancy']['status-value']['controller-status'] : "-";
              this.descrepancyData.status.inventory = (typeof(switchDetail['discrepancy']['status-value']['inventory-status'])!='undefined') ?  switchDetail['discrepancy']['status-value']['inventory-status'] : "-";
            }
            
          }
          this.loadSwitchFlows(this.switchDetail.switch_id,false);
      }else{
        this.switchService.getSwitchDetail(switchId,filter).subscribe((retrievedSwitchObject : any)=>{
          if(!retrievedSwitchObject){
            this.loaderService.hide();
            this.toastr.error(MessageObj.no_switch_found,'Error');
            this.router.navigate([
              "/switches"
            ]);        
          }else{
          this.switchDetail = retrievedSwitchObject;
          this.switch_id =retrievedSwitchObject.switch_id;
          this.switchNameForm.controls['name'].setValue(retrievedSwitchObject.name);
          this.name =retrievedSwitchObject.name;
          this.address =retrievedSwitchObject.address;
          this.hostname =retrievedSwitchObject.hostname;
          this.description =retrievedSwitchObject.description;
          this.state =retrievedSwitchObject.state;
          this.underMaintenance = retrievedSwitchObject["under_maintenance"];
          this.clipBoardItems = Object.assign(this.clipBoardItems,{
              sourceSwitchName: retrievedSwitchObject.name,
              sourceSwitch: this.switch_id,
              targetSwitchName: retrievedSwitchObject.hostname
             });
            this.loaderService.hide();
            if(retrievedSwitchObject['discrepancy'] && (retrievedSwitchObject['discrepancy']['status'])){
              if(retrievedSwitchObject['discrepancy']['status']){
                this.statusDescrepancy  = true;
                this.descrepancyData.status.controller = (typeof(retrievedSwitchObject['discrepancy']['status-value']['controller-status'])!='undefined') ?  retrievedSwitchObject['discrepancy']['status-value']['controller-status'] : "-";
                this.descrepancyData.status.inventory = (typeof(retrievedSwitchObject['discrepancy']['status-value']['inventory-status'])!='undefined') ?  retrievedSwitchObject['discrepancy']['status-value']['inventory-status'] : "-";
              }              
            }
          }
          this.loadSwitchFlows(this.switchDetail.switch_id,false);
        },err=>{
            this.loaderService.hide();
            this.toastr.error(MessageObj.no_switch_found,'Error');
            this.router.navigate(['/switches']);
  
        });
      }
      
    });
    let query = {_:new Date().getTime()};
    this.storeSwitchService.checkSwitchStoreDetails(query);  
    
  }

  editSwitchLocation(){
    var self = this;
    var locationData = this.switchDetail.location;
    locationData['pop'] = this.switchDetail.pop;
    const modalRef = this.modalService.open(SwitchupdatemodalComponent);
    modalRef.componentInstance.title = "Update Switch Location";
    modalRef.componentInstance.data =locationData ;
    modalRef.result.then((response) =>{
     },error => {
    })
    modalRef.componentInstance.emitService.subscribe(
      data => {
          this.loaderService.show(MessageObj.apply_changes);
          this.switchService.updateSwitch(data,this.switchId).subscribe((response)=>{
            this.toastr.success(MessageObj.switch_updated_success,'Success');
            this.loaderService.hide();
            modalRef.componentInstance.activeModal.close(true);
            this.switchDetail.pop = response.pop;
            this.switchDetail.location = response.location;
            
          },error => {
            this.loaderService.hide();
            var message = (error && error.error && typeof error.error["error-auxiliary-message"] !='undefined') ? error.error["error-auxiliary-message"]: MessageObj.switch_updated_error;
            this.toastr.error(message,'Error');
          });
      },
      error => {
      } 
    );
  }
  switchMaintenance(e){
    const modalRef = this.modalService.open(IslmaintenancemodalComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.isMaintenance = !this.underMaintenance;
    modalRef.componentInstance.content = 'Are you sure ?';
    this.underMaintenance = e.target.checked;
    modalRef.result.then((response) =>{
      if(!response){
        this.underMaintenance = false;
      }
    },error => {
      this.underMaintenance = false;
    })
    modalRef.componentInstance.emitService.subscribe(
      evacuate => {
        var data = {"under_maintenance":e.target.checked,"evacuate":evacuate};
          this.loaderService.show(MessageObj.apply_changes);
          this.switchService.switchMaintenance(data,this.switchId).subscribe((response)=>{
            this.toastr.success(MessageObj.maintenance_mode_changed,'Success');
            this.loaderService.hide();
            this.underMaintenance = e.target.checked;
            if(evacuate){
              location.reload();
            }
          },error => {
            this.loaderService.hide();
            this.toastr.error(MessageObj.error_im_maintenance_mode,'Error');
          });
      },
      error => {
      }
    );
    
  }


  evacuateSwitch(e){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    this.evacuate = e.target.checked;
    if(this.evacuate){      
     modalRef.componentInstance.content = 'Are you sure you want to evacuate all flows?';
    }else{
      modalRef.componentInstance.content = 'Are you sure ?';
    }
     modalRef.result.then((response)=>{
      if(response && response == true){
        var data = {"under_maintenance":this.underMaintenance,"evacuate":e.target.checked};
        this.switchService.switchMaintenance(data,this.switchId).subscribe((serverResponse)=>{
          this.toastr.success(MessageObj.flows_evacuated,'Success');
          location.reload();
        },error => {
          this.toastr.error(MessageObj.error_flows_evacuated,'Error');
        })
      }else{
        this.evacuate = false;
      }
    },error => {
      this.evacuate = false;
    })
    
   
  }

  ngOnDestroy(){
    if(this.settingSubscriber){
      this.settingSubscriber.unsubscribe();
      this.settingSubscriber = null;
    }
  }
}


