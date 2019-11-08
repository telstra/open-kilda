import { Component, Input,Renderer2,OnInit } from '@angular/core';
import { TopologyService } from '../../../common/services/topology.service';
import { TopologyView } from '../../../common/data-models/topology-view';
import { trigger, state, style, transition, animate} from '@angular/animations';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AffectedIslComponent } from "../affected-isl/affected-isl.component";
import {ImportTopologySettingComponent} from "../import-topology-setting/import-topology-setting.component";
import {ExportTopologySettingComponent} from "../export-topology-setting/export-topology-setting.component";
declare var jQuery: any;

@Component({
  selector: "app-topology-menu",
  templateUrl: "./topology-menu.component.html",
  styleUrls: ["./topology-menu.component.css"],
  animations: [
    trigger("changeState", [
      state(
        "show",
        style({
          opacity: 1,
          display:"block",
          "min-height": "200px",
          transition: "all 0.4s ease-in-out"
        })
      ),
      state(
        "hide",
        style({
          opacity: 0,
          display:"none",
          height: "0px",
          transition: "all 0.4s ease-in-out"
        })
      ),
      transition("show=>hide", animate("300ms")),
      transition("hide=>show", animate("300ms"))
    ])
  ]
})
export class TopologyMenuComponent implements OnInit {

  @Input() currentState;
  refreshMenu = "hide";
  showInfoFlag = "hide";
  submenu = '';
  refreshIntervals = [
    { label: "30 SECONDS", value: 30 },
    { label: "45 SECONDS", value: 45 },
    { label: "1 MINUTE", value: 60 },
    { label: "2 MINUTES", value: 120 },
    { label: "3 MINUTES", value: 180 },
    { label: "5 MINUTES", value: 300 },
    { label: "10 MINUTES", value: 600 },
    { label: "15 MINUTES", value: 900 },
    { label: "30 MINUTES", value: 1800 },
  ];
  constructor(
    private topologyService:TopologyService,
    private modalService: NgbModal,
  ) {}

  defaultSetting: TopologyView;
  linksdata = [];
  
  ngOnInit() {
    this.defaultSetting = this.topologyService.getViewOptions();
    this.topologyService.settingReceiver.subscribe((data: TopologyView) => {
      this.defaultSetting = data;
    });

    this.topologyService.autoRefreshReceiver.subscribe((data: TopologyView) => {
      this.defaultSetting = data;
    });
  }

  toggleRefreshMenu() {
    this.refreshMenu = this.refreshMenu == "hide" ? "show" : "hide";
  }

  setAutoRefresh = (refreshInterval) => {
    let currentSettings = this.defaultSetting;
    currentSettings.REFRESH_INTERVAL = refreshInterval; /** Interval in seconds */
    currentSettings.REFRESH_CHECKED = 1;
    this.topologyService.setAutoRefreshSetting(currentSettings);
    this.toggleRefreshMenu();
  };

  stopAutoRefresh = ()=>{
    let currentSettings = this.defaultSetting;
    currentSettings.REFRESH_CHECKED = 0;
    currentSettings.REFRESH_INTERVAL = 0;
    this.topologyService.setAutoRefreshSetting(currentSettings);
    this.toggleRefreshMenu();
  }

  showIsl() {
    let settings = this.defaultSetting;
    settings.ISL_CHECKED = 1;
    settings.FLOW_CHECKED = 0;
    this.topologyService.setViewOptinos(settings);
  }

  openSubmenu(id){
    if(this.submenu == id){
      this.submenu = '';
    }else{
      this.submenu = id;
    }
  }

  showFlow() {
    let settings = this.defaultSetting;
    settings.ISL_CHECKED = 0;
    settings.FLOW_CHECKED = 1;
    this.topologyService.setViewOptinos(settings);
  }

  showSwitch() {
    let settings = this.defaultSetting;
    settings.SWITCH_CHECKED = settings.SWITCH_CHECKED ? 0 : 1;
    this.topologyService.setViewOptinos(settings);
  }

   showInfo = () => {
    this.showInfoFlag = this.showInfoFlag == "hide" ? "show" : "hide";
  };

  importSettingModal(){
    const modalRef = this.modalService.open(ImportTopologySettingComponent,{ size: 'lg',windowClass:'modal-import-setting slideInUp'});
  }

  showAffectedISL(){
    const modalRef = this.modalService.open(AffectedIslComponent,{ size: 'lg',windowClass:'modal-isl slideInUp'});
  }

  exportSetting(){
    const modalRef = this.modalService.open(ExportTopologySettingComponent,{ size: 'lg',windowClass:'modal-import-setting slideInUp'});
  }

  onClickedOutside(e: Event,type) {
    this[type] = "hide";
  }

}
