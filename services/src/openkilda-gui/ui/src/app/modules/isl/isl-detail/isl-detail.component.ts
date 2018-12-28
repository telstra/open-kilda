import { Component, OnInit, EventEmitter, Output, AfterViewInit, OnDestroy } from '@angular/core';
import { HttpClient } from "@angular/common/http";
import { IslDetailModel } from '../../../common/data-models/isl-detail-model';
import { Observable } from "rxjs";
import { ActivatedRoute } from '@angular/router';
import { IslModel } from "../../../common/data-models/isl-model";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { IslListService } from '../../../common/services/isl-list.service';
import { DygraphService } from '../../../common/services/dygraph.service';
import { ToastrService } from 'ngx-toastr';
import { IslDataService } from '../../../common/services/isl-data.service';
import { FormBuilder, FormGroup, Validators, NgForm } from '@angular/forms';
import { NgxSpinnerService } from "ngx-spinner";
import { ClipboardService } from "ngx-clipboard";
import * as _moment from 'moment';
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from '@angular/platform-browser';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModalconfirmationComponent } from '../../../common/components/modalconfirmation/modalconfirmation.component';
import { CommonService } from '../../../common/services/common.service';

  declare var moment: any;
  @Component({
    selector: 'app-isl-detail',
    templateUrl: './isl-detail.component.html',
    styleUrls: ['./isl-detail.component.css']
  })

  export class IslDetailComponent implements OnInit, AfterViewInit,OnDestroy {
    
    detailUrl: string = '';
    src_switch:string = '';
    src_port:string = '';
    dst_switch:string = '';
    dst_port:string = '';
    speed:string = '';
    latency:string = '';
    state:string = '';
    available_bandwidth:string = '';
    detailDataObservable : any;
    src_switch_name: string;
    dst_switch_name: string;	
    graphDataForwardUrl: string;
    graphDataBackwardUrl: string;
    responseGraph = [];
    src_switch_kilda: string;
    dst_switch_kilda:string;
    callGraphAPIFlag: boolean = false;
    currentGraphData = {
        data:[],
        startDate:moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
        endDate: moment(new Date()).format("YYYY/MM/DD HH:mm:ss"),
        timezone: "LOCAL"
      };
    graphObj: any;
    message:{};
    getautoReloadValues = this.commonService.getAutoreloadValues();

    filterForm: FormGroup;
    graphMetrics = [];
    autoReloadTimerId = null;
    islForm: FormGroup;
    showCostEditing: boolean = false;
    currentGraphName : string = "ISL Latency Graph";
    dateMessage:string;
     clipBoardItems = {
        sourceSwitchName:"",
        sourceSwitch:"",
        targetSwitchName:"",
        targetSwitch:""
        
  }

    //@Output() autoreloadStatus: EventEmitter<boolean> = new EventEmitter();
    @Output() hideToValue: EventEmitter<any> = new EventEmitter();
    newMessageDetail(){
    this.islDataService.changeMessage(this.currentGraphData)
    }


    constructor(private httpClient:HttpClient,
      private route: ActivatedRoute,
      private maskPipe: SwitchidmaskPipe,
      private islListService:IslListService,
      private toastr: ToastrService,
      private dygraphService:DygraphService,
      private islDataService: IslDataService,
      private formBuiler: FormBuilder,
      private loaderService: LoaderService,
      private clipboardService: ClipboardService,
      private islFormBuiler: FormBuilder,
      private titleService: Title,
      private modalService: NgbModal,
      private commonService: CommonService
    ) {
      
      this.loaderService.show("Loading ISL detail");
    }
    ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View ISL');
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
      graph: ["latency"],
      metric: ["bits"],
      auto_reload: [""],
      auto_reload_time: ["", Validators.compose([Validators.pattern("[0-9]*")])]
    });

    

      this.graphMetrics = this.dygraphService.getPortMetricData();

      var retrievedObject = localStorage.getItem('linkData');

      this.src_switch =JSON.parse(retrievedObject).source_switch;
      this.src_switch_name =JSON.parse(retrievedObject).source_switch_name;
      this.src_port =JSON.parse(retrievedObject).src_port;
      this.dst_switch =JSON.parse(retrievedObject).target_switch;
      this.dst_switch_name =JSON.parse(retrievedObject).target_switch_name;
      this.dst_port =JSON.parse(retrievedObject).dst_port;
      this.speed = JSON.parse(retrievedObject).speed;
      this.latency = JSON.parse(retrievedObject).latency;
      this.state = JSON.parse(retrievedObject).state;
      this.available_bandwidth = JSON.parse(retrievedObject).available_bandwidth;
      this.clipBoardItems = Object.assign(this.clipBoardItems,{
          
          sourceSwitchName: JSON.parse(retrievedObject).source_switch_name,
          sourceSwitch: JSON.parse(retrievedObject).source_switch,
          targetSwitchName: JSON.parse(retrievedObject).target_switch_name,
          targetSwitch: JSON.parse(retrievedObject).target_switch
        });


      this.src_switch_kilda = this.maskPipe.transform(this.src_switch,'legacy');
      this.dst_switch_kilda = this.maskPipe.transform(this.dst_switch,'legacy');
      this.islListService.getIslDetail(this.src_switch, this.src_port, this.dst_switch, this.dst_port).subscribe((data : any) =>{
      if(data!= null){
        this.detailDataObservable = data;
          this.islForm = this.islFormBuiler.group({
          cost: [this.detailDataObservable.cost, Validators.min(0)],
        });
      }
      else{
        this.detailDataObservable = {
          "props": {
          "cost": "-"
          }
          };

            this.islForm = this.islFormBuiler.group({
        cost: [this.detailDataObservable.cost, Validators.min(0)],
        });
      }
     },error=>{
       this.toastr.error("API did not return Cost data.",'Error');
     });

    

    }
     maskSwitchId(switchType, e){
       if(switchType === 'source'){
         if(e.target.checked){
        this.src_switch = this.maskPipe.transform(this.src_switch,'legacy');
      }else{
        this.src_switch = this.maskPipe.transform(this.src_switch,'kilda');
        }
       }
     if(switchType === 'destination'){
      if(e.target.checked){
        this.dst_switch= this.maskPipe.transform(this.dst_switch,'legacy');
      }else{
        this.dst_switch = this.maskPipe.transform(this.dst_switch,'kilda');
      }
    }

       if(switchType == 'source'){
        this.clipBoardItems.sourceSwitch = this.src_switch;
      }else{
        this.clipBoardItems.targetSwitch = this.dst_switch;
      }
    }

   

    showMenu(e){
    e.preventDefault();
    $('.clip-board-button').hide();
    $('.clip-board-button').css({
      top: e.pageY+'px',
         left: (e.pageX-220)+'px',
         "z-index":2,
     }).toggle();
     
  }


   copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  changeDate(input, event) {
    this.filterForm.controls[input].setValue(event.target.value);
    setTimeout(() => {

         this.loadGraphData();
      
    }, 0);
  }


    ngAfterViewInit() {
     this.loadGraphData();

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


get f() {
    return this.filterForm.controls;
  }

  graphChanged(){
    if(this.filterForm.controls.graph.value == "isllossforward"){
      this.currentGraphName = "ISL Loss Packets Forward Graph";
    }
    if(this.filterForm.controls.graph.value == "isllossreverse"){
      this.currentGraphName = "ISL Loss Packets Resverse Graph";
    }
    if(this.filterForm.controls.graph.value == "target"){
      this.currentGraphName = "Destination Graph";
    }
    if(this.filterForm.controls.graph.value == "source"){
      this.currentGraphName = "Source Graph";
    }
    if(this.filterForm.controls.graph.value == "latency"){
      this.currentGraphName = "ISL Latency Graph";
    } 
    this.loadGraphData();
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
    this.loadGraphData();
  }



  loadGraphData(){
     if(this.filterForm.value.graph === 'latency'){
              this.callGraphAPI();
          }  
        else{
          this.callSourceGraphAPI();
        }
  }

  callGraphAPI(){
    
    let formdata = this.filterForm.value;
    let downsampling = formdata.download_sample;
    let autoReloadTime = Number(
      this.filterForm.controls["auto_reload_time"].value
    );
    let metric = formdata.metric;
    let timezone = formdata.timezone;
    let graph = formdata.graph;
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

      this.dygraphService.getForwardGraphData(this.src_switch_kilda,
                                              this.src_port,
                                              this.dst_switch_kilda,
                                              this.dst_port,
                                              downsampling,
                                              graph,
                                              metric,
                                              convertedStartDate,
                                              convertedEndDate).subscribe((dataForward : any) =>{
          this.responseGraph = [];
          if(dataForward[0] !== undefined){
            dataForward[0].tags.direction = "F";
          this.responseGraph.push(dataForward[0]) ;
          }
        
          this.dygraphService.getBackwardGraphData( this.src_switch_kilda,
                                                    this.src_port,
                                                    this.dst_switch_kilda,
                                                    this.dst_port,
                                                    downsampling,
                                                    graph,
                                                    convertedStartDate,
                                                    convertedEndDate).subscribe((dataBackward : any) =>{
          if(dataBackward[0] !== undefined){
            dataBackward[0].tags.direction = "R";
            this.responseGraph.push(dataBackward[0]);
          }
          this.currentGraphData.data = this.responseGraph;
          this.currentGraphData.timezone = timezone;
          this.currentGraphData.startDate = moment(new Date(formdata.fromDate));
          this.currentGraphData.endDate = moment(new Date(formdata.toDate));
          
          this.newMessageDetail()
          this.islDataService.currentMessage.subscribe(message => this.message = message)
          this.loaderService.hide();
       },error=>{
        this.loaderService.hide();
         this.toastr.error("Forward Graph API did not return data.",'Error');
       });
       },error=>{
        this.loaderService.hide();
         this.toastr.error("Backward Graph API did not return data.",'Error');
         
      });
                              
  }




  callSourceGraphAPI(){
     
    let formdata = this.filterForm.value;
    let downsampling = formdata.download_sample;
    let metric = formdata.metric;
    let timezone = formdata.timezone;
    let graph = formdata.graph;

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




    this.dygraphService.getForwardGraphData(this.src_switch_kilda,
                                              this.src_port,
                                              this.dst_switch_kilda,
                                              this.dst_port,
                                              downsampling,
                                              graph,
                                              metric,
                                              convertedStartDate,
                                              convertedEndDate).subscribe((dataForward : any) =>{
          
          this.responseGraph = [];
          if(dataForward[0] !== undefined){
            dataForward[0].tags.direction = "F";
            this.responseGraph.push(dataForward[0]) ;
          }
          if(dataForward[1] !== undefined){
            dataForward[1].tags.direction = "R";
            this.responseGraph.push(dataForward[1]) ;
          }
          this.currentGraphData.data = this.responseGraph;
          this.currentGraphData.timezone = timezone;
          this.currentGraphData.startDate = moment(new Date(formdata.fromDate));
          this.currentGraphData.endDate = moment(new Date(formdata.toDate));
          this.loaderService.hide();
          this.newMessageDetail()
          this.islDataService.currentMessage.subscribe(message => this.message = message)
       },error=>{
        this.loaderService.hide();
         this.toastr.error("Backward Graph API did not return data.",'Error');
         
      });
  }


  editCost(){
    this.showCostEditing = true;
    if(this.detailDataObservable.props.cost == "-"){
      this.detailDataObservable.props.cost = "";
    }


     this.islForm.controls["cost"].setValue(
             this.detailDataObservable.props.cost
            );
  }

  saveEditedCost(){
    if (this.islForm.invalid) {
      this.toastr.error("Please enter valid value for ISL cost.");
      return;
    }

    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to change the cost?';

    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show("Updating ISL cost");
        let costValue = this.islForm.value.cost;
        this.islListService.updateCost(this.src_switch, this.src_port, this.dst_switch, this.dst_port, costValue).subscribe((status: any) => {
          this.loaderService.hide();

          if(typeof(status.successes)!=='undefined' && status.successes > 0){
            this.toastr.success("ISL cost updated successfully!",'Success');
          
            this.showCostEditing = false;
            this.detailDataObservable.props.cost = costValue;
            this.islForm.controls["cost"].setValue(costValue);
            
          }else if(typeof(status.failures)!=='undefined' && status.failures > 0){
            this.toastr.error("Error in updating ISL cost!",'Error');
            this.showCostEditing = false;
          }


          if(this.detailDataObservable.props.cost == ""){
            this.detailDataObservable.props.cost = "-";
            }

        },error => {
          this.showCostEditing = false;
          if(error.status == '500'){
            this.toastr.error(error.error['error-message'],'Error! ');
          }else{
            this.toastr.error("Error in updating ISL cost!",'Error');
          }
        })
      }
    });
  }

  cancelEditedCost(){
    this.showCostEditing = false;
    if(this.detailDataObservable.props.cost == ""){
      this.detailDataObservable.props.cost = "-";
    }
  }

  zoomHandler=(event, x,y,z)=>{
      
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

  convertInMB(value) {
    value = parseInt(value);
    if (value === "" || value == undefined) {
      return "-";
    } else {
      return (value / 1000)+" Mbps";
    }
  }
  ngOnDestroy(){
    if (this.autoReloadTimerId) {
      clearInterval(this.autoReloadTimerId);
    }
  }

}
  