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
import { CommonService } from '../../../common/services/common.service';

declare var moment: any;

@Component({
  selector: 'app-port-details',
  templateUrl: './port-details.component.html',
  styleUrls: ['./port-details.component.css']
})
export class PortDetailsComponent implements OnInit, AfterViewInit, OnDestroy {
  portDataObject: any;
  retrievedSwitchObject: any;
  responseGraph = [];
  port_src_switch: any;
  currentGraphData = {
    data:[],
    startDate:moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
    endDate: moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
    timezone: "LOCAL"
  };
  filterForm: FormGroup;
  portForm: FormGroup;
  autoReloadTimerId = null;
  portMetrics = [];
  currentRoute : any;
  editConfigStatus: boolean = false;
  currentPortState: string;
  requestedPortState: string;
  dateMessage:string;
  getautoReloadValues = this.commonService.getAutoreloadValues();
  clipBoardItems = {
    sourceSwitch:"",
  }
  
  @Output() hideToValue: EventEmitter<any> = new EventEmitter();
  constructor(private maskPipe: SwitchidmaskPipe,
    private formBuiler: FormBuilder,
    private loaderService: LoaderService,
    private toastr: ToastrService,
    private router: Router,
    private dygraphService:DygraphService,
    private islDataService: IslDataService,
    private switchService:SwitchService,
    private clipboardService: ClipboardService,
    private titleService: Title,
    private modalService: NgbModal,
    public commonService: CommonService
  ) {}

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Port');
     this.portDataObject = JSON.parse(localStorage.getItem('portDataObject'));
     this.portForm = this.formBuiler.group({
      portStatus: [this.portDataObject.status],
    });
    this.retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
    this.port_src_switch = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'legacy');
    this.clipBoardItems.sourceSwitch = this.retrievedSwitchObject.switch_id;
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

    var date = new Date();
    var yesterday = new Date(date.getTime());
    yesterday.setDate(date.getDate() - 1);
    var fromStartDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
    var toEndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
    let dateRange = this.getDateRange(); 
    this.filterForm = this.formBuiler.group({
      timezone: ["LOCAL"],
      fromDate: [dateRange.from],
      toDate: [dateRange.to],
      download_sample: ["30s"],
      graph: ["flow"],
      metric: ["bits"],
      direction: ["forward"],
      auto_reload: [""],
      auto_reload_time: ["", Validators.compose([Validators.pattern("[0-9]*")])]
    });


    
    this.portMetrics = this.dygraphService.getPortMetricData();
    this.callPortGraphAPI();
    
  }

  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.retrievedSwitchObject.switch_id = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'legacy');
    } else {
      this.retrievedSwitchObject.switch_id = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'kilda');
    }

    this.clipBoardItems.sourceSwitch = this.retrievedSwitchObject.switch_id;
  } 

  changeDate(input, event) {
    this.filterForm.controls[input].setValue(event.target.value);
    setTimeout(() => {
      this.callPortGraphAPI();
    }, 0);
  }

    ngAfterViewInit() {
   
    this.filterForm.get("auto_reload").valueChanges.subscribe(value => {
      if (value) {
        this.filterForm
          .get("auto_reload_time")
          .setValidators([Validators.required, Validators.pattern("^[0-9]*")]);
      } else {
        this.filterForm
          .get("auto_reload_time")
          .setValidators([Validators.pattern("^[0-9]*")]);
        if (this.autoReloadTimerId) {
          clearInterval(this.autoReloadTimerId);
        }
      }
      this.filterForm.get("auto_reload_time").setValue("");
      this.filterForm.get("auto_reload_time").updateValueAndValidity();
    });
    this.filterForm.get("auto_reload_time").valueChanges.subscribe(value => {});

   setTimeout(()=>{
    jQuery('html, body').animate({ scrollTop: 0 }, 'fast');
   },1000);
    
  }

  startAutoReload() {
    let autoReloadTime = Number(
      this.filterForm.controls["auto_reload_time"].value
    );
    if (this.filterForm.controls["auto_reload"]) {
      if (this.autoReloadTimerId) {
        clearInterval(this.autoReloadTimerId);
      }
      if(autoReloadTime){
        this.autoReloadTimerId = setInterval(() => {
          this.callPortGraphAPI();
        }, 1000 * autoReloadTime);
      }
    } else {
      if (this.autoReloadTimerId) {
        clearInterval(this.autoReloadTimerId);
      }
    }
  }

  getDateRange() : any {
    var date = new Date();
    var yesterday = new Date(date.getTime());
    yesterday.setDate(date.getDate() - 1);
    var fromStartDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
    var toEndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");

    var utcStartDate = moment(yesterday).utc().format("YYYY/MM/DD HH:mm:ss")
    var utcToEndDate = moment(date).utc().format("YYYY/MM/DD HH:mm:ss");

    return { from : fromStartDate, to : toEndDate ,utcStartDate : utcStartDate,  utcToEndDate : utcToEndDate };
  }


    changeTimezone(){

     let formdata = this.filterForm.value;
    let timezone = formdata.timezone;
    let dateaRange = this.getDateRange();

    if(timezone == "UTC"){
      this.filterForm.controls['fromDate'].setValue(dateaRange.utcStartDate);
      this.filterForm.controls['toDate'].setValue(dateaRange.utcToEndDate);
    }else{
      this.filterForm.controls['fromDate'].setValue(dateaRange.from);
      this.filterForm.controls['toDate'].setValue(dateaRange.to);
    }
    this.callPortGraphAPI();
  }
   
  callPortGraphAPI(){
    let formdata = this.filterForm.value;
    let direction = formdata.direction;
    let autoReloadTime = Number(
      this.filterForm.controls["auto_reload_time"].value
    );
    let downsampling = formdata.download_sample;
    let metric = formdata.metric;
    let timezone = formdata.timezone;
    if (this.filterForm.controls["auto_reload"]) {
      formdata.toDate = new Date(new Date(formdata.toDate).getTime() + (autoReloadTime * 1000));
    }

    let convertedStartDate = moment(new Date(formdata.fromDate)).utc().format("YYYY-MM-DD-HH:mm:ss");
    let convertedEndDate = moment(new Date(formdata.toDate)).utc().format("YYYY-MM-DD-HH:mm:ss");

    let startDate = moment(new Date(formdata.fromDate));
    let endDate = moment(new Date(formdata.toDate));


    if (
      moment(new Date(formdata.fromDate)).isAfter(new Date(formdata.toDate))
    ) {
      this.toastr.error("Start date can not be after End date", "Error");
      return;
    }


    if (
      moment(new Date(formdata.toDate)).isBefore(new Date(formdata.fromDate))
    ) {
      this.toastr.error("To date should not be less than from date.", "Error");
      return;
    }


    if (formdata.timezone == "UTC") {
      convertedStartDate = moment(new Date(formdata.fromDate)).format("YYYY-MM-DD-HH:mm:ss");
      convertedEndDate = moment(new Date(formdata.toDate)).format("YYYY-MM-DD-HH:mm:ss");
      
    }
    
    this.dygraphService.
         getForwardGraphData(
           this.port_src_switch,
           this.portDataObject.port_number,
           '', '', downsampling,
           'source',
           metric,
           convertedStartDate,
           convertedEndDate).subscribe((dataForward : any) =>{
                this.loaderService.show();
                this.responseGraph = [];
                if(dataForward[0] !== undefined){
                  dataForward[0].tags.direction = "F";
                  this.responseGraph.push(dataForward[0]) ;
                }
                if(dataForward[1] !== undefined){
                  dataForward[1].tags.direction = "R";
                  this.responseGraph.push(dataForward[1]) ;
                }
                else{
                  if(dataForward[0] !== undefined){
                    dataForward[1] = {"tags": {"direction":"R" },
                                      "metric":"",
                                      "dps": {}};
                  
                  this.responseGraph.push(dataForward[1]) ;
                  }
                }
                 this.loaderService.hide();
                 this.currentGraphData.data = this.responseGraph;
                 this.currentGraphData.timezone = timezone;
                 this.currentGraphData.startDate = moment(new Date(formdata.fromDate));
                 this.currentGraphData.endDate = moment(new Date(formdata.toDate));  
                 this.newMessageDetail()
           },error=>{
             this.toastr.error("Graph API did not return data.",'Error');
             this.loaderService.hide();
          });

            
  }

   newMessageDetail(){
    this.islDataService.changeMessage(this.currentGraphData)
  }

  get f() {
    return this.filterForm.controls;
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

    zoomHandler=(event)=>{
      
      let formdata = this.filterForm.value;

      if(formdata.timezone == 'UTC'){
        var startDate = moment(new Date(event.minX)).utc().format("YYYY/MM/DD HH:mm:ss");
        var endDate = moment( new Date(event.maxX)).utc().format("YYYY/MM/DD HH:mm:ss");

        this.filterForm.controls['fromDate'].setValue(startDate);
        this.filterForm.controls['toDate'].setValue(endDate);
      }else{
        var startDate = moment(new Date(event.minX)).format("YYYY/MM/DD HH:mm:ss");
        var endDate = moment( new Date(event.maxX)).format("YYYY/MM/DD HH:mm:ss");

        this.filterForm.controls['fromDate'].setValue(startDate);
        this.filterForm.controls['toDate'].setValue(endDate);
      }
  
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }
  ngOnDestroy(){
    if (this.autoReloadTimerId) {
      clearInterval(this.autoReloadTimerId);
    }
  }
}

