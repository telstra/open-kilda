import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';

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
    private commonService:CommonService
  ) { 
   
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
