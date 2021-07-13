import { Component, OnInit, ViewChild, OnDestroy, AfterViewInit, Input, OnChanges, SimpleChanges, Renderer2,Output,EventEmitter } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Router, ActivatedRoute } from '@angular/router';
import { FlowPingModalComponent } from 'src/app/common/components/flow-ping-modal/flow-ping-modal.component';
import { MessageObj } from 'src/app/common/constants/constants';
import { FlowsService } from 'src/app/common/services/flows.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ToastrService } from 'ngx-toastr';
declare var jQuery: any;

@Component({
  selector: 'app-switch-flows',
  templateUrl: './switch-flows.component.html',
  styleUrls: ['./switch-flows.component.css']
})
export class SwitchFlowsComponent implements OnDestroy, OnInit,OnChanges, AfterViewInit {

  @ViewChild(DataTableDirective, { static: true })
  datatableElement: DataTableDirective;
  @Input() data;
  @Output() refresh =  new EventEmitter();
  switchId:any;
  dtOptions: any = {};
  switchFlow = [];
  selectAll = false;
  pingFlowIndex = {};
  pingFlowList:any=[];
  selectedFlowList:any=[];
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = true;
  
  srcSwitch : string;
  dstSwitch : string;
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
  constructor(
              private renderer:Renderer2,
              private loaderService : LoaderService,
              private router: Router,
              private route: ActivatedRoute,
              private flowService:FlowsService,              
              private toaster:ToastrService,
              private modalService:NgbModal
              ) { }

