import { Component, OnInit, AfterViewInit } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ToastrService } from 'ngx-toastr';
import { Router, NavigationEnd} from "@angular/router";
import { filter } from "rxjs/operators";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { ClipboardService } from "ngx-clipboard";
import { Title } from '@angular/platform-browser';
import { CommonService } from '../../../common/services/common.service';

@Component({
  selector: 'app-switch-detail',
  templateUrl: './switch-detail.component.html',
  styleUrls: ['./switch-detail.component.css']
})
export class SwitchDetailComponent implements OnInit, AfterViewInit {
  switch_id: string;
  name: string;
  address: string;
  hostname: string;
  description: string;
  state: string;
  openedTab : string = 'port';
  currentRoute: string = 'switch-details';
  clipBoardItems = {
    sourceSwitchName:"",
    sourceSwitch:"",
    targetSwitchName:""
  }

  constructor(private switchService:SwitchService,
        private maskPipe: SwitchidmaskPipe,
        private toastr: ToastrService,
        private router: Router,
        private loaderService: LoaderService,
        private clipboardService: ClipboardService,
        private titleService: Title,
        private commonService: CommonService
        ) {}

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View Switch');
      if(this.router.url.includes("/port")){
         this.router.navigated = false;
        this.router.navigate([this.router.url]);
      }

    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd)) .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        let tempRoute : any = event;
        if(tempRoute.url.includes("/port")){
          this.currentRoute = 'port-details';
        }
        else{
          this.currentRoute = 'switch-details';
        }
   
      });  

  	let retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
      this.switch_id =retrievedSwitchObject.switch_id;
      this.name =retrievedSwitchObject.name;
      this.address =retrievedSwitchObject.address;
      this.hostname =retrievedSwitchObject.hostname;
      this.description =retrievedSwitchObject.description;
      this.state =retrievedSwitchObject.state;

      this.clipBoardItems = Object.assign(this.clipBoardItems,{
          
          sourceSwitchName: retrievedSwitchObject.name,
          sourceSwitch: this.switch_id,
          targetSwitchName: retrievedSwitchObject.hostname
          
        });

  }

  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.switch_id = this.maskPipe.transform(this.switch_id,'legacy');
    } else {
      this.switch_id = this.maskPipe.transform(this.switch_id,'kilda');
    }

    this.clipBoardItems.sourceSwitch = this.switch_id;

  }

  toggleTab(tab){
  	if(tab === 'rules'){
  		this.openedTab = 'rules';
  	}
  	else{
  		this.openedTab = 'port';
  	}
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  ngAfterViewInit(){
    
  }
}


