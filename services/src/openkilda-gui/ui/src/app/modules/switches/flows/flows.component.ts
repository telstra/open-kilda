import { Component, OnInit, ViewChild, OnDestroy, AfterViewInit, Input, OnChanges, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { SwitchService } from 'src/app/common/services/switch.service';
declare var jQuery: any;

@Component({
  selector: 'app-flows',
  templateUrl: './flows.component.html',
  styleUrls: ['./flows.component.css']
})
export class FlowsComponent implements OnDestroy, OnInit,OnChanges, AfterViewInit {
  @ViewChild(DataTableDirective)
  datatableElement: DataTableDirective;
  @Input() switchid;
  @Input() portnumber;
  data = [];
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = false;
  customername : boolean = false;
  customerid : boolean = false;
  flowid : boolean = false;
  bandwidth : boolean = false;

  constructor(private renderer:Renderer2,private loaderService : LoaderService,private switchService:SwitchService) { }

  ngOnInit() {
    this.loaderService.show('Fetching flows');
    let ref= this;
    this.loadPortFlows();
    this.dtOptions = {
      paging: true,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      dom: 'tpl',
      "aoColumns": [
        { sWidth: '25%' },
        { sWidth: '40%' },
        { sWidth: '20%' },
        { sWidth: '15%' },
      ],
      initComplete:function( settings, json ){
        
      },
      language: {
        searchPlaceholder: "Search"
      },
      buttons:{
        buttons:[
          { extend: 'csv', text: 'Export', className: 'btn btn-dark' }
        ]
      }
      //dom: 'lBrtip',
      
    }
  }

  loadPortFlows(){
    var ref = this;
    let switchId = this.switchid;
    let portNumber = this.portnumber
    this.switchService.getSwitchPortFlows(switchId,portNumber).subscribe((flows:any)=>{
      let flowList =  flows || [];
      let newFlowList = [];
      flowList.forEach(customer => {
          if(customer.flows){
            customer.flows.forEach(flow => {
              newFlowList.push({
                "flow-id":flow['flow-id'],
                "customer-uuid":customer['customer-uuid'] || '-',
                "company-name":customer['company-name'] || '-',
                "bandwidth":flow['bandwidth']
              })
            });
          }
      });
      this.data = newFlowList;
      this.loaderService.hide();
     
      setTimeout(function(){
        ref.wrapperHide = true;
      },this.data.length/2);
    });
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
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
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