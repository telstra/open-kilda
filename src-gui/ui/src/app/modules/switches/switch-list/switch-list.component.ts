import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  Renderer2
} from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { ToastrService } from 'ngx-toastr';
import { Subject } from 'rxjs';
import { Switch } from '../../../common/data-models/switch';
import { Router } from '@angular/router';
import { NgxSpinnerService } from 'ngx-spinner';
import { LoaderService } from '../../../common/services/loader.service';
import { Title } from '@angular/platform-browser';
import { StoreSettingtService } from 'src/app/common/services/store-setting.service';
import { CommonService } from 'src/app/common/services/common.service';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-switch-list',
  templateUrl: './switch-list.component.html',
  styleUrls: ['./switch-list.component.css']
})
export class SwitchListComponent implements OnDestroy, OnInit, AfterViewInit {
  dataSet = [];

  loadingData = true;
  hasStoreSetting = false;
  settingSubscriber = null;
  textSearch: any;
  switchFilterFlag: string = sessionStorage.getItem('switchFilterFlag') || 'controller';

  constructor(
    private router: Router,
    private switchService: SwitchService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private titleService: Title,
    private renderer: Renderer2,
    private storeSwitchService: StoreSettingtService,
    public commonService: CommonService
  ) {}

  ngOnInit() {
    const ref = this;
    this.titleService.setTitle('OPEN KILDA - Switches');
    this.getStoreSwitchSettings();
  }

  ngAfterViewInit(): void {}


  loadSwitchList(filter) {
    this.dataSet = [];
    this.loadingData = true;
    if (filter != null) {
      this.switchFilterFlag = filter;
    }
    let switchListData: any = {};
    if (this.switchFilterFlag == 'controller') {
       switchListData = JSON.parse(localStorage.getItem('SWITCHES_LIST'));
    } else {
      switchListData = JSON.parse(localStorage.getItem('SWITCHES_LIST_ALL'));
    }

    if (switchListData) {
      const storageTime = switchListData.timeStamp;
      const startTime = new Date(storageTime).getTime();
      const lastTime = new Date().getTime();
      const timeminDiff = lastTime - startTime;
      const diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);
      const switchList = switchListData.list_data;
      if (switchList && diffMins < 5) {
        this.dataSet = switchList;
        if (this.switchFilterFlag == 'inventory') {
          this.dataSet = this.dataSet.filter((d) => {
            return d['inventory_switch_detail'];
          });
        }
        setTimeout(() => {
          this.loadingData = false;
        }, 100);

      } else {
        this.getSwitchList(this.switchFilterFlag);
      }
    } else {
       this.getSwitchList(this.switchFilterFlag);
    }

  }
  fulltextSearch(e) {
    this.textSearch = e.target.value || ' ';
  }
  getSwitchList(filter) {
    if (filter != null) {
      this.switchFilterFlag = filter;
    }
    this.loadingData = true;
    this.loaderService.show(MessageObj.loading_switches);
    this.switchService.getSwitchDetails(null,this.switchFilterFlag).subscribe(
      (data: any) => {
        const switchListData = JSON.stringify({'timeStamp': new Date().getTime(), 'list_data': data});
        if (this.switchFilterFlag == 'controller') {
          localStorage.setItem('SWITCHES_LIST', switchListData);
        } else {
          localStorage.setItem('SWITCHES_LIST_ALL', switchListData);
        }

        if (!data || data.length == 0) {
          this.toastr.info(MessageObj.no_switch_available, 'Information');
          this.dataSet = [];
        } else {
          this.dataSet = data;
          if (this.switchFilterFlag == 'inventory') {
            this.dataSet = this.dataSet.filter((d) => {
              return d['inventory_switch_detail'];
            });
          }
        }
        this.loadingData = false;
      },
      error => {
        this.loaderService.hide();
        this.toastr.info(MessageObj.no_switch_available, 'Information');
        this.dataSet = [];
        this.loadingData = false;
      }
    );
  }

  getStoreSwitchSettings() {
    const query = {_: new Date().getTime()};
    this.settingSubscriber = this.storeSwitchService.switchSettingReceiver.subscribe(setting => {
      this.hasStoreSetting = localStorage.getItem('hasSwtStoreSetting') == '1';
      this.loadSwitchList(this.switchFilterFlag);
    });

    this.storeSwitchService.checkSwitchStoreDetails(query);
  }

  ngOnDestroy(): void {
    if (this.settingSubscriber) {
      this.settingSubscriber.unsubscribe();
      this.settingSubscriber = null;
    }
  }


}
