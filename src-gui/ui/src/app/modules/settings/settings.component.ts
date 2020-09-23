import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  openedTab = 'storesetting';
  openedInnerTab = 'identityserver'
  isIdentityServer = false;
  constructor(
    private titleService: Title,
    public commonService:CommonService,
    private toastr:ToastrService,
    private router:Router
  ) { 
    if(!this.commonService.hasPermission('store_setting') && !this.commonService.hasPermission('application_setting') && !this.commonService.hasPermission('saml_setting')){
      this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }else{
        if(this.commonService.hasPermission('store_setting')){
          this.openedTab = 'storesetting';
        }else if(this.commonService.hasPermission('application_setting')){
          this.openedTab = 'applicationsetting';
        }else if(this.commonService.hasPermission('saml_setting')){
          this.openedTab = "samlsetting";
        }
      }
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Settings');
    this.commonService.linkStoreReceiver.subscribe((value:any)=>{
        this.isIdentityServer = value;
    });
    }
  
  openTab(tab){
    this.openedTab = tab;
  }
  openinnerTab(tab){
    this.openedInnerTab = tab;
  }

}
