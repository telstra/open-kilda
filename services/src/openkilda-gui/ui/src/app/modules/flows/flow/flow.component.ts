import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { CommonService } from '../../../common/services/common.service';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';
import { FlowsService } from 'src/app/common/services/flows.service';
import { LoaderService } from 'src/app/common/services/loader.service';

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
    private storeLinkService: StoreSettingtService,
    private flowService : FlowsService,
    private loaderService: LoaderService,
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

  getStatusList(){ 
    if(!localStorage.getItem('linkStoreStatusList')){
        this.flowService.getStatusList().subscribe((statuses)=>{
            localStorage.setItem('linkStoreStatusList',JSON.stringify(statuses));
            this.loaderService.hide();
        },(error)=>{
          localStorage.setItem('linkStoreStatusList',JSON.stringify([]));
          this.loaderService.hide();
        });
    }else{
      this.loaderService.hide();
    }
    
  }
  getStoreLinkSettings(){
    if(!localStorage.getItem('linkStoreSetting')){
      this.loaderService.show('Checking Link Store Configuration..');
      let query = {_:new Date().getTime()};
      this.storeLinkService.getLinkStoreDetails(query).subscribe((settings)=>{
        if(settings && settings['urls'] && typeof(settings['urls']['get-link']) !='undefined' &&  typeof(settings['urls']['get-link']['url'])!='undefined'){
          localStorage.setItem('linkStoreSetting',JSON.stringify(settings));
          localStorage.setItem('haslinkStoreSetting',"1");
          this.getStatusList();
        }else{
          localStorage.removeItem('linkStoreSetting');
          localStorage.removeItem('haslinkStoreSetting');
          this.loaderService.hide();
        }
      },(err)=>{
        this.loaderService.hide();
      });
    }else{
      this.getStatusList();
    }
   
  }

}
