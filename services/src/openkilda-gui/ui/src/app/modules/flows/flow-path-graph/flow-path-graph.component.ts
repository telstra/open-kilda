import { Component, OnInit, Input, AfterViewInit, OnDestroy } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { DygraphService } from "../../../common/services/dygraph.service";
import { FormBuilder, FormGroup } from "@angular/forms";
import { ToastrService } from "ngx-toastr";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ClipboardService } from "ngx-clipboard";
declare var moment: any;

@Component({
  selector: "app-flow-path-graph",
  templateUrl: "./flow-path-graph.component.html",
  styleUrls: ["./flow-path-graph.component.css"]
})
export class FlowPathGraphComponent implements OnInit, AfterViewInit, OnDestroy {
  @Input() data?: any;
  @Input("path-type") type?: string;
  cookieData:any;
  selectedCookie:any;

  filterForm: FormGroup;
  flowMetrics = [];
  timezoneData = [{label:'UTC', value:'UTC'},{label:'My Timezone',value:'My'}];
  constructor(
    private flowService: FlowsService,
    private dygraphService: DygraphService,
    private formBuilder: FormBuilder,
    private toaster: ToastrService,
    private switchMask: SwitchidmaskPipe,
    private clipBoardService : ClipboardService
  ) {}

  ngOnInit() {

    this.flowMetrics = this.dygraphService.getFlowMetricData();
    let dateRange = this.getDateRange(); 
    
    this.filterForm = this.formBuilder.group({
      timezone: ["My"],
      startDate: [dateRange.from],
      endDate: [dateRange.to],
      metric:["bits"]
    });

    this.changeFilter();
  }


  getDateRange() : any {

    let fromStartDate = moment()
      .subtract(4, "hour")
      .format("YYYY/MM/DD HH:mm:ss");
    let toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");

    var utcStartDate = moment().subtract(4, "hour").utc().format("YYYY/MM/DD HH:mm:ss")
    var utcToEndDate = moment().utc().format("YYYY/MM/DD HH:mm:ss");

    return { from : fromStartDate, to : toEndDate ,utcStartDate : utcStartDate,  utcToEndDate : utcToEndDate };
  }

  ngAfterViewInit() {}

  changeDate(input, event){
    this.filterForm.controls[input].setValue(event.target.value);
    setTimeout(()=>{
      this.changeFilter();
    },0);
  }

  changeTimeZone(){
    let formdata = this.filterForm.value;
    let timezone = formdata.timezone;
    let dateaRange = this.getDateRange();

    if(timezone == "UTC"){
      this.filterForm.controls['startDate'].setValue(dateaRange.utcStartDate);
      this.filterForm.controls['endDate'].setValue(dateaRange.utcToEndDate);
    }else{
      this.filterForm.controls['startDate'].setValue(dateaRange.from);
      this.filterForm.controls['endDate'].setValue(dateaRange.to);
    }

    this.changeFilter();
  }

  changeFilter() {
    this.getFlowPathStatsData();
  }

