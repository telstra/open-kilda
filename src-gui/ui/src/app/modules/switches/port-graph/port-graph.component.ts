import { Component, OnInit, EventEmitter, Output, AfterViewInit, OnDestroy } from '@angular/core';
import { FormBuilder, FormGroup, Validators, NgForm } from '@angular/forms';
import * as _moment from 'moment';
import { ToastrService } from 'ngx-toastr';
import { DygraphService } from 'src/app/common/services/dygraph.service';
import { SwitchidmaskPipe } from 'src/app/common/pipes/switchidmask.pipe';
import { LoaderService } from 'src/app/common/services/loader.service';
import { IslDataService } from 'src/app/common/services/isl-data.service';
import { CommonService } from 'src/app/common/services/common.service';

declare var moment: any;

@Component({
  selector: 'app-port-graph',
  templateUrl: './port-graph.component.html',
  styleUrls: ['./port-graph.component.css']
})
export class PortGraphComponent implements OnInit, AfterViewInit,OnDestroy {

  portDataObject: any;
  currentGraphData = {
    data:[],
    startDate:moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
    endDate: moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
    timezone: "LOCAL"
  };
  filterForm: FormGroup;
  portForm: FormGroup;
  port_src_switch: any;
  retrievedSwitchObject: any;
  graphSubscriber=null;
  responseGraph = [];
  autoReloadTimerId = null;  
  getautoReloadValues = this.commonService.getAutoreloadValues();
  portMetrics = [];
    
  @Output() hideToValue: EventEmitter<any> = new EventEmitter();
  constructor(
    private maskPipe: SwitchidmaskPipe,
    private formBuiler: FormBuilder,
    private toastr: ToastrService,
    private dygraphService:DygraphService,
    private loaderService: LoaderService,
    private islDataService: IslDataService,
    private commonService:CommonService,
  ) { }

  ngOnInit() {
    this.portDataObject = JSON.parse(localStorage.getItem('portDataObject'));
     this.portForm = this.formBuiler.group({
      portStatus: [this.portDataObject.status],
    });
    this.retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
    this.port_src_switch = this.maskPipe.transform(this.retrievedSwitchObject.switch_id,'legacy');
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
    
    this.graphSubscriber = this.dygraphService.
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

  newMessageDetail(){
    this.islDataService.changeMessage(this.currentGraphData)
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

  get f() {
    return this.filterForm.controls;
  }

  zoomHandler(event){

  }
  ngOnDestroy(){
    if (this.autoReloadTimerId) {
      clearInterval(this.autoReloadTimerId);
    }
    if(this.graphSubscriber){
      this.graphSubscriber.unsubscribe();
      this.graphSubscriber = null;
    }

  }

}
