import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { LoaderService } from 'src/app/common/services/loader.service';
import { IslListService } from 'src/app/common/services/isl-list.service';
import { ToastrService } from 'ngx-toastr';
import { timeDay } from 'd3';
import { MessageObj } from 'src/app/common/constants/constants';


@Component({
  selector: 'app-isl-list',
  templateUrl: './isl-list.component.html',
  styleUrls: ['./isl-list.component.css']
})
export class IslListComponent implements OnInit, AfterViewInit {

  dataSet = [];
  loadingData = true;
  constructor(
    private titleService: Title,
    private loaderService: LoaderService,
    private islListService: IslListService,
    private toastr: ToastrService
  ) { }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - ISL');
    const islListData = JSON.parse(localStorage.getItem('ISL_LIST'));
    if (islListData) {
      const storageTime = islListData.timeStamp;
      const startTime = new Date(storageTime).getTime();
      const lastTime = new Date().getTime();
      const timeminDiff = lastTime - startTime;
      const diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);
      const islList = islListData.list_data;
      if (islList && diffMins < 5) {
        this.dataSet = islList;
        this.loadingData = false;
      } else {
        this.getISLlistService();
      }
    } else {
      this.getISLlistService();
    }

  }

  getISLlistService() {
   this.loaderService.show('Loading ISL');
    this.loadingData = true;
    const query = {_: new Date().getTime()};
    this.islListService.getIslList(query).subscribe(
      (data: Array<object>) => {
        const islListData = JSON.stringify({'timeStamp': new Date().getTime(), 'list_data': data});
        localStorage.setItem('ISL_LIST', islListData);
        if (!data || data.length == 0) {
          this.toastr.info(MessageObj.no_isl_available, 'Information');
          this.dataSet = [];
        } else {
          this.dataSet = data;
        }
        this.loadingData = false;
      },
      error => {
        this.toastr.info(MessageObj.no_isl_available, 'Information');
        this.dataSet = [];
        this.loadingData = false;
      }
    );
  }

  ngAfterViewInit() {
  }

}
