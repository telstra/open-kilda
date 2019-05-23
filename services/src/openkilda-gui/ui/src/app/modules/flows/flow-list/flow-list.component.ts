import { AfterViewInit, Component, OnInit, ViewChild, OnDestroy, HostListener, Renderer2, Input, OnChanges, SimpleChanges } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { FlowsService } from '../../../common/services/flows.service';
import { ToastrService } from 'ngx-toastr';
import { Subject } from 'rxjs';
import { Flow } from '../../../common/data-models/flow';
import { Router } from '@angular/router';
import { NgxSpinnerService } from 'ngx-spinner';
import { LoaderService } from '../../../common/services/loader.service';
import { local } from 'd3';
import { CommonService } from '../../../common/services/common.service';
declare var jQuery: any;

@Component({
  selector: 'app-flow-list',
  templateUrl: './flow-list.component.html',
  styleUrls: ['./flow-list.component.css']
})

export class FlowListComponent implements OnDestroy, OnInit, OnChanges, AfterViewInit{
  @Input() srcSwitch : string;
  @Input() dstSwitch : string;

  dataSet: any;

  hide = true;
  storedData = [];
  statusParams = [];
  loadCount = 0;

  loadingData = true;
  storeLinkSetting : boolean = false;
  statusList : any = [];


  constructor(private router:Router, 
    private flowService:FlowsService,
    private toastr: ToastrService,
    private loaderService : LoaderService,
    private renderer: Renderer2,
    private commonService: CommonService
  ) { 
    var flowListData = JSON.parse(localStorage.getItem("flows"));
    if(flowListData){
      var storageTime = flowListData.timeStamp;
      var startTime = new Date(storageTime).getTime();
      var lastTime = new Date().getTime();
      let timeminDiff = lastTime - startTime;
      var diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);
      if(diffMins < 5){
        this.storedData  = flowListData.list_data || [];
        this.dataSet = this.storedData;
      }else{
        this.storedData  =  [];
        this.dataSet = this.storedData;
      }
      
    }else{
        this.storedData  =  [];
        this.dataSet = this.storedData;
    }
    

    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false
    this.statusList = JSON.parse(localStorage.getItem("linkStoreStatusList"));
   }

  ngOnInit(){
    if(this.storeLinkSetting){
      var cachedStatus = localStorage.getItem("activeFlowStatusFilter") || null;
      if(cachedStatus){
      	this.statusParams = [cachedStatus];
      }
      if(this.statusParams.length <=0){
        this.statusParams = ['Active'];
      	localStorage.setItem("activeFlowStatusFilter",this.statusParams.join(","));
      }
    }
    this.getFlowList(this.statusParams);
  }

  ngAfterViewInit(){

  }

  ngOnDestroy(): void {
    
  }

  getFlowList(statusParam){
    this.loadingData = true;
    this.loaderService.show("Loading Flows");
    if(this.storedData && this.storedData.length <=0 ){  
      var statusParam = statusParam.filter(function (el) {
        return el != null && el != "";
      });

      let filtersOptions = statusParam.length > 0 ? { status:statusParam.join(","),_:new Date().getTime()} : {_:new Date().getTime()};
      this.flowService.getFlowsList(filtersOptions).subscribe((data : Array<object>) =>{
        this.dataSet = data || [];
        if(this.dataSet.length == 0){
          this.toastr.info("No Flows Available",'Information');
        }else{
          var flowListData = JSON.stringify({'timeStamp':new Date().getTime(),"list_data":data});
          localStorage.setItem('flows',flowListData);
        }
        this.loadingData = false;     
      },error=>{
        this.toastr.info("No Flows Available",'Information');
        this.loaderService.hide();
        this.loadingData = false;  
        this.dataSet = [];  
      });
    }else{
      this.loadingData = false;
    }
  }

  refreshFlowList(statusParam){
    this.srcSwitch = null;
    this.dstSwitch = null;
    this.statusParams = statusParam;
    localStorage.removeItem('flows');
    this.storedData = [];
    this.getFlowList(statusParam);
  }

  
  
  toggleSearch(e,inputContainer){ 
    
    this[inputContainer] = this[inputContainer] ? false : true;
    if(this[inputContainer]){
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).focus();
      });
    }
    event.stopPropagation();
  }

  stopPropagationmethod(e){
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
   }
  }

  triggerSearch(){ 
    setTimeout(()=>{
      jQuery('#expandedSrcSwitchName').trigger('change');
      jQuery('#expandedTargetSwitchName').trigger('change');
     },1000);
  }
  ngOnChanges(change:SimpleChanges){
     
  }

}
