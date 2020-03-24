import { Component, OnInit, Input, ViewChild, OnChanges, SimpleChange,EventEmitter, SimpleChanges, Renderer2, AfterViewInit, OnDestroy, Output } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Subject } from 'rxjs';
import { Flow } from 'src/app/common/data-models/flow';
import { Router } from '@angular/router';
import { CommonService } from 'src/app/common/services/common.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ClipboardService } from 'ngx-clipboard';
declare var jQuery: any;

@Component({
  selector: 'app-flow-datatables',
  templateUrl: './flow-datatables.component.html',
  styleUrls: ['./flow-datatables.component.css']
})
export class FlowDatatablesComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  @Input() data = [];
  @Input() srcSwitch : string;
  @Input() dstSwitch : string;
  @Input() filterFlag:string;
  @Input() textSearch:any;

  typeFilter:string = '';
  dtOptions = {};
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
  storeLinkSetting = false;
  loadFilter : boolean =  false;
  activeStatus :any = '';
  clipBoardItems = [];

  constructor(private loaderService:LoaderService, private renderer: Renderer2,private router: Router,
    public commonService: CommonService,
    private clipboardService: ClipboardService,
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
      language: {
        searchPlaceholder: "Search"
      },
      buttons:{
        buttons:[
          { extend: 'csv', text: 'Export', className: 'btn btn-dark' }
        ]
      },
      "aoColumns": [
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
        { sWidth: '1%' ,"bSortable": false},
        { sWidth: '10%' ,"bSortable": false},
       
       ],
       columnDefs:[
        {
          "targets": [ 2 ],
          "visible": false,
          "searchable": true
      },
      {
          "targets": [ 6 ],
          "visible": false,
          "searchable": true
      },
      { "targets": [12], "visible": false},
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
    if( typeof(change.data)!='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
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
        $('input', this.header()).on('keyup change', function () {
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

}
