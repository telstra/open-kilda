import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
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
  switchFlows:any;
  openedTab : string = 'port';
  isSwitchNameEdit = false;
  isStorageDBType= false;
  evacuate:boolean = false;;
  underMaintenance:boolean;
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
      localStorage.removeItem('portLoaderEnabled');
      this.getSwitchDetail(params['id'],filter);
    });

    if(this.router.url.includes("/port")){
         this.router.navigated = false;
        this.router.navigate([this.router.url]);
    }
    this.commonService.getAllSettings().subscribe((response)=>{
      this.isStorageDBType = response && response['SWITCH_NAME_STORAGE_TYPE']=="DATABASE_STORAGE";
    },error=>{

    })
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
                this.loaderService.show("Deleting Switch");
                this.switchService.deleteSwitch(
                  this.switchId,
                  { code: otp },
                  response => {
                    modalRef.close();
                    this.toastr.success("Switch deleted successfully", "Success!");
                    this.loaderService.hide();
                    localStorage.removeItem('switchDetailsJSON');
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
                this.toastr.error("Unable to detect OTP", "Error!");
              }
            },
            error => {
            }
          );
        }else{
          const modalRef2 = this.modalService.open(ModalComponent);
          modalRef2.componentInstance.title = "Warning";
          modalRef2.componentInstance.content = 'You are not authorised to delete the switch.';
        }
        
      }
    });
  }

  toggleTab(tab, enableLoader = false){
    this.openedTab = tab;
    if(tab == 'flows'){
        this.loadSwitchFlows(this.switchDetail.switch_id);
    }else if(enableLoader){
      this.isLoaderActive = true;
    }else{
      this.isLoaderActive = false;
    }
  }

  loadSwitchFlows(switchId){
    this.loaderService.show('Loading Flows..');
    var filter = this.switchFlowFlag =='inventory' ;
    this.loadswitchFlows = false;
    this.switchService.getSwitchFlows(switchId,filter,null).subscribe(data=>{
      this.switchFlows = data;
      this.loadswitchFlows = true;
    },error=>{
      this.loaderService.hide();
      this.switchFlows = [];
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
          this.loaderService.show('Saving Switch Name..');
          let name = this.switchNameForm.controls['name'].value;
          let switchId = this.switch_id;
          this.switchService.saveSwitcName(name,switchId).subscribe((response)=>{
            self.loaderService.hide();
            self.name = response.name;
            self.switchDetail.name = response.name;
            let retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
            localStorage.removeItem('switchDetailsJSON');
            retrievedSwitchObject.name = response.name;
            localStorage.setItem('switchDetailsJSON',JSON.stringify(retrievedSwitchObject));
            localStorage.removeItem('SWITCHES_LIST');
          },(error)=>{ 
            this.loaderService.hide();
          })
        }
      });
  }

  ngAfterViewInit(){
    
  }

  getSwitchDetail(switchId,filter){

    this.loaderService.show("Loading Switch Details");

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
      }else{
        this.switchService.getSwitchDetail(switchId,filter).subscribe((retrievedSwitchObject : any)=>{
          if(!retrievedSwitchObject){
            this.loaderService.hide();
            this.toastr.error("No Switch Found",'Error');
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
        },err=>{
  
            this.loaderService.hide();
            this.toastr.error("No Switch Found",'Error');
            this.router.navigate(['/switches']);
  
        });
      }
      
    });
    let query = {_:new Date().getTime()};
    this.storeSwitchService.checkSwitchStoreDetails(query);

    
    
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

          this.switchService.switchMaintenance(data,this.switchId).subscribe((response)=>{
            this.toastr.success('Maintenance mode changed successful','Success');
            this.underMaintenance = e.target.checked;
            if(evacuate){
              location.reload();
            }
          },error => {
            this.toastr.error('Error in changing maintenance mode! ','Error');
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
          this.toastr.success('All flows are evacuated successfully!','Success');
          location.reload();
        },error => {
          this.toastr.error('Error in evacuating flows! ','Error');
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


