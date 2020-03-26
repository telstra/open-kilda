import { Component, OnInit, ViewChild, OnDestroy, AfterViewInit, Input, OnChanges, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Router, ActivatedRoute } from '@angular/router';
declare var jQuery: any;

@Component({
  selector: 'app-switch-flows',
  templateUrl: './switch-flows.component.html',
  styleUrls: ['./switch-flows.component.css']
})
export class SwitchFlowsComponent implements OnDestroy, OnInit,OnChanges, AfterViewInit {

  @ViewChild(DataTableDirective)
  datatableElement: DataTableDirective;
  @Input() data;
  switchId:any;
  dtOptions: any = {};
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

  showFlow(flowObj){
    this.router.navigate(['/flows/details/'+flowObj.flowid]);
  }
  fulltextSearch(e:any){ 
    var value = e.target.value;
    console.log('value',value);
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
