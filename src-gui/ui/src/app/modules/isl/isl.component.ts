import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';


@Component({
  selector: 'app-isl',
  templateUrl: './isl.component.html',
  styleUrls: ['./isl.component.css']
})
export class IslComponent implements OnInit {

  page: string = 'list';	
  constructor(
    private titleService: Title,
    private commonService:CommonService,
    private toastr:ToastrService,
    private router:Router
  ) {
    if(!this.commonService.hasPermission('menu_isl')){
      this.toastr.error('You are not authorised to access this page.');  
       this.router.navigate(["/home"]);
      }
   }

  ngOnInit() {
  }
  openPage(page){
    this.titleService.setTitle('OPEN KILDA - ISL');
    this.page = page;
  }

}
