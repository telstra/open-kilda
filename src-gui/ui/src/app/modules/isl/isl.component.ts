import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';


@Component({
  selector: 'app-isl',
  templateUrl: './isl.component.html',
  styleUrls: ['./isl.component.css']
})
export class IslComponent implements OnInit {

  page: string = 'list';	
  constructor(
    private titleService: Title
  ) { }

  ngOnInit() {
  }
  openPage(page){
    this.titleService.setTitle('OPEN KILDA - ISL');
    this.page = page;
  }

}
