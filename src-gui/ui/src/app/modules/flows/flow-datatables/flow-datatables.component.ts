import { Component, OnInit, Input, ViewChild, OnChanges, SimpleChange,EventEmitter, SimpleChanges, Renderer2, AfterViewInit, OnDestroy, Output } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Subject } from 'rxjs';
import { Flow } from 'src/app/common/data-models/flow';
import { Router } from '@angular/router';
import { CommonService } from 'src/app/common/services/common.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ClipboardService } from 'ngx-clipboard';
import { FlowReRouteModalComponent } from 'src/app/common/components/flow-re-route-modal/flow-re-route-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { FlowsService } from 'src/app/common/services/flows.service';
declare var jQuery: any;
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-flow-datatables',
  templateUrl: './flow-datatables.component.html',
  styleUrls: ['./flow-datatables.component.css']
})
export class FlowDatatablesComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  @ViewChild(DataTableDirective, { static: true }) datatableElement: DataTableDirective;
  @Input() data = [];
  @Input() srcSwitch : string;
  @Input() dstSwitch : string;
  @Input() filterFlag:string;
  @Input() textSearch:any;
  @Output() refresh =  new EventEmitter();
  @Output() enableReroute =  new EventEmitter();
  

  typeFilter:string = '';
  dtOptions = {};
  reRouteFlowIndex = {};
  reRouteList:any=[];
  checkedFlow = [];
  selectAll = false;
  dtTrigger: Subject<any> = new Subject();

  wrapperHide = true;
  expandedSrcSwitchName : boolean = false;
  expandedSrcSwitchPort : boolean = false;
  expandedSrcSwitchVlan : boolean = false;

  expandedTargetSwitchName : boolean = false;
  expandedTargetSwitchPort : boolean = false;
  expandedTargetSwitchVlan : boolean = false;

  expandedBandwidth: boolean = false;
  expandedFlowId : boolean = false;
  expandedState : boolean = false;
  expandedStatus : boolean = false;
  expandedDescription : boolean = false;
  expandedCreated : boolean = false;
  storeLinkSetting = false;
  loadFilter : boolean =  false;
  hasDownFLows :boolean = false;
  activeStatus :any = '';
  clipBoardItems = [];

  constructor(private loaderService:LoaderService, private renderer: Renderer2,private router: Router,
    public commonService: CommonService,    
    private flowService:FlowsService,
    private clipboardService: ClipboardService,    
    private modalService:NgbModal,
    private formBuilder: FormBuilder) {
    this.wrapperHide = false;
    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false;    
   }
  ngOnInit() {
    let ref= this;
    this.dtOptions = {
      pageLength: 10,
      deferRender: true,
      info:true,
      dom: 'tpli',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      "order": [],
      language: {
        searchPlaceholder: "Search"
      },
      buttons:{
        buttons:[
          { extend: 'csv', text: 'Export', className: 'btn btn-dark' }
        ]
      },
      drawCallback:function(){
        if(jQuery('#flowDataTable tbody tr').length < 10){
          jQuery('#flowDataTable_next').addClass('disabled');
        }else{
          jQuery('#flowDataTable_next').removeClass('disabled');
        }
      },
      "aoColumns": [
        { sWidth: '7%' ,"bSortable":false},
        { sWidth: '15%' },
        { sWidth:  '13%',"sType": "name","bSortable": true },
        { sWidth: '8%' },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '13%',"sType": "name","bSortable": true },
        { sWidth: '8%' },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },        
        { sWidth: '10%' },
        { sWidth: '1%' ,"bSortable": false},
        { sWidth: '10%' ,"bSortable": false},
       
       ],
       columnDefs:[
        {
          "targets": [ 0],
          "visible": ref.commonService.hasPermission('fw_permission_reroute'),
          "searchable": false
       },
        {
          "targets": [ 3],
          "visible": false,
          "searchable": true
      },
      {
          "targets": [ 7 ],
          "visible": false,
          "searchable": true
      },
      { "targets": [14], "visible": false},
      ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }
  }

  ngOnChanges(change:SimpleChanges){
    var ref = this;
    if( typeof(change.data)!='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
        this.data.forEach(function(d){
          if(d.status =='DOWN' || d.status == 'DEGRADED'){
            ref.hasDownFLows = true;
            ref.checkedFlow[d.flowid] = false;
          }
        });
        this.clipBoardItems = this.data;
      }
    }
    if(typeof(change.textSearch)!=='undefined' && change.textSearch.currentValue){
      this.fulltextSearch(change.textSearch.currentValue);
    }
    if(typeof(change.srcSwitch)!=='undefined' &&  change.srcSwitch.currentValue){
      this.expandedSrcSwitchName =  true;
    }else{
      this.expandedSrcSwitchName =  false;
    }
   if(typeof(change.dstSwitch)!='undefined' && change.dstSwitch.currentValue){
      this.expandedTargetSwitchName = true;
    }else{
      this.expandedTargetSwitchName = false;
    } 

    this.triggerSearch();
  }
  loadFilters() {
    this.loadFilter = ! this.loadFilter;
  }
 
  fulltextSearch(value:any){ 
    if(this.dtTrigger)
        this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
          dtInstance.search(value)
                  .draw();
        });
  }

  ngAfterViewInit(){
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () {
        const that = this;
        $('input[type="search"]', this.header()).on('keyup change', function () {
          if (that.search() !== this['value']) {
              that
              .search(this['value'])
              .draw();
          }
        });
      });
    });
  }

  toggleSearch(e,inputContainer){ 
    
    this[inputContainer] = this[inputContainer] ? false : true;
    if(this[inputContainer]){
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).focus();
      });
    }else{
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).value = "";
        jQuery('#'+inputContainer).trigger('change');
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

  showFlow(flowObj : Flow){
    localStorage.setItem("filterFlag",this.filterFlag);
    this.router.navigate(['/flows/details/'+flowObj.flowid]);
  }
 

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }
  
  descrepancyString(row){
    let text = [];
    if(row.hasOwnProperty('controller-flow')){
        if(row['controller-flow']){
          text.push("controller:true");
        }else{
          text.push("controller:false");
        }
    }else{
      text.push("controller:false");
    }

    if(row.hasOwnProperty('inventory-flow')){
      if(row['inventory-flow']){
        text.push("inventory:true");
      }else{
        text.push("inventory:false");
      }
    }else{
      text.push("inventory:false");
    }

    return text.join(", ");
  }


  copyToClip(event, copyItem,index) {
    var copyItem = this.clipBoardItems[index][copyItem];
    this.clipboardService.copyFromContent(copyItem);
  }

  /*    Re-routing down flows ***/

  loadFlowReRouteModal() {
    const modelRef = this.modalService.open(FlowReRouteModalComponent,{ size: 'lg',windowClass:'modal-isl slideInUp', backdrop: 'static',keyboard:false });
    modelRef.componentInstance.title = MessageObj.re_routing_flows;
    modelRef.componentInstance.reRouteIndex = this.reRouteFlowIndex;
    modelRef.componentInstance.responseData = this.reRouteList;
    modelRef.result.then(()=>{      
      this.refreshList();
    });
}