  ngOnInit() {

    this.route.params.subscribe(params => {
      this.switchId= params['id'];
    });
    var ref = this;
    this.dtOptions = {
      pageLength: 10,
      deferRender: true,
      info:true,
      dom: 'tpli',
      order:[['1','desc']],
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      language: {
        searchPlaceholder: "Search"
      },
      buttons:{
        buttons:[
          { extend: 'csv', text: 'Export', className: 'btn btn-dark' },
        ]
      },
      drawCallback:function(){
        if(jQuery('#switchflowDataTable tbody tr').length < 10){
          jQuery('#switchflowDataTable_next').addClass('disabled');
        }else{
          jQuery('#switchflowDataTable_next').removeClass('disabled');
        }
      },
      "aoColumns": [
        { sWidth: '5%' ,"bSortable": false},
        { sWidth: '10%' },
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
        { sWidth: '1%' ,"bSortable": false},
       ],
       columnDefs:[
        {
          "targets": [ 3 ],
          "visible": false,
          "searchable": true
      },
      {
          "targets": [ 7 ],
          "visible": false,
          "searchable": true
      },
      { "targets": [13], "visible": false},
      ],
      initComplete:function( settings, json ){ 
        var timer = (ref.data && ref.data.length) ? ref.data.length/2:0;
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = false;
        },timer);
      }
    }
    
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

  

  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () { 
        const that = this;
        $('input[type="search"]', this.header()).on('keyup change', function () {
          if (that.search() !== this['value']) {
            that.search(this['value']).draw();
          }
        });
      });
    });
    this.enableButtons();
  }

  rerender(): void {
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.destroy();
      this.dtTrigger.next();
    });
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

  ngOnChanges(change: SimpleChanges){
    var ref = this;
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
        if(this.data && this.data.length){
          this.data.forEach(function(d){
            ref.switchFlow[d.flowid] = false;
          })
        }
      }
    }
  }

  toggleSearch(e, inputContainer) {
    event.stopPropagation();
    this[inputContainer] = this[inputContainer] ? false : true;
    if (this[inputContainer]) {
      setTimeout(() => {
        this.renderer.selectRootElement("#" + inputContainer).focus();
      });
    }else{
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).value = "";
        jQuery('#'+inputContainer).trigger('change');
      });
    }
  }

  stopPropagationmethod(e) {
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
    }
  }


  selectAllFlows(e) {
    this.selectAll = !this.selectAll;
    if(this.switchFlow && Object.keys(this.switchFlow).length){
      Object.keys(this.switchFlow).forEach((k,i)=>{ this.switchFlow[k] = this.selectAll; });
    }
  }
  
  toggleSelection(flow) {
    this.switchFlow[flow.flowid] = !this.switchFlow[flow.flowid];
    if(this.switchFlow && Object.keys(this.switchFlow).length){
      var selectAll = true;
      Object.keys(this.switchFlow).forEach((k,i)=>{
         if(!this.switchFlow[k]){ 
            selectAll = false;
              return false;
           }  
        });
      this.selectAll = selectAll;
    }
  }
  ifSelectedFlows(){
    let selectedFlows = [];
    Object.keys(this.switchFlow).forEach((k,i)=>{
      if(this.switchFlow[k]){
        selectedFlows.push(k);
      }
    });
    var flag = (selectedFlows && selectedFlows.length == 0);
    return flag;
  }
  pingFlows() {
    this.pingFlowIndex ={};
    let selectedFlows = [];
    Object.keys(this.switchFlow).forEach((k,i)=>{
      if(this.switchFlow[k]){
        selectedFlows.push(k);
      }
    })
    if(selectedFlows && selectedFlows.length){
      this.selectedFlowList = selectedFlows;
      this.loadFlowPingModal();
      var flowID = selectedFlows.pop();    
      this.pingFlowIndex[flowID] = {type:'info'};
      this.pingFlow(flowID,selectedFlows);
    }
    
  }
  pingFlow(flowID,flowList) {
    var self = this;
      if(flowID){
        this.pingFlowList.push(flowID);
        this.pingFlowIndex[flowID]['progress'] = 10;
        this.pingFlowIndex[flowID]['forwardprogress'] = 10;
        self.pingFlowIndex[flowID]['reverseprogress'] = 10;
        this.pingFlowIndex[flowID]['interval'] = setInterval(() => {
          if(this.pingFlowIndex[flowID]['forwardprogress'] <= 90){
            this.pingFlowIndex[flowID]['forwardprogress'] =  this.pingFlowIndex[flowID]['forwardprogress'] + 10;
          }
          if(this.pingFlowIndex[flowID]['reverseprogress'] <= 90){
            this.pingFlowIndex[flowID]['reverseprogress'] =  this.pingFlowIndex[flowID]['reverseprogress'] + 10;
          }
          if(this.pingFlowIndex[flowID]['progress'] <= 90){
            this.pingFlowIndex[flowID]['progress'] =  this.pingFlowIndex[flowID]['progress'] + 10;
          }
        },300);
        this.flowService.pingFlow(flowID).subscribe(function(data:any){
            clearInterval( self.pingFlowIndex[flowID]['interval']);
            var forward_ping = (data && data['forward'] && data['forward']['ping_success']) ?data['forward']['ping_success'] : false;
            var reverse_ping = (data && data['reverse'] && data['reverse']['ping_success']) ?data['reverse']['ping_success'] : false;
              if(forward_ping){
                self.pingFlowIndex[flowID]['forwardtype'] = 'success';
                self.pingFlowIndex[flowID]['forwardprogress'] = 100;
                self.pingFlowIndex[flowID]['forwardmessage'] = "Forward Ping Successful"
              }else{
                self.pingFlowIndex[flowID]['forwardtype'] = 'danger';
                self.pingFlowIndex[flowID]['forwardprogress'] = 100;
                self.pingFlowIndex[flowID]['forwardmessage'] = "Forward Ping Failed";
              }
              if(reverse_ping){
                self.pingFlowIndex[flowID]['reversetype'] = 'success';
                self.pingFlowIndex[flowID]['reverseprogress'] = 100;
                self.pingFlowIndex[flowID]['reversemessage'] = "Reverse Ping Successfull";
              }else{
                self.pingFlowIndex[flowID]['reversetype'] = 'danger';
                self.pingFlowIndex[flowID]['reverseprogress'] = 100;
                self.pingFlowIndex[flowID]['reversemessage'] = "Reverse Ping Failed";
              }
              if(flowList && flowList.length){
                var flow_id = flowList.pop();
                 self.pingFlowIndex[flow_id] = {type:'info'};
                 self.pingFlow(flow_id,flowList);
              }else{
                return;
              }
              
            },function(error){
              clearInterval(self.pingFlowIndex[flowID]['interval']);
              self.pingFlowIndex[flowID]['error'] = true;
              self.pingFlowIndex[flowID]['type'] = 'danger';
              self.pingFlowIndex[flowID]['progress'] = 100;
              self.pingFlowIndex[flowID]['message'] = error.error["error-auxiliary-message"];
              self.pingFlowIndex[flowID]['description'] = error.error["error-description"];
              if(flowList && flowList.length){
                var flow_id = flowList.pop();
                 self.pingFlowIndex[flow_id] = {type:'info'};
                 self.pingFlow(flow_id,flowList);
              }else{
                return;
              }
           });
      }
   
  }
  loadFlowPingModal() {
    const modelRef = this.modalService.open(FlowPingModalComponent,{ size: 'lg', windowClass:'modal-isl slideInUp', backdrop: 'static',keyboard:false });
    modelRef.componentInstance.title = MessageObj.ping_flows;
    modelRef.componentInstance.pingFlowIndex = this.pingFlowIndex;
    modelRef.componentInstance.selectedFlowList = this.selectedFlowList;
    modelRef.componentInstance.responseData = this.pingFlowList;
    modelRef.result.then(()=>{
      this.refreshList();
    });
}

refreshList(){
  this.refresh.emit();
}

  showFlow(flowObj){
    this.router.navigate(['/flows/details/'+flowObj.flowid]);
  }
  fulltextSearch(e:any){ 
    var value = e.target.value;
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        dtInstance.search(value)
                .draw();
      });
  }

  enableButtons(){
    setTimeout(()=>{
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        var buttons = new jQuery.fn.dataTable.Buttons(dtInstance, {
          buttons: [
            { extend: 'csv', text: 'Export', className: 'btn btn-dark' }
          ]
        }).container().appendTo($('#buttons'));
      });
    });
    
  }

}
