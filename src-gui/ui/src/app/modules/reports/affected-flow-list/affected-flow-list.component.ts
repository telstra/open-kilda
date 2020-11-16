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
  selector: 'app-affected-flow-list',
  templateUrl: './affected-flow-list.component.html',
  styleUrls: ['./affected-flow-list.component.css']
})
export class AffectedFlowListComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  @Input() data = [];
  @Input() textSearch:any;
  @Output() refresh =  new EventEmitter();
  

  typeFilter:string = '';
  dtOptions = {};
  dtTrigger: Subject<any> = new Subject();

  wrapperHide = true;
  expandedSrcSwitchName : boolean = false;
  expandedTargetSwitchName : boolean = false;
  expandedReason : boolean = false;

  expandedFlowId : boolean = false;
  expandedStatus : boolean = false;
  loadFilter : boolean =  false;
  activeStatus :any = '';
  clipBoardItems = [];

  constructor(private loaderService:LoaderService, private renderer: Renderer2,private router: Router,
    public commonService: CommonService,    
    private flowService:FlowsService,
    private clipboardService: ClipboardService,    
    private modalService:NgbModal,
    private formBuilder: FormBuilder) {
    this.wrapperHide = false; 
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
          { extend: 'csv', text: 'Export', filename:"Affected_Flows",className: 'btn btn-dark',exportOptions:{columns: ':visible'} }
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
        { sWidth: '10%'},
        { sWidth: '10%',"sType": "name","bSortable": true },
        { sWidth: '10%'},
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '40%',"sType": "name","bSortable": true }, 
       ],
       columnDefs:[
        {
          "targets": [ 2],
          "visible": false,
          "searchable": true
      },
      {
          "targets": [4 ],
          "visible": false,
          "searchable": true
      },
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
        this.clipBoardItems = this.data;
      }
    }
    if(typeof(change.textSearch)!=='undefined' && change.textSearch.currentValue){
      this.fulltextSearch(change.textSearch.currentValue);
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
    this.enableButtons();
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
    localStorage.setItem("filterFlag",'controller');
    this.router.navigate(['/flows/details/'+flowObj.flowid]);
  }
 

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }
  
  copyToClip(event, copyItem,index) {
    var copyItem = this.clipBoardItems[index][copyItem];
    this.clipboardService.copyFromContent(copyItem);
  }

  enableButtons(){
    setTimeout(()=>{
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        var buttons = new jQuery.fn.dataTable.Buttons(dtInstance, {
          buttons: [
            { extend: 'csv', filename:"Affected_Flows", text: 'Export', className: 'btn btn-dark' ,exportOptions:{columns: ':visible'} }
          ]
        }).container().appendTo($('#buttons'));
      });
    });
    
  }
}