refreshList() {
  this.refresh.emit();
}

enableRerouteFlow(flag){
  this.enableReroute.emit({flag:flag});
}

selectAllFlows(e) {
  this.selectAll = !this.selectAll;
  if(this.checkedFlow && Object.keys(this.checkedFlow).length){
    Object.keys(this.checkedFlow).forEach((k,i)=>{ this.checkedFlow[k] = this.selectAll; });
  }
  this.enableRerouteFlow(this.selectAll);
}

toggleSelection(flow) {
  var re_routeFlag = false;
  this.checkedFlow[flow.flowid] = !this.checkedFlow[flow.flowid];
  if(this.checkedFlow && Object.keys(this.checkedFlow).length){
    var selectAll = true;
    Object.keys(this.checkedFlow).forEach((k,i)=>{
       if(!this.checkedFlow[k]){ 
          selectAll = false;
            return false;
         }else{
          re_routeFlag = true;
         }  
      });
    this.selectAll = selectAll;
  }  
  this.enableRerouteFlow(re_routeFlag);
  
}

reRouteFlows() {
  this.reRouteFlowIndex ={};
  let selectedFlows = [];
  Object.keys(this.checkedFlow).forEach((k,i)=>{
    if(this.checkedFlow[k]){
      selectedFlows.push(k);
    }
  });
  if(selectedFlows && selectedFlows.length){
    this.loadFlowReRouteModal();
    var flowID = selectedFlows.pop();    
    this.reRouteFlowIndex[flowID] = {type:'info'};
    this.reRouteFlow(flowID,selectedFlows);
  }  
}

reRouteFlow(flowID,flowList) {
  var self = this;
    if(flowID){
      this.reRouteList.push(flowID);
      this.reRouteFlowIndex[flowID]['progress'] = 10;
      this.reRouteFlowIndex[flowID]['interval'] = setInterval(() => {
        if(this.reRouteFlowIndex[flowID]['progress'] <= 90){
          this.reRouteFlowIndex[flowID]['progress'] =  this.reRouteFlowIndex[flowID]['progress'] + 10;
        }
      },300);
      this.flowService.getReRoutedPath(flowID).subscribe(function(data:any){
          if(data && typeof(data.rerouted)!=='undefined' && data.rerouted){
            clearInterval( self.reRouteFlowIndex[flowID]['interval']);
            self.reRouteFlowIndex[flowID]['type'] = 'success';
            self.reRouteFlowIndex[flowID]['progress'] = 100;
            self.reRouteFlowIndex[flowID]['message'] = MessageObj.flow_rerouted;
            } else {
              clearInterval(self.reRouteFlowIndex[flowID]['interval']);
              self.reRouteFlowIndex[flowID]['type'] = 'success';
              self.reRouteFlowIndex[flowID]['progress'] = 100;
              self.reRouteFlowIndex[flowID]['message'] = MessageObj.flow_on_best_route;
            }
            if(flowList && flowList.length){
              var flow_id = flowList.pop();
               self.reRouteFlowIndex[flow_id] = {type:'info'};
               self.reRouteFlow(flow_id,flowList);
            }else{
              return;
            }
            
          },function(error){
            clearInterval(self.reRouteFlowIndex[flowID]['interval']);
            self.reRouteFlowIndex[flowID]['type'] = 'danger';
            self.reRouteFlowIndex[flowID]['progress'] = 100;
            self.reRouteFlowIndex[flowID]['message'] = error.error["error-auxiliary-message"];
            self.reRouteFlowIndex[flowID]['description'] = error.error["error-description"];
            if(flowList && flowList.length){
              var flow_id = flowList.pop();
               self.reRouteFlowIndex[flow_id] = {type:'info'};
               self.reRouteFlow(flow_id,flowList);
            }else{
              return;
            }
         });
    }
 
}

  /** End re-routing down flows *** */

}
