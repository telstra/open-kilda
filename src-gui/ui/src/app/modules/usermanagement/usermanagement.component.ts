import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { TabService } from '../../common/services/tab.service';
import { CommonService } from '../../common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';

@Component({
  selector: 'app-usermanagement',
  templateUrl: './usermanagement.component.html',
  styleUrls: ['./usermanagement.component.css']
})
export class UsermanagementComponent implements OnInit {

  openedTab = 'users';
  subscription: Subscription;

  constructor(private tabService: TabService,
    public commonService: CommonService,
    private toaster:ToastrService,
    private router:Router
  ) {

    if(!this.commonService.hasPermission('menu_user_management')){
      this.toaster.error('You are not authorised to access this');  
       this.router.navigate(["/home"]);
      }
    this.subscription = this.tabService.getSelectedTab().subscribe(tab => {
      if(tab){
        this.openedTab = tab.text;
      }
    });
   }

  ngOnInit() {
  }

  openTab(tab){
    this.openedTab = tab;
  }

}
