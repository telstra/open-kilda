import { Component, OnInit, ViewChild, OnDestroy, AfterViewInit, Input, OnChanges, SimpleChanges} from '@angular/core';
import { LoaderService } from 'src/app/common/services/loader.service';
import { SwitchService } from 'src/app/common/services/switch.service';
import { CommonService } from 'src/app/common/services/common.service';

@Component({
  selector: 'app-flows',
  templateUrl: './flows.component.html',
  styleUrls: ['./flows.component.css']
})
export class FlowsComponent implements OnDestroy, OnInit,OnChanges, AfterViewInit {
  @Input() switchid;
  @Input() portnumber;
  data = [];
  textSearch:any;
  flowSubscriber = null;
  portFlowFlag = 'controller';
  ifLoadingData = false;
  storeLinkSetting = localStorage.getItem('hasSwtStoreSetting') == '1' ? true : false;

  constructor(private loaderService : LoaderService,private switchService:SwitchService,public commonService:CommonService) { }

  ngOnInit() {
    this.loadPortFlows(this.portFlowFlag);
  }

  loadPortFlows(filter){
    this.ifLoadingData = true;
    this.loaderService.show('Fetching flows');
    if(filter){
      this.portFlowFlag = filter;
    }
    var ref = this;
    var filterFlag = this.portFlowFlag == 'inventory';
    let switchId = this.switchid;
    let portNumber = this.portnumber;
    var flowData = null;
    if(filterFlag){
      flowData = JSON.parse(localStorage.getItem('portFlowInventory')) || null;
    }else{
      flowData = JSON.parse(localStorage.getItem('portFlows')) || null;
    }
    if(flowData && flowData.length){
      this.data = flowData;
      this.loaderService.hide();
      this.ifLoadingData = false;
    }else{
      this.flowSubscriber = this.switchService.getSwitchFlows(switchId,filterFlag,portNumber).subscribe((flows:any)=>{
        if(this.portFlowFlag == 'controller'){
            this.data = flows;
            localStorage.setItem('portFlows',JSON.stringify(this.data));
            this.ifLoadingData = false;
        }else{
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
          this.ifLoadingData = false;
          localStorage.setItem('portFlowInventory',JSON.stringify(this.data));
        }
      
      },error =>{
        this.data = [];
        ref.loaderService.hide();
        this.ifLoadingData = false;
      });
    }
    
  }

  fulltextSearch(e){ 
    this.textSearch = e.target.value || ' ';
  }

 

  ngAfterViewInit(): void {
   
  }

  rerender(): void {
  
  }

  ngOnDestroy(): void {
    if(this.flowSubscriber){
      this.flowSubscriber.unsubscribe();
      this.flowSubscriber = null;
    }
  }

  ngOnChanges(change: SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }
  }

 


}