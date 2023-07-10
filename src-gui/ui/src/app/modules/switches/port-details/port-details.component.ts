import { Component, OnInit, EventEmitter, Output, AfterViewInit, OnDestroy } from '@angular/core';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { FormBuilder, FormGroup, Validators, NgForm } from '@angular/forms';
import * as _moment from 'moment';
import { NgxSpinnerService } from "ngx-spinner";
import { ToastrService } from 'ngx-toastr';
import { DygraphService } from '../../../common/services/dygraph.service';
import { IslDataService } from '../../../common/services/isl-data.service';
import { SwitchService } from '../../../common/services/switch.service';
import { ActivatedRoute, Router, NavigationEnd} from "@angular/router";
import { filter } from "rxjs/operators";
import { LoaderService } from "../../../common/services/loader.service";
import { ClipboardService } from "ngx-clipboard";
import { Title } from '@angular/platform-browser';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalconfirmationComponent } from "../../../common/components/modalconfirmation/modalconfirmation.component";
import { CommonService } from 'src/app/common/services/common.service';
import { MessageObj } from 'src/app/common/constants/constants';

declare var moment: any;

@Component({
  selector: 'app-port-details',
  templateUrl: './port-details.component.html',
  styleUrls: ['./port-details.component.css']
})
export class PortDetailsComponent implements OnInit, OnDestroy{
  portDataObject: any;
  retrievedSwitchObject: any;
  port_src_switch: any;
  openedTab : string = 'graph';
  portForm: FormGroup;
  portFlows :any = [];
  flowBandwidthSum :any = 0;
  flowBandwidthFlag:boolean = false;
  switchId  = null;
  portId  = null;

  hasStoreSetting = false;
  
  
  currentRoute : any;
  editConfigStatus: boolean = false;
  currentPortState: string;
  requestedPortState: string;
  dateMessage:string;  
  discoverypackets:boolean=false;
  clipBoardItems = {
    sourceSwitch:"",
  }

  descrepancyData = {
    assignmentType:{
      controller: "-",
      inventory:"-"
    }
  }

  assignmentTypeDescrepancy = false;
  
  @Output() hideToValue: EventEmitter<any> = new EventEmitter();
  constructor(private maskPipe: SwitchidmaskPipe,
    private formBuiler: FormBuilder,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private router: Router,
    private dygraphService:DygraphService,
    private route: ActivatedRoute,
    
    private switchService:SwitchService,
    private clipboardService: ClipboardService,
    private titleService: Title,
    private modalService: NgbModal,
    public commonService:CommonService,
  ) {
    this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
    if(!this.commonService.hasPermission('menu_switches')){
      this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }
    
  }

