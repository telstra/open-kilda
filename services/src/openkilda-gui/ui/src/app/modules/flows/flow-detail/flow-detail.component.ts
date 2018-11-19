import { Component, OnInit, HostListener, AfterViewInit, Renderer2 } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { Router, ActivatedRoute } from "@angular/router";
import { ToastrService } from "ngx-toastr";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ClipboardService } from "ngx-clipboard";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from '@angular/platform-browser';
import { CommonService } from "../../../common/services/common.service";
import { Location } from "@angular/common";
declare var jQuery: any;
 
@Component({
  selector: "app-flow-detail",
  templateUrl: "./flow-detail.component.html",
  styleUrls: ["./flow-detail.component.css"]
})

export class FlowDetailComponent implements OnInit {
  openedTab = "graph";
  flowDetail: any;
  validatedFlow: any = [];
  resyncedFlow : any = [];
  flowIs = '';
  contracts : any = [];
  loading = false;
  isLoadedcontract = false;
  clipBoardItems = {
    flowName: "",
    sourceSwitchName:"",
    sourceSwitch:"",
    targetSwitchName:"",
    targetSwitch:"",
    validateFlow:"",
    resyncFlow : "",
  }

  storeLinkSetting = false;
  statusDescrepancy = false;
  bandWidthDescrepancy = false;
  loadStatsGraph = false;
  sourceCheckedValue = false;
  targetCheckedValue = false;
  descrepancyData = {
    status:{
      controller: "-",
      inventory:"-"
    },
    bandwidth:{
      controller: "-",
      inventory:"-"
    }
  }

  
  public reRoutingInProgress = false;

  constructor(
    private flowService: FlowsService,
    private router: Router,
    private route: ActivatedRoute,
    private toaster: ToastrService,
    private maskPipe: SwitchidmaskPipe,
    private loaderService: LoaderService,
    private clipboardService: ClipboardService,
    private titleService: Title,
    private commonService: CommonService,
    private _location:Location,
    private _render:Renderer2,
    ) {
    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false
  }
  ngOnInit() {
    this.titleService.setTitle("OPEN KILDA - View Flow")
    //let flowId: string = this.route.snapshot.paramMap.get("id");
    this.route.params.subscribe(params => {
      this.loadStatsGraph = false;
      this.getFlowDetail(params['id']);// reset and set based on new parameter this time
      this.sourceCheckedValue = false;
      this.targetCheckedValue = false;
   });
    
  }
  
  openTab(tab) {
    this.openedTab = tab;
    if(tab == 'contracts'){
      this.loaderService.show('Loading contracts..');
      this.flowService.getcontract(this.flowDetail.flowid).subscribe(data=>{
        this.contracts  = data || [];
        this.isLoadedcontract = true;
        this.loaderService.hide();
      },(err)=>{
        this.isLoadedcontract = true;
        this.loaderService.hide();
           var Err = err.error;
           var msg  = (Err && typeof(Err['error-auxiliary-message'])!='undefined') ? Err['error-auxiliary-message']:'';
          this.toaster.error(msg,"Error");
      })  
    }
  }

  /**fetching flow detail via API call */
  getFlowDetail(flowId) {
    this.openedTab = 'graph';
    this.loadStatsGraph = true;
    this.clearResyncedFlow();
    this.clearValidatedFlow();
    this.loaderService.show("Fetching Flow Detail");
    this.bandWidthDescrepancy  = false;
    this.statusDescrepancy = false;
    this.flowService.getFlowDetailById(flowId).subscribe(
      flow => {
        flow["source_switch"] = this.convertSwitchPattern(flow["source_switch"]);
        flow["target_switch"] = this.convertSwitchPattern(flow["target_switch"]);
        this.flowDetail = flow;
        this.clipBoardItems = Object.assign(this.clipBoardItems,{
          flowName: flow.flowid,
          sourceSwitchName: flow["source_switch_name"],
          sourceSwitch: flow["source_switch"],
          targetSwitchName: flow["target_switch_name"],
          targetSwitch: flow["target_switch"]
        });

        if(flow['discrepancy'] && (flow['discrepancy']['status'] || flow['discrepancy']['bandwidth'])){
          if(flow['discrepancy']['status']){
            this.statusDescrepancy  = true;
            this.descrepancyData.status.controller = (typeof(flow['discrepancy']['status-value']['controller-status'])!='undefined') ?  flow['discrepancy']['status-value']['controller-status'] : "-";
            this.descrepancyData.status.inventory = (typeof(flow['discrepancy']['status-value']['inventory-status'])!='undefined') ?  flow['discrepancy']['status-value']['inventory-status'] : "-";
          }
          if(flow['discrepancy']['bandwidth']){
            this.bandWidthDescrepancy = true;
            this.descrepancyData.bandwidth.controller = (typeof(flow['discrepancy']['bandwidth-value']['controller-bandwidth'])!='undefined') ?  flow['discrepancy']['bandwidth-value']['controller-bandwidth'] : "-";
            this.descrepancyData.bandwidth.inventory = (typeof(flow['discrepancy']['bandwidth-value']['inventory-bandwidth'])!='undefined') ?  flow['discrepancy']['bandwidth-value']['inventory-bandwidth'] : "-";
          }
        }
        
        this.loaderService.hide();
      },
      error => {
        var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No Flow found';
        this.toaster.error(errorMsg, "Error");
        this._location.back();
        this.loaderService.hide();
      }
    );
  }

