import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-isl-edit',
  templateUrl: './isl-edit.component.html',
  styleUrls: ['./isl-edit.component.css']
})
export class IslEditComponent implements OnInit {

  constructor(
    private titleService: Title
  ) { }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Edit ISL');
  }

}
