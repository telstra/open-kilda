import { Component, OnInit, Input,ViewChild, Output, EventEmitter, OnChanges, OnDestroy, AfterViewInit, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Router } from '@angular/router';
import { Switch } from 'src/app/common/data-models/switch';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';
import { ClipboardService } from 'ngx-clipboard';

@Component({
  selector: 'app-switch-datatable',
  templateUrl: './switch-datatable.component.html',
  styleUrls: ['./switch-datatable.component.css']
})
export class SwitchDatatableComponent implements OnInit, OnChanges,OnDestroy,AfterViewInit {

  @ViewChild(DataTableDirective)  datatableElement: DataTableDirective;
  @Input() data =[];
  @Input() switchFilterFlag:string;  
  @Input() textSearch:any;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = false;
  hasStoreSetting =false;

  switch_id : boolean = false;
  commonname : boolean = false;
  name : boolean = false;
  address : boolean = false;
  hostname : boolean = false;
  poplocation : boolean = false;
  description : boolean = false;
  state : boolean = false;
  clipBoardItems = [];
  constructor(private loaderService : LoaderService,
    private renderer: Renderer2, 
    private router:Router,
    private storeSwitchService: StoreSettingtService,
    private clipboardService:ClipboardService
  ) { }

  ngOnInit() {
    this.wrapperHide = false;
    let ref = this;
    this.dtOptions = {
      pageLength: 10,
      retrieve: true,
      autoWidth: false,
      dom: 'tpli',
      colResize:false,
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      "responsive": true,
      "aoColumns": [
        { sWidth: '10%' },
        { sWidth: '10%',"sType": "name","bSortable": true },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '30%' },
        { sWidth: '10%' }],
      language: {
        searchPlaceholder: "Search"
      },
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },this.data.length/2);
      },
      columnDefs:[
        { targets: [4], visible: false},
        { targets: [7], visible: false},
      ]
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
    this.checkSwitchSettings();
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

  fulltextSearch(value:any){ 
    if(this.dtTrigger)
        this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
          dtInstance.search(value)
                  .draw();
        });
  }

  ngOnChanges(change:SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
        this.clipBoardItems = this.data;
      }
    }
    if(typeof(change.textSearch)!=='undefined' && change.textSearch.currentValue){
      this.fulltextSearch(change.textSearch.currentValue);
    }
  }

  showSwitch(switchObj: Switch) {
    var switchDetailsJSON = {
      "switch_id": switchObj.switch_id,
      "name": switchObj.name,
      "common-name":switchObj['common-name'],
      "address": switchObj.address,
      "hostname": switchObj.hostname,
      "description": switchObj.description,
      "state": switchObj.state
    };

    localStorage.setItem(
      "switchDetailsJSON",
      JSON.stringify(switchDetailsJSON)
    );
    localStorage.setItem("switchFilterFlag",this.switchFilterFlag);
    this.router.navigate(["/switches/details/" + switchObj.switch_id]);
  }

  checkValue(value) {
    if (value === "" || value == undefined) {
      return "-";
    } else {
      return value;
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
  descrepancyString(row){
    let text = [];
    if(row.hasOwnProperty('controller-switch')){
        if(row['controller-switch']){
          text.push("controller:true");
        }else{
          text.push("controller:false");
        }
    }else{
      text.push("controller:false");
    }

    if(row.hasOwnProperty('inventory-switch')){
      if(row['inventory-switch']){
        text.push("inventory:true");
      }else{
        text.push("inventory:false");
      }
    }else{
      text.push("inventory:false");
    }

    return text.join(", ");
  }
  
  checkSwitchSettings(){

    this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
    if(this.hasStoreSetting){
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        dtInstance.columns( [4] ).visible( true );
      });
    }else{
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        dtInstance.columns( [4] ).visible( false );
      });
      
    }
  }

  copyToClip(event, copyItem,index) {
    if(copyItem == 'name'){
      var copyData = (this.clipBoardItems[index]['common-name']) ? this.checkValue(this.clipBoardItems[index]['common-name']) : this.checkValue(this.clipBoardItems[index][copyItem]);
    }else{
      var copyData = this.checkValue(this.clipBoardItems[index][copyItem]);
    }
    
    this.clipboardService.copyFromContent(copyData);
  }



}
