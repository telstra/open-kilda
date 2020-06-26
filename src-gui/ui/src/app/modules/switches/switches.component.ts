import { Component, OnInit } from '@angular/core';
import { CommonService } from 'src/app/common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';
import { MessageObj } from 'src/app/common/constants/constants';
@Component({
  selector: 'app-switches',
  templateUrl: './switches.component.html',
  styleUrls: ['./switches.component.css']
})
export class SwitchesComponent implements OnInit {
  openedTab = 'search';
  constructor(private commonService:CommonService,private toastr:ToastrService,private router:Router) { 
    if(!this.commonService.hasPermission('menu_switches')){
      this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }
  }

  ngOnInit() {
  }
  openTab(tab){
    this.openedTab = tab;
  }
}
