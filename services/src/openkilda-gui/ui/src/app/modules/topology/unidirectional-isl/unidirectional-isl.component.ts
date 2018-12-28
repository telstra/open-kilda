import { Component, OnInit, AfterViewInit, OnDestroy, ViewChild, Renderer2, Input } from '@angular/core';
import { SwitchService } from "../../../common/services/switch.service";
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { TopologyService } from 'src/app/common/services/topology.service';

@Component({
  selector: 'app-unidirectional-isl',
  templateUrl: './unidirectional-isl.component.html',
  styleUrls: ['./unidirectional-isl.component.css']
})
export class UnidirectionalIslComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DataTableDirective)
  datatableElement: DataTableDirective;
  dtOptions:any =  {};
  dtTrigger: Subject<any> = new Subject();
  unidirectionalISL : any;

  uexpandedSrcSwitchName: boolean = false;
  uexpandedSrcPort: boolean = false;
  uexpandedDestinationSwitchName: boolean = false;
  uexpandedDestinationPort: boolean = false;
  uexpandedCost: boolean = false;
  uexpandedState: boolean = false;
  uexpandedSpeed: boolean = false;
  uexpandedAvailableBandwidth: boolean = false;
  uexpandedLatency: boolean = false;

  constructor(private switchService: SwitchService,private renderer:Renderer2,private topologyService:TopologyService) { 
    this.unidirectionalISL = topologyService.getUnidirectionalIsl();
  }

  ngOnInit() {
  	  this.dtOptions = {
      "iDisplayLength": 8,
      "bLengthChange": false,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      lengthMenu: false,
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
    }
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

  toggleuSearch(e,inputContainer){ 
    this[inputContainer] = this[inputContainer] ? false : true;
    if (this[inputContainer]){
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
