import { Component, OnInit ,ViewChild,Input,OnChanges,Output,EventEmitter, Renderer2, SimpleChanges} from '@angular/core';
import { SamlSettingService } from 'src/app/common/services/saml-setting.service';
import { LoaderService } from 'src/app/common/services/loader.service';
import {MessageObj} from '../../../common/constants/constants';
import { ToastrService } from 'ngx-toastr';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
@Component({
  selector: 'app-saml-list-table',
  templateUrl: './saml-list-table.component.html',
  styleUrls: ['./saml-list-table.component.css']
})
export class SamlListTableComponent implements OnInit , OnChanges{
  
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  @Input() data=[];
  @Output() editsaml = new EventEmitter();
  @Output() deletesaml = new EventEmitter();
  dtOptions = {};  
  wrapperHide=true;
  dtTrigger: Subject<any> = new Subject();
  constructor(
    private samlSettingService:SamlSettingService,
    private loaderService:LoaderService,
    private renderer:Renderer2
  ) { 
    this.wrapperHide=false;
  }

  ngOnInit() {
    var ref = this;
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
      drawCallback:function(){
        if(jQuery('#flowDataTable tbody tr').length < 10){
          jQuery('#flowDataTable_next').addClass('disabled');
        }else{
          jQuery('#flowDataTable_next').removeClass('disabled');
        }
      },
      "aoColumns": [
        { sWidth: '7%' ,"sType": "name","bSortable": true},
        { sWidth: '15%' },
        { sWidth: '13%' },
        { sWidth: '10%' ,"bSortable": false},
       
       ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }
  }

  editProvider(row){
    this.editsaml.emit(row);
  }

  deleteProvider(row){
    this.deletesaml.emit(row);
  }  

  ngOnChanges(change:SimpleChanges){
    var ref = this;
    if( typeof(change.data)!='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
        this.dtTrigger.next();
      }
    }
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

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

}
