import { Component, OnInit, Input,ViewChild, Output, EventEmitter, OnChanges, OnDestroy, AfterViewInit, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Router } from '@angular/router';
import { Switch } from 'src/app/common/data-models/switch';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';
import { ClipboardService } from 'ngx-clipboard';
import { SwitchService } from 'src/app/common/services/switch.service';
import { CommonService } from 'src/app/common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { MessageObj } from 'src/app/common/constants/constants';
declare var jQuery: any;

@Component({
  selector: 'app-switch-datatable',
  templateUrl: './switch-datatable.component.html',
  styleUrls: ['./switch-datatable.component.css']
})
export class SwitchDatatableComponent implements OnInit, OnChanges,OnDestroy,AfterViewInit {

  @ViewChild(DataTableDirective, { static: true })  datatableElement: DataTableDirective;
  @Input() data =[];
  @Input() switchFilterFlag:string;  
  @Input() textSearch:any;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = false;
  hasStoreSetting =false;
  flowSubscription:Subscription[]=[];
  switch_id : boolean = false;
  commonname : boolean = false;
  name : boolean = false;
  address : boolean = false;
  hostname : boolean = false;
  poplocation : boolean = false;
  description : boolean = false;
  sumofflows:boolean = false;
  noofflows:boolean=false;
  state : boolean = false;
  enableExportBtn:boolean = false;
  clipBoardItems = [];
  flowDataOfSwitch:any={};
  constructor(private loaderService : LoaderService,
    private renderer: Renderer2, 
    private router:Router,
    private commonService:CommonService,
    private toastr:ToastrService,
    private storeSwitchService: StoreSettingtService,
    private clipboardService:ClipboardService,
    private switchService:SwitchService
  ) { 
    if(!this.commonService.hasPermission('menu_switches')){
       this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }
  }

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
        drawCallback:function(){
          if(jQuery('#switchDataTable tbody tr').length < 10){
            jQuery('#switchDataTable_next').addClass('disabled');
          }else{
            jQuery('#switchDataTable_next').removeClass('disabled');
          }
        },
      "aoColumns": [
        { sWidth: '10%' },
        { sWidth: '10%',"sType": "name","bSortable": true },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },
        { sWidth: '10%' },        
        { sWidth: '15%' },
        { sWidth: '25%' },
        { sWidth: '10%' }
      ],
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
        { targets: [9], visible: false}
      ]
    };

    this.fetchSwitchFlowDataObj();
  
  }

  async fetchSwitchFlowDataObj(){
   
    if(this.data && this.data.length){
      var i=0;
      var processComplete = 1;
      this.data.forEach((d)=>{
        this.flowSubscription[i] = this.switchService.getSwitchFlows(d.switch_id,d['inventory-switch'],null).subscribe(data=>{
          let flowsData:any = data;
          d['sumofbandwidth'] = 0;
          d['noofflows'] = 0;
            if(flowsData && flowsData.length){
              for(let flow of flowsData){
                d['sumofbandwidth'] = d['sumofbandwidth']  + (flow.maximum_bandwidth / 1000);
              }
              if(d['sumofbandwidth'] ){
                d['sumofbandwidth'] = d['sumofbandwidth'].toFixed(3); 
              }              
              d['noofflows'] = flowsData.length;
            }
          },error=>{
            d['sumofbandwidth'] = 0;
            d['noofflows'] = 0;
          },()=>{
            processComplete = processComplete + 1;
            if(this.data.length == processComplete){
              this.enableExportBtn = true;
            }
          }) 
         i++; 
      });
      
    }
  }

  exportCsv(val){
    var headings = ["Switch ID", "Name", "Address","Hostname","Pop Location","Sum(Bandwidth) of Flows(Mbps)","No Of Flows","Description","State", "Evacuate", "Hardware","Location","Manufacturer","Version","Port", "Serial Number","Software", "Under Maintenance"];
   
    if(val){
       headings = ["Switch ID", "Name", "Address","Hostname","Pop Location","Sum(Bandwidth) of Flows(Mbps)","No Of Flows","Description","State"];
     }
    var lineArray = [];
    lineArray.push(headings);
    this.data.forEach(function(d){
      var line = [];
      line.push("\"" + ((d.switch_id)? d.switch_id : '-') + "\"");
      line.push("\"" + ((d.name)? d.name : '-') + "\"");
      line.push("\"" + ((d.address)? d.address : '-') + "\"");
      line.push("\"" + ((d.hostname)? d.hostname : '-') + "\"");
      line.push("\"" + (d['pop-location'] || '-') + "\"");
      line.push("\"" + ((d.sumofbandwidth || d.sumofbandwidth ==0) ? d.sumofbandwidth : '-') + "\"");
      line.push("\"" + ((d.noofflows || d.noofflows == 0 ) ? d.noofflows : '-') + "\"");
      line.push("\"" + ((d.description)? d.description : '-') + "\"");
      line.push("\"" + ((d.state)? d.state : '-') + "\"");
      if(!val){
        var locationString = 'longitude:'+((d.location.longitude) ? d.location.longitude : '-')+', latitude:'+((d.location.latitude)? d.location.latitude : '-')+', city:'+((d.location.city) ? d.location.city : '-')+', street:'+((d.location.street)? d.location.street : '-')+', Country:'+((d.location.country)? d.location.country : '-');
        line.push("\"" + ((d.evacuate)? d.evacuate : 'false') + "\"");
        line.push("\"" + ((d.hardware)? d.hardware : '-') + "\"");
        line.push("\"" + locationString + "\"");
        line.push("\"" + ((d.manufacturer)? d.manufacturer : '-') + "\"");
        line.push("\"" + ((d.of_version)? d.of_version : '-') + "\"");
        line.push("\"" + ((d.port)? d.port : '-') + "\"");
        line.push("\"" + ((d.serial_number)? d.serial_number : '-') + "\"");
        line.push("\"" + ((d.software)? d.software : '-') + "\"");
        line.push("\"" + ((d.under_maintenance)? d.under_maintenance : 'false') + "\"");
      }
      
      var csvLine = line.join(",");
      lineArray.push(csvLine);
    })
    var fileName = "OPEN KILDA - Switches";
    var csvContent = lineArray.join("\n");
    let blob = new Blob(['\ufeff' + csvContent], {
        type: 'text/csv;charset=utf-8;'
    });
    let dwldLink = document.createElement("a");
    let url = URL.createObjectURL(blob);
    let isSafariBrowser = navigator.userAgent.indexOf('Safari') != -1;
    navigator.userAgent.indexOf('Chrome') == -1;
    if (isSafariBrowser) {
        dwldLink.setAttribute("target", "_blank");
    }
    dwldLink.setAttribute("href", url);
    dwldLink.setAttribute("download", fileName + ".csv");
    dwldLink.style.visibility = "hidden";
    document.body.appendChild(dwldLink);
    dwldLink.click();
    document.body.removeChild(dwldLink);
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
    if(this.flowSubscription && this.flowSubscription.length){
      this.flowSubscription.forEach((subscription) => subscription.unsubscribe());
      this.flowSubscription = [];
    }
    
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
    const switchDetailsKey = 'switchDetailsKey_' + switchObj.switch_id;
    localStorage.setItem(
        switchDetailsKey,
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
