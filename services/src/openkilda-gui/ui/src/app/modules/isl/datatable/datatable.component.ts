import {
  AfterViewInit,
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  Input,
  Renderer2,
  OnChanges,
  SimpleChanges
} from "@angular/core";
import { IslModel } from "../../../common/data-models/isl-model";
import { IslDetailModel } from "../../../common/data-models/isl-detail-model";
import { IslListService } from "../../../common/services/isl-list.service";
import { Router } from "@angular/router";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { Subject } from "rxjs";
import { DataTableDirective } from "angular-datatables";
import { ToastrService } from "ngx-toastr";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Renderer3 } from "@angular/core/src/render3/interfaces/renderer";

@Component({
  selector: "app-datatable",
  templateUrl: "./datatable.component.html",
  styleUrls: ["./datatable.component.css"]
})
export class DatatableComponent implements OnDestroy, OnInit, AfterViewInit, OnChanges {
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();

  @Input() data = [];

  listUrl: string = "";
  listDataObservable= [];

  loadingData = 0;
  wrapperHide = true;
  loadCount = 0;

  expandedSrcSwitchName : boolean = false;
  expandedSrcPort : boolean = false;
  expandedCost : boolean = false;
  expandedDestinationSwitchName : boolean = false;
  expandedDestinationPort : boolean = false;
  expandedState : boolean = false;
  expandedAvailableSpeed: boolean = false;
  expandedSpeed: boolean = false;
  expandedAvailableBandwidth : boolean = false;
  expandedLatency : boolean = false;
  expandedUnidirectional : boolean = false;

  showIslDetail = function(data) {
    var flowData = {
      source_switch: "",
      src_port: "",
      source_switch_name: "",
      target_switch: "",
      dst_port: "",
      target_switch_name: "",
      available_bandwidth: "",
      speed: "",
      state: "",
      latency: "",
      unidirectional: "",
      cost: 0
    };

    flowData.source_switch_name = data.source_switch_name;
    flowData.source_switch = data.source_switch;

    flowData.src_port = data.src_port;
    flowData.target_switch_name = data.target_switch_name;
    flowData.target_switch = data.target_switch;
    flowData.dst_port = data.dst_port;
    flowData.state = data.state;
    flowData.speed = data.speed;
    flowData.available_bandwidth = data.available_bandwidth;
    flowData.latency = this.checkValue(data.latency);
    flowData.unidirectional = data.unidirectional;

    localStorage.setItem("linkData", JSON.stringify(flowData));
    this.router.navigate(["/isl/switch/isl"]);
  };

  constructor(
    private router: Router,
    private httpClient: HttpClient,
    private islListService: IslListService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private renderer: Renderer2
  ) {
    this.wrapperHide = false;
  }



  getPercentage(val, baseVal) {
    if (
      val !== "" &&
      val != undefined &&
      baseVal !== "" &&
      baseVal != undefined
    ) {
      let percentage: any = (val / baseVal) * 100;
      let percentage_fixed: any = percentage.toFixed(2);
      let value_percentage: any = percentage_fixed.split(".");
      if (value_percentage[1] > 0) {
        return percentage.toFixed(2);
      } else {
        return value_percentage[0];
      }
    } else {
      return "-";
    }
  }

  checkValue(value) {
    if (value === "" || value == undefined) {
      return "-";
    } else {
      return value;
    }
  }

  checkValueInt(value) {
    if (value === "" || value == undefined) {
      return "-";
    } else {
      return value / 1000;
    }
  }

  ngOnInit() {
    let ref = this;
    this.dtOptions = {
      pageLength: 10,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      language: {
        searchPlaceholder: "Search"
      },"aoColumns": [
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
        { sWidth: '12%' },
        { sWidth: '8%' },
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
        },
        {
            "targets":  [9],
            "type": "num-fmt" 
        }
      ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    };
  }

  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function() {
        const that = this;
        $("input", this.header()).on("keyup change", function() {
          if (that.search() !== this["value"]) {
            that.search(this["value"]).draw();
          }
        });
      });
    });
  }

  ngOnChanges(change:SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

  toggleSearch(e, inputContainer) {
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

  stopPropagationmethod(e) {
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
    }
  }
}
