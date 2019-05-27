import {
  AfterViewInit,
  Component,
  OnInit,
  OnDestroy,
  ViewChild,
  Input,Output,
  Renderer2,
  OnChanges,
  SimpleChanges,
  EventEmitter
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
import { ClipboardService } from "ngx-clipboard";

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
  @Output() refresh =  new EventEmitter();
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
  expandedMaxBandwidth: boolean = false;
  expandedAvailableBandwidth : boolean = false;
  expandedLatency : boolean = false;
  expandedUnidirectional : boolean = false;
  clipBoardItems = [];
  showIslDetail = function(data) {
     this.router.navigate(["/isl/switch/isl/"+data.source_switch+"/"+data.src_port+"/"+data.target_switch+"/"+data.dst_port]);
  };

  constructor(
    private router: Router,
    private httpClient: HttpClient,
    private islListService: IslListService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private renderer: Renderer2,
    private clipboardService: ClipboardService
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

  refreshList(){
    this.refresh.emit();
  }
  fulltextSearch(e:any){ 
      var value = e.target.value;
        this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
          dtInstance.search(value)
                  .draw();
        });
  }

  ngOnInit() {
    let ref = this;
    this.dtOptions = {
      pageLength: 10,
      retrieve: true,
      autoWidth: false,
      colResize: false,      
      dom: 'tpli',
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
        this.clipBoardItems = this.data;
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

  copyToClip(event, copyItem,index) {
     var copyData = this.checkValue(this.clipBoardItems[index][copyItem]);
    this.clipboardService.copyFromContent(copyData);
  }

}
