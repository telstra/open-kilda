import { Component,ViewChild,Input, OnInit , AfterViewInit, OnDestroy,Renderer2 ,Output,EventEmitter } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { LoaderService } from 'src/app/common/services/loader.service';
import { FlowsService } from 'src/app/common/services/flows.service';
import { ToastrService } from 'ngx-toastr';
import { FlowReRouteModalComponent } from 'src/app/common/components/flow-re-route-modal/flow-re-route-modal.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-isl-flow-datatables',
  templateUrl: './isl-flow-datatables.component.html',
  styleUrls: ['./isl-flow-datatables.component.css']
})
export class IslFlowDatatablesComponent implements OnInit , AfterViewInit , OnDestroy  {
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  @Input() data = [];
  @Input() srcSwitch : string;
  @Input() dstSwitch : string;
  @Output() refresh =  new EventEmitter();
  dtOptions = {};
  reRouteFlowIndex = {};
  islFlow = [];
  selectAll = false;
  reRouteList:any=[];
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = true;
  expandedFlowId : boolean = false;
  expandedSrcSwitchPort : boolean = false;
  expandedSrcSwitchVlan : boolean = false;
  expandedTargetSwitchPort : boolean = false;
  expandedTargetSwitchVlan : boolean = false;
  expandedBandwidth : boolean = false;
  expandedState: boolean = false;
  expandedDescription: boolean = false;
  constructor(private loaderService:LoaderService,
              private renderer: Renderer2, 
              private flowService:FlowsService,
              private toaster:ToastrService,
              private modalService:NgbModal,
              ) { }

  ngOnInit() {
    let ref = this;
    if(this.data && this.data.length){
      this.data.forEach(function(d){
        ref.islFlow[d.flowid] = false;
      })
    }
    
    this.dtOptions = {
      pageLength: -1,
      deferRender: true,
      dom: 't',
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      order:[['1','desc']],
      "aoColumns": [
        { sWidth: '5%' ,"bSortable":false},
        { sWidth: '15%',"sType": "name","bSortable": true },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' } ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }
  }

fulltextSearch(e:any) { 
    var value = e.target.value;
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        dtInstance.search(value)
                .draw();
      });
}

refreshList() {
   this.refresh.emit();
}

toggleSearch(e,inputContainer) {  
  console.log('herein serch');
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


selectAllFlows(e) {
  this.selectAll = !this.selectAll;
  if(this.islFlow && Object.keys(this.islFlow).length){
    Object.keys(this.islFlow).forEach((k,i)=>{ this.islFlow[k] = this.selectAll; });
  }
}

toggleSelection(flow) {
  this.islFlow[flow.flowid] = !this.islFlow[flow.flowid];
  if(this.islFlow && Object.keys(this.islFlow).length){
    var selectAll = true;
    Object.keys(this.islFlow).forEach((k,i)=>{
       if(!this.islFlow[k]){ 
          selectAll = false;
            return false;
         }  
      });
    this.selectAll = selectAll;
  }
}

reRouteFlows() {
  this.reRouteFlowIndex ={};
  let selectedFlows = [];
  Object.keys(this.islFlow).forEach((k,i)=>{
    if(this.islFlow[k]){
      selectedFlows.push(k);
    }
  })
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
              self.reRouteFlowIndex[flowID]['type'] = 'info';
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

loadFlowReRouteModal() {
      const modelRef = this.modalService.open(FlowReRouteModalComponent,{ size: 'lg', windowClass:'modal-isl slideInUp', backdrop: 'static',keyboard:false });
      modelRef.componentInstance.title = MessageObj.re_routing_flows;
      modelRef.componentInstance.reRouteIndex = this.reRouteFlowIndex;
      modelRef.componentInstance.responseData = this.reRouteList;
      modelRef.result.then(()=>{
        this.refreshList();
      });
}

ngAfterViewInit() {
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
ngOnDestroy(): void {
  this.dtTrigger.unsubscribe();
}

stopPropagationmethod(e) {
  event.stopPropagation();

  if (e.key === "Enter") {
    return false;
  }
}

}
