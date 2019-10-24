import { Component, OnInit, ViewChild, OnDestroy, AfterViewInit, Input, OnChanges, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { SwitchService } from 'src/app/common/services/switch.service';
import { CommonService } from 'src/app/common/services/common.service';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
declare var jQuery: any;

@Component({
  selector: 'app-port-inventory-flows',
  templateUrl: './port-inventory-flows.component.html',
  styleUrls: ['./port-inventory-flows.component.css']
})
export class PortInventoryFlowsComponent implements  OnDestroy, OnInit,OnChanges, AfterViewInit {
  @ViewChild(DataTableDirective)
  datatableElement: DataTableDirective;
  @Input() data;  
  @Input() textSearch:any;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = false;  
  customername : boolean = false;
  customerid : boolean = false;
  flowid : boolean = false;
  bandwidth : boolean = false;
  constructor(private renderer:Renderer2,private loaderService:LoaderService,private switchService:SwitchService,public commonService:CommonService,private router: Router) { }

  
  ngOnInit() { 
    var ref = this;
    this.dtOptions = {
    pageLength: 10,
    deferRender: true,
    info:true,
    dom: 'tpl',
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
        { extend: 'csv', text: 'Export', className: 'btn btn-dark',exportOptions:{columns: ':visible'} }
      ]
    },
    "aoColumns": [
      { sWidth: '15%' },
      { sWidth:  '15%',"sType": "name","bSortable": true },
      { sWidth: '15%' },
      { sWidth: '15%' },
      ],
    columnDefs:[

    ],
    initComplete:function( settings, json ){ 
      setTimeout(function(){
        ref.loaderService.hide();
        ref.wrapperHide = true;
      },this.data.length/2);
    }
  }
}


  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () {
        const that = this;
        $('input', this.header()).on('keyup change', function () {
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
    if(typeof(change.data)!=='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }
    if(typeof(change.textSearch)!=='undefined' && change.textSearch.currentValue){
      this.fulltextSearch(change.textSearch.currentValue);
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

  fulltextSearch(value:any){ 
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
       dtInstance.search(value)
               .draw();
     });
 }
 showFlow(flowObj){
  this.router.navigate(['/flows/details/'+flowObj['flow-id']]);
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
  enableButtons(){
    setTimeout(()=>{
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        var buttons = new jQuery.fn.dataTable.Buttons(dtInstance, {
          buttons: [
            { extend: 'csv', text: 'Export', className: 'btn btn-dark' ,exportOptions:{columns: ':visible'} }
          ]
        }).container().appendTo($('#buttons'));
      });
    });
    
  }

}
