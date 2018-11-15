import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { LoaderService } from 'src/app/common/services/loader.service';
import { IslListService } from 'src/app/common/services/isl-list.service';
import { ToastrService } from 'ngx-toastr';


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
    this.getISLlistService();
  }

  getISLlistService(){
   this.loaderService.show("Loading ISL");
    this.loadingData = true;
    let query = {_:new Date().getTime()};
    this.islListService.getIslList(query).subscribe(
      (data: Array<object>) => {
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
