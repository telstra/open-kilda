import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { LoaderService } from 'src/app/common/services/loader.service';
import { IslListService } from 'src/app/common/services/isl-list.service';
import { ToastrService } from 'ngx-toastr';
import { timeDay } from 'd3';


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
    private loaderService:LoaderService,
    private islListService : IslListService,
    private toastr : ToastrService
  ) { }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - ISL');
    var islListData = JSON.parse(localStorage.getItem("ISL_LIST"));
    if(islListData){
      var storageTime = islListData.timeStamp;
      var startTime = new Date(storageTime).getTime();
      var lastTime = new Date().getTime();
      let timeminDiff = lastTime - startTime;
      var diffMins = Math.round(((timeminDiff % 86400000) % 3600000) / 60000);;
      var islList = islListData.list_data;
      if(islList && diffMins < 5){
        this.dataSet = islList;
        this.loadingData = false;
      }else{
        this.getISLlistService();
      }
    }else{
      this.getISLlistService();
    }
    
  }

  getISLlistService(){
   this.loaderService.show("Loading ISL");
    this.loadingData = true;
    let query = {_:new Date().getTime()};
    this.islListService.getIslList(query).subscribe(
      (data: Array<object>) => {
        var islListData = JSON.stringify({'timeStamp':new Date().getTime(),"list_data":data});
        localStorage.setItem('ISL_LIST',islListData);
        if (!data || data.length == 0) {
          this.toastr.info("No ISL Available", "Information");
          this.dataSet = [];
        } else {
          this.dataSet = data;
        }
        this.loadingData = false;
      },
      error => {
        this.toastr.info("No ISL Available", "Information");
        this.dataSet = [];
        this.loadingData = false;
      }
    );
  }

  ngAfterViewInit(){
  }

}
