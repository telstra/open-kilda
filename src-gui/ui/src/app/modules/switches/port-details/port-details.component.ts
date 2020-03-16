import { Component, OnInit, EventEmitter, Output, AfterViewInit, OnDestroy } from '@angular/core';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { FormBuilder, FormGroup, Validators, NgForm } from '@angular/forms';
import * as _moment from 'moment';
import { NgxSpinnerService } from "ngx-spinner";
import { ToastrService } from 'ngx-toastr';
import { DygraphService } from '../../../common/services/dygraph.service';
import { IslDataService } from '../../../common/services/isl-data.service';
import { SwitchService } from '../../../common/services/switch.service';
import { Router, NavigationEnd} from "@angular/router";
import { filter } from "rxjs/operators";
import { LoaderService } from "../../../common/services/loader.service";
import { ClipboardService } from "ngx-clipboard";
import { Title } from '@angular/platform-browser';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalconfirmationComponent } from "../../../common/components/modalconfirmation/modalconfirmation.component";
import { CommonService } from 'src/app/common/services/common.service';


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

  hasStoreSetting = false;
  
  
  currentRoute : any;
  editConfigStatus: boolean = false;
  currentPortState: string;
  requestedPortState: string;
  dateMessage:string;
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
    
    private switchService:SwitchService,
    private clipboardService: ClipboardService,
    private titleService: Title,
    private modalService: NgbModal,
    public commonService:CommonService,
  ) {
    this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Port');
     this.portDataObject = JSON.parse(localStorage.getItem('portDataObject'));
     this.portForm = this.formBuiler.group({
      portStatus: [this.portDataObject.status],
    });
    this.retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
    this.port_src_switch = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'legacy');
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
      this.toastr.info("Nothing is changed", "Information");
    }
    else{

      const modalRef = this.modalService.open(ModalconfirmationComponent);
      modalRef.componentInstance.title = "Confirmation";
      modalRef.componentInstance.content = $('#final_configure_confirm_modal')[0].innerHTML;
      
      modalRef.result.then((response) => {
        if(response && response == true){
            this.loaderService.show("Updating Port Details");
            this.commitConfig();
        }
      });
    }
  }

  commitConfig(){
      let portStatus = this.portForm.value.portStatus;
      this.switchService.configurePort(this.retrievedSwitchObject.switch_id,
                                       this.portDataObject.port_number,
                                       portStatus).subscribe(status => {
        this.loaderService.hide();
        this.toastr.success("Port configured successfully!",'Success');
        this.portDataObject.status = portStatus;
        localStorage.setItem('portDataObject', JSON.stringify(this.portDataObject));
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

