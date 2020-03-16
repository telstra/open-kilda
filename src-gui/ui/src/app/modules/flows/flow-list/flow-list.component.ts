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
  textSearch:any;
  loadingData = true;
  storeLinkSetting : boolean = false;
  statusList : any = [];
  filterFlag:string= localStorage.getItem('filterFlag') || 'controller';
  activeStatus :any = '';


  constructor(private router:Router, 
    private flowService:FlowsService,
    private toastr: ToastrService,
    private loaderService : LoaderService,
    private renderer: Renderer2,
    public commonService: CommonService
  ) { 
    
    this.checkFlowData();

    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false
    this.statusList = JSON.parse(localStorage.getItem("linkStoreStatusList"));
   }

   checkFlowData(){
      var flowListData:any = {};
     
      if(this.filterFlag == 'controller'){
        flowListData = JSON.parse(localStorage.getItem("flows"));
      }else{
        flowListData = JSON.parse(localStorage.getItem("flowsinventory"));
      }
   
    if(flowListData){
      var storageTime = flowListData.timeStamp;
      var startTime = new Date(storageTime).getTime();
      var lastTime = new Date().getTime();
      let timeminDiff = lastTime - startTime;
      var diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);
      if(diffMins < 30){
        this.storedData  = flowListData.list_data || [];
        this.dataSet = this.storedData;
        if(this.filterFlag == 'inventory'){
          this.dataSet = this.dataSet.filter(function(d){
            return d['inventory-flow'];
          })
        }
      }else{
        this.storedData  =  [];
        this.dataSet = this.storedData;
      }
      
    }else{
        this.storedData  =  [];
        this.dataSet = this.storedData;
    }
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
    this.activeStatus = this.statusParams.join(',');
    this.getFlowList(this.statusParams,this.filterFlag);
  }

  ngAfterViewInit(){

  }

  ngOnDestroy(): void {
    
  }

  fulltextSearch(e){ 
    this.textSearch = e.target.value || ' ';
  }
  getFlowList(statusParam,filter){ 
    this.loadingData = true;
    this.dataSet = [];
    this.loaderService.show("Loading Flows");
    localStorage.removeItem('flowDetail');
    if(filter != null) { 
      this.filterFlag = filter;
      this.checkFlowData();
     }
    if(this.storedData && this.storedData.length <=0 ){ 
      var statusParam = statusParam.filter(function (el) {
        return el != null && el != "";
      });
      let filtersOptions = statusParam.length > 0 ? { status:statusParam.join(","),controller:this.filterFlag == 'controller',_:new Date().getTime()} : {controller:this.filterFlag == 'controller',_:new Date().getTime()};
      this.flowService.getFlowsList(filtersOptions).subscribe((data : Array<object>) =>{
        this.dataSet = data || [];
        if(this.dataSet.length == 0){
          this.toastr.info("No Flows Available",'Information');
        }else{
          var flowListData = JSON.stringify({'timeStamp':new Date().getTime(),"list_data":data});
          localStorage.setItem('filterFlag',this.filterFlag)
          if(this.filterFlag == 'controller'){
            localStorage.setItem('flows',flowListData);
          }else{
            localStorage.setItem('flowsinventory',flowListData);
          }
         
        }
        if(this.filterFlag == 'inventory'){
          this.dataSet = this.dataSet.filter(function(d){
            return d['inventory-flow'];
          })
        }
        this.loadingData = false;     
      },error=>{
        this.toastr.info("No Flows Available",'Information');
        this.loaderService.hide();
        this.loadingData = false;  
        this.dataSet = [];  
      });
    }else{
      setTimeout(()=>{
        this.loadingData = false;
        this.loaderService.hide();
      },100);
      
    }
  }

  refreshFlowList(statusParam){
    this.srcSwitch = null;
    this.dstSwitch = null;
    this.statusParams = [];
    this.statusParams.push(statusParam);

    if(this.filterFlag == 'controller'){
      localStorage.removeItem('flows');
    }else{    
      localStorage.removeItem('flowsinventory');
    }
    this.storedData = [];
    this.getFlowList(this.statusParams,this.filterFlag);
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
