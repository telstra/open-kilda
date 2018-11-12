import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { CommonService } from '../../../common/services/common.service';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';

@Component({
  selector: 'app-flow',
  templateUrl: './flow.component.html',
  styleUrls: ['./flow.component.css']
})
export class FlowComponent implements OnInit {
  openedTab = 'search';
  src : string =  null;
  dst : string = null;
  loadFlows = false
  constructor(
    private titleService : Title,
    private route: ActivatedRoute,
    public commonService: CommonService,
    private storeLinkService: StoreSettingtService
  ) { 
   
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Flows')
    this.src = this.route.snapshot.queryParamMap.get('src');
    this.dst = this.route.snapshot.queryParamMap.get('dst');
    
    if(this.src || this.dst){ 
      this.openedTab = 'list'; 
      this.loadFlows = true;
    }

    this.getStoreLinkSettings();
  }

  openTab(tab){
    this.openedTab = tab;
    if(tab == 'list'){
        this.loadFlows = true;
      }else{
        this.src = null;
        this.dst = null;
      }
    
  }

  copyToClip(event,item){
    //console.log('event', event,item, this[item]);
  }

  getStoreLinkSettings(){
    let query = {_:new Date().getTime()};
    this.storeLinkService.getLinkStoreDetails(query).subscribe((settings)=>{
      if(settings && settings['urls'] && typeof(settings['urls']['get-link']) !='undefined' &&  typeof(settings['urls']['get-link']['url'])!='undefined'){
        localStorage.setItem('linkStoreSetting',JSON.stringify(settings));
        localStorage.setItem('haslinkStoreSetting',"1");
      }else{
        localStorage.removeItem('linkStoreSetting');
        localStorage.removeItem('haslinkStoreSetting');
      }
    });
  }

}
