import { Component, OnInit } from '@angular/core';
import { Router, NavigationEnd } from '@angular/router';
import { CommonService } from '../../services/common.service';

@Component({
  selector: 'app-sidebar',
  templateUrl: './sidebar.component.html',
  styleUrls: ['./sidebar.component.css']
})
export class SidebarComponent implements OnInit {

  currentUrl :any = '';

  constructor(private router: Router, public commonService: CommonService) {
    this.router.events.subscribe((_:NavigationEnd) => {
      this.currentUrl = router.url
      if(!this.currentUrl.includes('/flows')){
        localStorage.removeItem('flows');
      }

      this.commonService.setCurrentUrl(router.url);
      
    });
  }

  ngOnInit() {}


  urlmatch(url){
    if(!this.currentUrl.includes('/switches')){
      localStorage.removeItem('SWITCHES_LIST');
    }

    
    return this.currentUrl.includes(url);
  }
}