  convertSwitchPattern(switchId){
    if(switchId){
      if(switchId.startsWith("SW") || switchId.startsWith("sw")){
        switchId = switchId.substring(2);
        if(!switchId.includes(":")){
          return this.maskPipe.addCharacter(switchId,2).join(":").toLowerCase();
        }else{
          return switchId;
        }
      }else{
        return switchId;
      }
    }

  }
  /** Switch Id masking using toggle button */
  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.flowDetail[switchType+"_switch"] = this.maskPipe.transform(
        this.flowDetail[switchType+"_switch"],
        "legacy"
      );
    } else {
      this.flowDetail[switchType+"_switch"] = this.maskPipe.transform(
        this.flowDetail[switchType+"_switch"],
        "kilda"
      );
    }

    if(switchType == 'source'){
      this.clipBoardItems.sourceSwitch = this.flowDetail[switchType+"_switch"];
      this.sourceCheckedValue = e.target.checked ? true : false;
    }else{
      this.clipBoardItems.targetSwitch = this.flowDetail[switchType+"_switch"];
      this.targetCheckedValue = e.target.checked ? true : false;
    }
  }

  /** Validate flow */
  validateFlow() {
    this.validatedFlow = null;
    this.flowIs  ='validate';
    this.loading = true;
    this.flowService.validateFlow(this.flowDetail.flowid).subscribe(
      data => {
        this.validatedFlow = data;
        this.clipBoardItems.validateFlow = data;
        this.loading = false;
      },
      error => {
        this.flowIs  ='';
        this.loading = false;
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
  }

  /** Validate flow */
  resyncFlow() {
    this.resyncedFlow = null;
    this.flowIs  ='resync';
    this.loading = true;
    this.flowService.resynchFlow(this.flowDetail.flowid).subscribe(
      data => {
        this.resyncedFlow = data;
        this.clipBoardItems.resyncFlow = data;
        this.loading = false;
      },
      error => {
        this.flowIs  ='';
        this.loading = false;
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
  }

  /** Re-route flow path for best route  */
  reRouteFlow() {
    this.reRoutingInProgress = true;
    this.loaderService.show("Re-routing");
    this.flowService.getReRoutedPath(this.flowDetail.flowid).subscribe(
      data => {
        this.loaderService.hide();
        this.reRoutingInProgress = false; /** Re-load flow path components */
        if(data && typeof(data.rerouted)!=='undefined' && data.rerouted){
          this.toaster.success('Flow : '+this.flowDetail.flowid+" successfully re-routed!","success");
        } else {
          this.toaster.info('Flow : '+this.flowDetail.flowid+" already on best route!");
        }
      },
      error => {
        this.loaderService.hide();
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
    setTimeout(() => {
      this.reRoutingInProgress = false;
    }, 5000);
  }

  showMenu(e) {
    e.preventDefault();
    $(".clip-board-button").hide();
    $(".clip-board-button")
      .css({
        top: e.pageY + "px",
        left: e.pageX - 220 + "px",
        "z-index": 2
      })
      .toggle();
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  copyToClipHtml(event, copyHtmlItem){
    this.clipboardService.copyFromContent(jQuery('.code').text());
  }

  clearResyncedFlow(){
    this.resyncedFlow = [];
    this.flowIs ="";
  }

  clearValidatedFlow(){
    this.validatedFlow = [];
    this.flowIs ="";
  }

}
