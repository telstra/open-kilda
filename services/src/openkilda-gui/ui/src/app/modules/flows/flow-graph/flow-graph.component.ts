import { Component, OnInit, AfterViewInit, Input, OnDestroy, OnChanges, SimpleChange, SimpleChanges } from "@angular/core";
import { DygraphService } from "../../../common/services/dygraph.service";
import { FormGroup, FormBuilder, Validators } from "@angular/forms";
import { FlowsService } from "../../../common/services/flows.service";
import { ToastrService } from "ngx-toastr";
import { CommonService } from "src/app/common/services/common.service";

declare var moment: any;

@Component({
  selector: "app-flow-graph",
  templateUrl: "./flow-graph.component.html",
  styleUrls: ["./flow-graph.component.css"]
})
export class FlowGraphComponent implements OnInit, AfterViewInit, OnDestroy ,OnChanges{
  @Input()
  flowId;

  autoReloadTimerId = null;
  flowMetrics = [];
  packetMetrics = [];
  getautoReloadValues = this.commonService.getAutoreloadValues();
  filterForm: FormGroup;
  constructor(
    private dygraphService: DygraphService,
    private formBuiler: FormBuilder,
    private flowService: FlowsService,
    private toaster: ToastrService,
    private commonService:CommonService
  ) {
    
  }
  ngOnChanges(change:SimpleChanges){
    if(change.flowId && change.flowId.currentValue){
      this.flowId = change.flowId.currentValue;
      this.ngOnInit();
      this.loadGraphData();
    }
  }
  ngOnInit() {
    let dateRange = this.getDateRange(); 
    this.filterForm = this.formBuiler.group({
      timezone: ["LOCAL"],
      fromDate: [dateRange.from],
      toDate: [dateRange.to],
      download_sample: ["30s"],
      graph: ["flow"],
      metric: ["packets"],
      direction: ["forward"],
      auto_reload: [""],
      auto_reload_time: ["", Validators.compose([Validators.pattern("[0-9]*")])]
    });

    this.flowMetrics = this.dygraphService.getFlowMetricData();
    this.packetMetrics = this.dygraphService.getPacketsMetricData();
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

  changeDate(input, event) {
    this.filterForm.controls[input].setValue(event.target.value);
    setTimeout(() => {
      this.loadGraphData();
    }, 0);
  }

  ngAfterViewInit() {
    //this.loadGraphData();

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
          this.loadGraphData();
        }, 1000 * autoReloadTime);
      }
    } else {
      if (this.autoReloadTimerId) {
        clearInterval(this.autoReloadTimerId);
      }
    }
  }

  changeTimeZone(){
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

    this.loadGraphData();
  }

  loadGraphData(){
    let formdata = this.filterForm.value;
    let flowid = this.flowId;
    let autoReloadTime = Number(
      this.filterForm.controls["auto_reload_time"].value
    );
    
    let direction = formdata.direction;
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
      this.toaster.error("Start date can not be after End date", "Error");
      return;
    }
    if (formdata.timezone == "UTC") {
      convertedStartDate = moment(new Date(formdata.fromDate)).format("YYYY-MM-DD-HH:mm:ss");
      convertedEndDate = moment(new Date(formdata.toDate)).format("YYYY-MM-DD-HH:mm:ss");
      
    }

    if (formdata.graph == "flow") {
      this.flowService
        .getFlowGraphData(
          flowid,
          convertedStartDate,
          convertedEndDate,
          downsampling,
          metric
        )
        .subscribe(
          res => {
            this.dygraphService.changeFlowGraphData({
              data: res,
              startDate: startDate,
              endDate: endDate,
              timezone: timezone
            });
          },
          error => {
            this.dygraphService.changeFlowGraphData({
              data: [],
              startDate: startDate,
              endDate: endDate,
              timezone: timezone
            });
          }
        );
    } else {
      this.flowService
        .getFlowPacketGraphData(
          flowid,
          convertedStartDate,
          convertedEndDate,
          downsampling,
          direction
        )
        .subscribe(
          res => {
            this.dygraphService.changeFlowGraphData({
              data: res,
              startDate: startDate,
              endDate: endDate,
              timezone: timezone
            });
          },
          error => {
            this.dygraphService.changeFlowGraphData({
              data: [],
              startDate: startDate,
              endDate: endDate,
              timezone: timezone
            });
          }
        );
    }
  }

  get f() {
    return this.filterForm.controls;
  }
  

  zoom =(event)=>{
      
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

  ngOnDestroy(){
    if (this.autoReloadTimerId) {
      clearInterval(this.autoReloadTimerId);
    }
  }

  
}
