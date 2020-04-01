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
  activeSubmenu :any = '';
  constructor(private router: Router, public commonService: CommonService) {
    this.router.events.subscribe((_:NavigationEnd) => {
      this.currentUrl = router.url
      if(!this.currentUrl.includes('/flows')){
        localStorage.removeItem('flows');
        localStorage.removeItem('haslinkStoreSetting');
        localStorage.removeItem('linkStoreSetting');
        localStorage.removeItem('linkStoreStatusList');
        localStorage.removeItem('activeFlowStatusFilter'); 
        localStorage.removeItem('filterFlag');          
        localStorage.removeItem('flowsinventory');       
      }
      if(!this.currentUrl.includes('/isl')) {
        localStorage.removeItem('linkData');
        localStorage.removeItem('ISL_LIST');  
      } 
      if(!this.currentUrl.includes('/switches')) {
        localStorage.removeItem('SWITCHES_LIST');
        localStorage.removeItem('SWITCHES_LIST_ALL');
        localStorage.removeItem('switchPortDetail');  
      }  

      if(!this.currentUrl.includes('/port')) {
        localStorage.removeItem('portFlows');
        localStorage.removeItem('portFlowInventory');
      }  
     
      this.commonService.setCurrentUrl(router.url);
      
    });
  }

  ngOnInit() {}


  urlmatch(url){
     return this.currentUrl.includes(url);
  }

  toggleSubmenu(id){
    if(this.activeSubmenu == id){
      this.activeSubmenu = '';
    }else{
      this.activeSubmenu = id;
    }
    
  }

  closeSidebar() {
    jQuery('body').removeClass('mini-sidebar');
    localStorage.removeItem('sidebarToggled');
  }

  openSidebar() {
    jQuery('body').addClass('mini-sidebar');
    localStorage.setItem('sidebarToggled','1');
  }
  
}
