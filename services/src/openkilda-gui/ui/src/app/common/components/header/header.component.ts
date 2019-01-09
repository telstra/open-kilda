import { Component, OnInit, AfterViewInit } from '@angular/core';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ChangepasswordComponent } from '../changepassword/changepassword.component';
import { environment } from '../../../../environments/environment';
import { CommonService } from '../../services/common.service';
import { Router,NavigationEnd } from '@angular/router';
import { CookieManagerService } from '../../services/cookie-manager.service';
import { LoaderService } from '../../services/loader.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.css']
})
export class HeaderComponent implements OnInit, AfterViewInit {

  username: string;

  constructor(
    private modalService: NgbModal,
    public commonService: CommonService,
    private router:Router,
    private cookieManager: CookieManagerService,
    private appLoader:LoaderService,
  ) { }

  openChangePassword(){
    this.modalService.open(ChangepasswordComponent,{backdrop: 'static', windowClass:'animated slideInUp'});
  }

  ngAfterViewInit(): void {
    
  }

  ngOnInit() {
    this.router.events.subscribe((_:NavigationEnd) => {
      setTimeout(() => {
        this.username = localStorage.getItem('username');
        
      });
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
