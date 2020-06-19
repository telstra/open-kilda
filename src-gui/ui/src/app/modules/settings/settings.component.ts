import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';

@Component({
  selector: 'app-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.css']
})
export class SettingsComponent implements OnInit {

  openedTab = 'identityserver';
  isIdentityServer = false;
  constructor(
    private titleService: Title,
    private commonService:CommonService,
    private toastr:ToastrService,
    private router:Router
  ) { 
    if(!this.commonService.hasPermission('store_setting')){
      this.toastr.error('You are not authorised to access this');  
       this.router.navigate(["/home"]);
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

}
