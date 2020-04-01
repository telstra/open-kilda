import { Component, ChangeDetectorRef, AfterContentChecked, OnInit, AfterViewChecked } from '@angular/core';
import { NgxSpinnerService } from 'ngx-spinner';
import { LoaderService } from './common/services/loader.service';
import { UserService } from './common/services/user.service'
import { Title } from '@angular/platform-browser';
import { Router, NavigationEnd } from '@angular/router';
import { CommonService } from './common/services/common.service';
declare var jQuery: any;

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, AfterViewChecked {
  loaderMessage:string = null;
  currentUrl :any = '';
  sidebarSetting:any=false;

  constructor(
    private userService: UserService,
    private loader:NgxSpinnerService,
    private appLoader:LoaderService,
    private cdr:ChangeDetectorRef,
    private titleService: Title,
    private router: Router,
    private commonService :CommonService
  ) {

    this.router.events.subscribe((_:NavigationEnd) => {
      this.currentUrl = router.url

      var scrollToTop = window.setInterval(function () {
        $( "div.content" ).scrollTop(0);
        if($( "div.content").scrollTop() <=0){
          window.clearInterval(scrollToTop);
        }
      }, 16); 
    });

    this.userService.getLoggedInUserInfo().subscribe((user:any) => {
      if(user && user.permissions) {
        localStorage.setItem("is2FaEnabled", user.is2FaEnabled);
        localStorage.setItem("username", user.name);
        localStorage.setItem("user_id", user.user_id);
        localStorage.setItem("userPermissions", JSON.stringify(user.permissions));
        this.commonService.setUserData(user);
      }
    });

  }

  ngOnInit() {
    this.appLoader.messageReciever.subscribe((data)=>{
      if(data.show){
        this.loader.show();
        this.loaderMessage = data.message;
       
      }
      else if(!data.show){
        this.loader.hide();
        this.loaderMessage = null;
      }
    });

    setTimeout(()=>{
      this.sidebarSetting = localStorage.getItem('sidebarToggled') || false;
      if(this.sidebarSetting){
        jQuery('body').addClass('mini-sidebar');
      }else{
        jQuery('body').removeClass('mini-sidebar');
      }
      
    });

  }

  ngAfterViewChecked(){
    this.cdr.detectChanges();
  }

}
