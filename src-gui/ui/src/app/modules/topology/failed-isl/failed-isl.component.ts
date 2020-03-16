import { Component, OnInit, AfterViewInit, OnDestroy, ViewChild, Renderer2 } from '@angular/core';
import { SwitchService } from "../../../common/services/switch.service";
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { TopologyService } from 'src/app/common/services/topology.service';

@Component({
  selector: 'app-failed-isl',
  templateUrl: './failed-isl.component.html',
  styleUrls: ['./failed-isl.component.css']
})
export class FailedIslComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DataTableDirective)
  datatableElement: DataTableDirective;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  failedISL : any;
  
  
  expandedSrcSwitchName : boolean = false;
  expandedSrcPort : boolean = false;
  expandedDestinationSwitchName : boolean = false;
  expandedDestinationPort : boolean = false;
  expandedCost : boolean = false;
  expandedState : boolean = false;
  expandedSpeed : boolean = false;
  expandedAvailableBandwidth : boolean = false;
  expandedLatency : boolean = false;



  constructor(private switchService: SwitchService, private renderer:Renderer2,private topologyService: TopologyService) { 
    this.failedISL = topologyService.getFailedIsls();
  }

  ngOnInit() {
  	  this.dtOptions = {
      "iDisplayLength": 8,
      "bLengthChange": false,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      dom: 'tpl',
      language: {
        searchPlaceholder: "Search"
      },
      "aoColumns": [
              { sWidth: '14%',"sType": "name","bSortable": true },
              { sWidth:  '8%' },
              { sWidth: '8%' },
              { sWidth: '14%' },
              { sWidth: '8%' },
              { sWidth: '8%' },
              { sWidth: '7%' },
              { sWidth: '12%' },
              { sWidth: '12%' },
              { sWidth: '12%' },
              { sWidth: '8%' }
      ],
      "columnDefs": [
            {
                "targets": [ 1 ],
                "visible": false,
                "searchable": true
            },
            {
                "targets": [ 4 ],
                "visible": false,
                "searchable": true
            }
      ] 
      
    };
  }

  ngAfterViewInit(): void {
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
 rerender(): void {
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.destroy();
      try{ this.dtTrigger.next();  }catch(err){  }
  
    });
  }

  ngOnDestroy(): void {
     this.dtTrigger.unsubscribe();
  }

   toggleSearch(e,inputContainer){ 
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
    event.stopPropagation();
  }

  stopPropagationmethod(e){
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
    }
  }

}
