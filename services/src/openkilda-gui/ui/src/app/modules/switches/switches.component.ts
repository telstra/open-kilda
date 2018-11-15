import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-switches',
  templateUrl: './switches.component.html',
  styleUrls: ['./switches.component.css']
})
export class SwitchesComponent implements OnInit {
  openedTab = 'search';
  constructor() { }

  ngOnInit() {
  }
  openTab(tab){
    this.openedTab = tab;
  }
}