  ngOnInit() {
      this.titleService.setTitle('OPEN KILDA - Port');
      this.route.parent.params.subscribe(params => this.switchId = params['id']);
      this.route.params.subscribe(params => this.portId = params['port']);

      const portDataObjectKey = 'portDataObject_' + this.switchId + '_' + this.portId;
      this.portDataObject = JSON.parse(localStorage.getItem(portDataObjectKey));
      this.portForm = this.formBuiler.group({
          portStatus: [this.portDataObject.status],
      });
      const switchDetailsKey = 'switchDetailsKey_' + this.switchId;
      this.retrievedSwitchObject = JSON.parse(localStorage.getItem(switchDetailsKey));
      this.port_src_switch = this.maskPipe.transform(this.retrievedSwitchObject.switch_id, 'legacy');
      this.clipBoardItems.sourceSwitch = this.retrievedSwitchObject.switch_id;
    
    if(this.portDataObject['discrepancy'] && (this.portDataObject['discrepancy']['assignment-type'])){
      if(this.portDataObject['discrepancy']['assignment-type']){
        this.assignmentTypeDescrepancy  = true;
        this.descrepancyData.assignmentType.controller = (typeof(this.portDataObject['discrepancy']['controller-assignment-type'])!='undefined') ?  this.portDataObject['discrepancy']['controller-assignment-type'] : "-";
        this.descrepancyData.assignmentType.inventory = (typeof(this.portDataObject['discrepancy']['inventory-assignment-type'])!='undefined') ?  this.portDataObject['discrepancy']['inventory-assignment-type'] : "-";
      }
      
    }
   
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd)) .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        let tempRoute : any = event; 
        if(tempRoute.url.includes("/port")){
          this.currentRoute = 'port-details';
        }
        else{
          this.currentRoute = 'switch-details';
        }
   });
    this.loadPortFlows();
     this.getDiscoveryPackets();
  }

  getDiscoveryPackets(){
    this.switchService.getdiscoveryPackets(this.retrievedSwitchObject.switch_id,this.portDataObject.port_number).subscribe((response:any)=>{
       this.discoverypackets = response.discovery_enabled;
    },error => {
      //this.toastr.error('Error in updating discovery packets mode! ','Error');
    });
  }

  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.retrievedSwitchObject.switch_id = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'legacy');
    } else {
      this.retrievedSwitchObject.switch_id = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'kilda');
    }

    this.clipBoardItems.sourceSwitch = this.retrievedSwitchObject.switch_id;
  } 

  savePortDetails(){

    this.currentPortState = this.portDataObject.status;
    this.requestedPortState = this.portForm.value.portStatus;
    $('#old_status_val').text(this.portDataObject.status);
    $("#new_status_val").text(this.portForm.value.portStatus);

    if(this.currentPortState == this.requestedPortState){
      this.toastr.info(MessageObj.nothing_changed, "Information");
    }
    else{

      const modalRef = this.modalService.open(ModalconfirmationComponent);
      modalRef.componentInstance.title = "Confirmation";
      modalRef.componentInstance.content = $('#final_configure_confirm_modal')[0].innerHTML;
      
      modalRef.result.then((response) => {
        if(response && response == true){
            this.loaderService.show(MessageObj.updating_port_details);
            this.commitConfig();
        }
      });
    }
  }

  getPortStatus = (status)=>{
    if(status.toLowerCase() == 'good'){
      return 'UP';
    }else if(status.toLowerCase() == 'bad'){
      return 'DOWN';
    }
   return status;
    
  }

  commitConfig(){
      let portStatus = this.portForm.value.portStatus;
      this.switchService.configurePort(this.retrievedSwitchObject.switch_id,
                                       this.portDataObject.port_number,
                                       portStatus).subscribe(status => {
        this.loaderService.hide();
        this.toastr.success(MessageObj.port_configured,'Success');
        this.portDataObject.status = portStatus;
        const portDataObjectKey = 'portDataObject_' + this.switchId + '_' + this.portId;
        localStorage.setItem(portDataObjectKey, JSON.stringify(this.portDataObject));
        this.editConfigStatus = false;

      },error => {
        this.loaderService.hide();
        this.editConfigStatus = false;
        if(error.status == '500'){
          this.toastr.error(error.error['error-auxiliary-message']);
        }else{
          this.toastr.error("Something went wrong!");
        }
      })
  }

  enableDisableDiscoveryPackets(e){
   const modalRef = this.modalService.open(ModalconfirmationComponent);
   modalRef.componentInstance.title = "Confirmation";
   modalRef.componentInstance.content = 'Are you sure you want to update discovery packets value?';
   var OldValue = this.discoverypackets;
   this.discoverypackets = e.target.checked;
   modalRef.result.then((response) => {
    if(response && response == true){
        this.loaderService.show(MessageObj.updating_discovery_flag);
         this.switchService.updatediscoveryPackets(this.retrievedSwitchObject.switch_id,this.portDataObject.port_number,this.discoverypackets).subscribe((response)=>{
           this.toastr.success(MessageObj.discovery_packets_updated,'Success');
           this.loaderService.hide();
           this.discoverypackets = e.target.checked;
         },error => {
          this.discoverypackets = OldValue;
          this.loaderService.hide();
           this.toastr.error(MessageObj.error_updating_discovery_packets,'Error');
         });
    }else{
      this.discoverypackets = OldValue;
    }
  });
 }

  configurePortDetails(){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to configure the port?';
    modalRef.result.then((response) => {
      if(response && response == true){
        this.editConfigStatus = true;
        this.portForm = this.formBuiler.group({
          portStatus: [this.portDataObject.status],
        });
      }
    });
  }

  loadPortFlows(){
    if(this.portDataObject['port_number'] && (this.retrievedSwitchObject['switch_id'])){
        let switchId = this.retrievedSwitchObject.switch_id;
        let portnumber = this.portDataObject.port_number;
        this.flowBandwidthFlag = true;
        this.switchService.getSwitchFlows(switchId,false,portnumber).subscribe(data=>{
          this.portFlows = data;
          if(this.portFlows && this.portFlows.length){
            for(let flow of this.portFlows){
                this.flowBandwidthSum = this.flowBandwidthSum + (flow.maximum_bandwidth / 1000);
            }
          }else{
            if(this.portFlows == null){
              this.portFlows = [];
            }
          }
          if(this.flowBandwidthSum){
            this.flowBandwidthSum = this.flowBandwidthSum.toFixed(3);
          }
          
          this.flowBandwidthFlag = false;
        },error=>{
          this.portFlows = [];
          this.flowBandwidthSum = 0;
          this.flowBandwidthFlag = false;
        })
    }
    
  }


  cancelConfigurePort(){
     this.editConfigStatus = false;
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  toggleTab(tab){
    this.openedTab = tab;
  }

  assignDate(timestamp){
    if(timestamp){
      return  moment(timestamp*1000).format("LLL");
    }else{
      return '-';
    }

   
  }

  ngOnDestroy(){
    localStorage.setItem('portLoaderEnabled',"1");
  }
}

