import { Component, OnInit, Input, ViewChild, OnChanges, SimpleChange,EventEmitter, SimpleChanges, Renderer2, AfterViewInit, OnDestroy, Output } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Subject } from 'rxjs';
import { Flow } from 'src/app/common/data-models/flow';
import { Router } from '@angular/router';
import { CommonService } from 'src/app/common/services/common.service';
import { FormBuilder, FormGroup } from '@angular/forms';
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
  @Output() refresh =  new EventEmitter();
  @Input()  statusParams:any;
  @Input() statusList:any;
  
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
  filterForm : FormGroup;

  constructor(private loaderService:LoaderService, private renderer: Renderer2,private router: Router,
    private commonService: CommonService,
    private formBuilder: FormBuilder) {
    this.wrapperHide = false;
    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false;    
   }
  ngOnInit() {
   this.activeStatus = this.statusParams.join(",");
    this.filterForm = this.formBuilder.group({ status: (this.statusParams && this.statusParams.length > 0) ? this.statusParams[this.statusParams.length-1]: 'active'});
    let ref= this;
    this.dtOptions = {
      pageLength: 10,
      deferRender: true,
      dom: 'tpl',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      language: {
        searchPlaceholder: "Search"
      },
      "aoColumns": [
        { sWidth: '15%' },
        { sWidth:  '13%',"sType": "name","bSortable": true },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '13%',"sType": "name","bSortable": true },
        { sWidth: '8%' },
        { sWidth: '9%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },{ sWidth: '10%' ,"bSortable": false} ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }
  }

  ngOnChanges(change:SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }

    if(change.srcSwitch.currentValue){
      this.expandedSrcSwitchName =  true;
    }else{
      this.expandedSrcSwitchName =  false;
    }
   if(change.dstSwitch.currentValue){
      this.expandedTargetSwitchName = true;
    }else{
      this.expandedTargetSwitchName = false;
    } 

    this.triggerSearch();

  }
  loadFilters() {
    this.loadFilter = ! this.loadFilter;
  }
  refreshList(status){
    let statusParam = [];
    statusParam.push(status);
    this.refresh.emit({statusParam:statusParam});
  }
  fulltextSearch(e:any){ 
      var value = e.target.value;
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
    this.router.navigate(['/flows/details/'+flowObj.flowid]);
  }
 

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

}
