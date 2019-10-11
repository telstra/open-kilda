import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  Renderer2
} from "@angular/core";
import { DataTableDirective } from "angular-datatables";
import { SwitchService } from "../../../common/services/switch.service";
import { ToastrService } from "ngx-toastr";
import { Subject } from "rxjs";
import { Switch } from "../../../common/data-models/switch";
import { Router } from "@angular/router";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from "@angular/platform-browser";
import { StoreSettingtService } from "src/app/common/services/store-setting.service";
import { CommonService } from "src/app/common/services/common.service";

@Component({
  selector: "app-switch-list",
  templateUrl: "./switch-list.component.html",
  styleUrls: ["./switch-list.component.css"]
})
export class SwitchListComponent implements OnDestroy, OnInit, AfterViewInit {
  dataSet = [];

  loadingData = true;
  hasStoreSetting = false;
  settingSubscriber = null;
  textSearch:any;
  switchFilterFlag:string = localStorage.getItem('switchFilterFlag') || 'controller';

  constructor(
    private router: Router,
    private switchService: SwitchService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private titleService: Title,
    private renderer: Renderer2,
    private storeSwitchService: StoreSettingtService,
    public commonService:CommonService
  ) {}

  ngOnInit() {
    let ref = this;
    this.titleService.setTitle("OPEN KILDA - Switches");
    this.getStoreSwitchSettings();
  }

  ngAfterViewInit(): void {}



  loadSwitchList(filter){
    this.dataSet = [];
    this.loadingData = true;
    if(filter!=null){
      this.switchFilterFlag = filter;
    }
    var switchListData:any = {};
    if(this.switchFilterFlag == 'controller'){
       switchListData = JSON.parse(localStorage.getItem("SWITCHES_LIST"));
    }else{
      switchListData = JSON.parse(localStorage.getItem("SWITCHES_LIST_ALL"));
    }
   
    if(switchListData){
      var storageTime = switchListData.timeStamp;
      var startTime = new Date(storageTime).getTime();
      var lastTime = new Date().getTime();
      let timeminDiff = lastTime - startTime;
      var diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);;
      var switchList = switchListData.list_data;
      if (switchList && diffMins < 5) {
        this.dataSet = switchList;
        if(this.switchFilterFlag == 'inventory'){
          this.dataSet = this.dataSet.filter((d)=>{
            return d['inventory-switch'];
          })
        }
        setTimeout(()=>{
          this.loadingData = false;
        },100);
        
      } else {
        this.getSwitchList(this.switchFilterFlag);
      }
    }else{
       this.getSwitchList(this.switchFilterFlag);
    }
    
  }
  fulltextSearch(e){ 
    this.textSearch = e.target.value || ' ';
  }
  getSwitchList(filter) {
    if(filter !=null){
      this.switchFilterFlag = filter;
    }
    this.loadingData = true;
    this.loaderService.show("Loading Switches");
    let query = {controller:this.switchFilterFlag == 'controller', _: new Date().getTime(),storeConfigurationStatus:this.hasStoreSetting };
    this.switchService.getSwitchList(query).subscribe(
      (data: any) => {
        var switchListData = JSON.stringify({'timeStamp':new Date().getTime(),"list_data":data});
        if(this.switchFilterFlag == 'controller'){
          localStorage.setItem("SWITCHES_LIST", switchListData);
        }else{
          localStorage.setItem("SWITCHES_LIST_ALL", switchListData);
        }
       
        if (!data || data.length == 0) {
          this.toastr.info("No Switch Available", "Information");
          this.dataSet = [];
        } else {
          this.dataSet = data;
          if(this.switchFilterFlag == 'inventory'){
            this.dataSet = this.dataSet.filter((d)=>{
              return d['inventory-switch'];
            })
          }
        }
        this.loadingData = false;
      },
      error => {
        this.loaderService.hide();
        this.toastr.info("No Switch Available", "Information");
        this.dataSet = [];
        this.loadingData = false;
      }
    );
  }

  getStoreSwitchSettings(){
    let query = {_:new Date().getTime()};
    
    this.settingSubscriber = this.storeSwitchService.switchSettingReceiver.subscribe(setting=>{
      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;
      this.loadSwitchList(this.switchFilterFlag);
    });

    this.storeSwitchService.checkSwitchStoreDetails(query);
  }
  
  ngOnDestroy(): void {
    if(this.settingSubscriber){
      this.settingSubscriber.unsubscribe();
      this.settingSubscriber = null;
    }
  }

  
}
