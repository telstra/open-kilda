import { Component, OnInit, AfterViewInit } from '@angular/core';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ChangepasswordComponent } from '../changepassword/changepassword.component';
import { trigger, state, style, transition, animate} from '@angular/animations';
import { environment } from '../../../../environments/environment';
import { CommonService } from '../../services/common.service';
import { Router,NavigationEnd } from '@angular/router';
import { CookieManagerService } from '../../services/cookie-manager.service';
import { LoaderService } from '../../services/loader.service';
import { TopologyService } from '../../services/topology.service';
import { TopologyView } from '../../../common/data-models/topology-view';


@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css'],
  animations: [
    trigger("changeState", [
      state(
        "show",
        style({
          opacity: 1,
          display:"block",
          transition: "all 0.4s ease-in-out"
        })
      ),
      state(
        "hide",
        style({
          opacity: 0,
          display:"none",
          transition: "all 0.4s ease-in-out"
        })
      ),
      transition("show=>hide", animate("30ms")),
      transition("hide=>show", animate("30ms"))
    ])
  ]
})
export class HeaderComponent implements OnInit, AfterViewInit {

  username: string;  
  notification_arr= [];  
  showNotification="hide";
  currentUrl :any = '';
  flagTopology = false;
  constructor(
    private modalService: NgbModal,
    public commonService: CommonService,
    private router:Router,
    private cookieManager: CookieManagerService,
    private appLoader:LoaderService,
    private topologyService:TopologyService,
  ) { 
    this.router.events.subscribe((_:NavigationEnd) => {
      this.currentUrl = router.url;
      if(this.currentUrl.includes('/topology')) {
        this.flagTopology = true;
      }else{
        this.flagTopology = false;
      }
    });
    
  }
  defaultSetting: TopologyView;
  openChangePassword(){
    this.modalService.open(ChangepasswordComponent,{backdrop: 'static', windowClass:'animated slideInUp'});
  }

  ngAfterViewInit(): void {
    
  }

  toggleNotification(){
    this.showNotification = this.showNotification == "hide" ? "show": "hide";
  }
  onClickedOutside(e: Event,type) {
    this[type] = "hide";
  }
  clearAllNotification(){
      this.notification_arr = [];
      localStorage.removeItem('notification_data');
      localStorage.setItem('notification_data',JSON.stringify(this.notification_arr));
  }
  
  openNotificationObject(data){
    if(data && data.type!= 'undefined'){
      switch(data.type){
        case 'switch':  this.openUrl(environment.appEndPoint+"/switches/details/" + data.switch.switch_id); break;
        case 'switchadded': this.openUrl(environment.appEndPoint+"/switches/details/" + data.switch.switch_id); break;        
        case 'switchremoved':this.openUrl(environment.appEndPoint+"/switches/details/" + data.switch.switch_id); break;
        case 'isl':this.openUrl(environment.appEndPoint+"/isl/switch/isl/"+data.newlink.source_switch+"/"+data.newlink.src_port+"/"+data.newlink.target_switch+"/"+data.newlink.dst_port); break;
        case 'isladded':this.openUrl(environment.appEndPoint+"/isl/switch/isl/"+data.newlink.source_switch+"/"+data.newlink.src_port+"/"+data.newlink.target_switch+"/"+data.newlink.dst_port); break;
        case 'islremoved': this.openUrl(environment.appEndPoint+"/isl/switch/isl/"+data.newlink.source_switch+"/"+data.newlink.src_port+"/"+data.newlink.isl.target_switch+"/"+data.newlink.dst_port); break;
      }
    }
  }
  openUrl(url){
    window.open(
      url,
      '_blank' 
    );
  }
  openNotification(data){
    if(data && data.type!= 'undefined'){
      this.topologyService.highlightNotifications(data); 
    }
  }
  ngOnInit() {
    this.defaultSetting = this.topologyService.getViewOptions();
    this.router.events.subscribe((_:NavigationEnd) => {
      setTimeout(() => {
        this.username = localStorage.getItem('username');        
      });
    });

    this.topologyService.notificationObj.subscribe((data: any) => {
      this.notification_arr = data;
      if(this.notification_arr.length == 0){
        this.notification_arr = JSON.parse(localStorage.getItem('notification_data')) || [];
      }
    });
    this.topologyService.settingReceiver.subscribe((data: TopologyView) => {
      this.defaultSetting = data;
    });
    this.commonService.sessionReceiver.subscribe((user :any)=>{
      this.username = user.name;
    });
  }

  logOut(){ 
    this.appLoader.show();
    this.cookieManager.set('isLoggedOutInProgress',1);
    this.commonService.getLogout().subscribe((data)=>{
      location.href= environment.appEndPoint+'/login';
    },err=>{
      location.href= environment.appEndPoint+'/login';
    });
  }



}
