import { AfterViewInit, Component, OnInit, ViewChild, OnDestroy, HostListener, Renderer2, Input, OnChanges, SimpleChanges } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { FlowsService } from '../../../common/services/flows.service';
import { ToastrService } from 'ngx-toastr';
import { Subject } from 'rxjs';
import { Flow } from '../../../common/data-models/flow';
import { Router } from '@angular/router';
import { LoaderService } from '../../../common/services/loader.service';
import { MessageObj } from 'src/app/common/constants/constants';
declare var jQuery: any;
import { AffectedFlowListComponent } from 'src/app/modules/reports/affected-flow-list/affected-flow-list.component';
import { CommonService } from 'src/app/common/services/common.service';

@Component({
  selector: 'app-affected-flows-report',
  templateUrl: './affected-flows-report.component.html',
  styleUrls: ['./affected-flows-report.component.css']
})
export class AffectedFlowsReportComponent  implements OnDestroy, OnInit, OnChanges, AfterViewInit{
  @Input() srcSwitch : string;
  @Input() dstSwitch : string;
  @ViewChild(AffectedFlowListComponent) childFlowComponent:AffectedFlowListComponent;

  dataSet: any;

  hide = true;
  storedData = [];
  loadCount = 0;
  textSearch:any;
  loadingData = true;
  statusList : any = [];
  activeStatus :any = '';


  constructor(private router:Router, 
    private flowService:FlowsService,
    private toastr: ToastrService,
    private loaderService : LoaderService,
    private renderer: Renderer2,
    public commonService:CommonService,
  ) { 
    

   }

   

  ngOnInit(){
    let statusParams = [''];
    this.getFlowList(statusParams,true);
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
    this.loaderService.show(MessageObj.loading_flows);
    
    if(this.storedData && this.storedData.length <=0 ){ 
      var statusParam = statusParam.filter(function (el) {
        return el != null && el != "";
      });
      let filtersOptions = statusParam.length > 0 ? { status:statusParam.join(","),controller:filter,_:new Date().getTime()} : {controller:filter,_:new Date().getTime()};
      this.flowService.getFlowsList(filtersOptions).subscribe((data : Array<object>) =>{
        this.dataSet = data || [];
        if(this.dataSet.length == 0){
          this.toastr.info(MessageObj.no_flow_available,'Information');
        }
        this.dataSet = this.dataSet.filter(function(d){
          return d.status == 'DOWN' || d.status == 'DEGRADED';
        });        
        this.loadingData = false;     
      },error=>{
        this.toastr.info("No Flows Available",'Information');
        this.loaderService.hide();
        this.loadingData = false;  
        this.dataSet = [];  
      });
    }else{
      this.loadingData = false;
      this.loaderService.hide();
      
    }
  }

  refreshFlowList(){
    this.textSearch = '';
    let statusParams = [''];
    jQuery('#search-input').val('');
    this.storedData = [];
    this.getFlowList(statusParams,true);
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