  getFlowPathStatsData() {
    let formData = this.filterForm.value;
    
    var fromDate = new Date(formData.startDate);
    var toDate = new Date(formData.endDate);
    if (moment(fromDate).isAfter(toDate)) {
      this.toaster.error("Start date can not be after End date", "error");
      return;
    }
   

    let fromStartDate, toEndDate, startDate, endDate;
    if (typeof fromDate !== "undefined" && typeof toDate !== "undefined") {
      if (formData.timezone == "UTC") {
        startDate = moment(fromDate).format("YYYY-MM-DD-HH:mm:ss");
        endDate = moment(toDate).format("YYYY-MM-DD-HH:mm:ss");
        fromStartDate = moment(fromDate).format("YYYY/MM/DD HH:mm:ss");
        toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss");
      } else {
        startDate = moment(fromDate)
          .utc()
          .format("YYYY-MM-DD-HH:mm:ss");
        endDate = moment(toDate)
          .utc()
          .format("YYYY-MM-DD-HH:mm:ss");
        fromStartDate = moment(fromDate).format("YYYY/MM/DD HH:mm:ss");
        toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss");
      }
    } else {
      if (formData.timezone == "UTC") {
        startDate = moment()
          .subtract(4, "hour")
          .format("YYYY-MM-DD-HH:mm:ss");
        endDate = moment().format("YYYY-MM-DD-HH:mm:ss");
        fromStartDate = moment()
          .subtract(4, "hour")
          .format("YYYY/MM/DD HH:mm:ss");
        toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
      } else {
        startDate = moment()
          .subtract(4, "hour")
          .utc()
          .format("YYYY-MM-DD-HH:mm:ss");
        endDate = moment()
          .utc()
          .format("YYYY-MM-DD-HH:mm:ss");
        fromStartDate = moment()
          .subtract(4, "hour")
          .format("YYYY/MM/DD HH:mm:ss");
        toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
      }
    }

    let switches = [];

    if (this.type == "forward") {
      this.data.flowpath_forward.forEach(element => {
        switches.push(this.switchMask.transform(element.switch_id, "legacy"));
      });
    } else {
      this.data.flowpath_reverse.forEach(element => {
        switches.push(this.switchMask.transform(element.switch_id, "legacy"));
      });
    }
    let metric = formData.metric;
    let requestPayload = {
      flowid: this.data.flowid,
     // switches: switches,
      startdate: startDate,
      enddate: endDate,
      downsample: "30s",
      direction: this.type,
      metric:metric
    };

    this.flowService.getFlowPathStats(requestPayload).subscribe(
      response => {
        let dataforgraph = this.dygraphService.getCookieDataforFlowStats(response,this.type);
        let cookieBasedData = this.dygraphService.getCookieBasedData(response,this.type);
        this.cookieData = Object.keys(cookieBasedData);
        let data = (dataforgraph && dataforgraph.length) ? dataforgraph: [] ;
        let graphdata = {
          data: data,
          startDate: fromDate,
          endDate: toDate,
          type: this.type,
          timezone: formData.timezone,
          loadfromcookie:this.selectedCookie
        };
        this.dygraphService.changeFlowPathGraphData(graphdata);
      },
      error => {
        let dataforgraph = this.dygraphService.getCookieDataforFlowStats([],this.type);
        let cookieBasedData = this.dygraphService.getCookieBasedData([],this.type);
        this.cookieData = Object(cookieBasedData).keys;
        var data = (dataforgraph && dataforgraph.length) ? dataforgraph :[]
        let graphdata = {
          data: data,
          startDate: fromDate,
          endDate: toDate,
          type: this.type,
          timezone: formData.timezone,
          loadfromcookie:this.selectedCookie
        };
        this.dygraphService.changeFlowPathGraphData(graphdata);
      }
    );
  }

  ngOnDestroy(){
   
  }
  loadcookiegraph(cookie){
    this.selectedCookie = cookie;
    let cookieBasedData = JSON.parse(localStorage.getItem('flowPathCookieData'));
    var data = (cookieBasedData && cookieBasedData[cookie])?cookieBasedData[cookie] :[]
       
    let formData = this.filterForm.value;
    
    let graphdata = {
      data: data,
      startDate: formData.startDate,
      endDate: formData.endDate,
      type: this.type,
      timezone: formData.timezone,
      loadfromcookie:this.selectedCookie
    };
    this.dygraphService.changeFlowPathGraphData(graphdata);
  }
  
  zoom =(event)=>{
    let formdata = this.filterForm.value;

    if(formdata.timezone == 'UTC'){
      var startDate = moment(new Date(event.minX)).utc().format("YYYY/MM/DD HH:mm:ss");
      var endDate = moment( new Date(event.maxX)).utc().format("YYYY/MM/DD HH:mm:ss");

      this.filterForm.controls['startDate'].setValue(startDate);
      this.filterForm.controls['endDate'].setValue(endDate);
    }else{
      var startDate = moment(new Date(event.minX)).format("YYYY/MM/DD HH:mm:ss");
      var endDate = moment( new Date(event.maxX)).format("YYYY/MM/DD HH:mm:ss");

      this.filterForm.controls['startDate'].setValue(startDate);
      this.filterForm.controls['endDate'].setValue(endDate);
    }

  }
  
  copyToClipCookie(data){
    this.clipBoardService.copyFromContent(data);
    this.toaster.success('Copied to clicboard');
  }

  changeMetric(){
    this.changeFilter();
  }
}
