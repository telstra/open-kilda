import { Component, OnInit, OnChanges, SimpleChanges, Input } from '@angular/core';

@Component({
  selector: 'app-switch-meters-table',
  templateUrl: './switch-meters-table.component.html',
  styleUrls: ['./switch-meters-table.component.css']
})
export class SwitchMetersTableComponent implements OnInit, OnChanges {
  @Input() data = [];
  constructor() { }

  ngOnInit() {
  }

  ngOnChanges(change:SimpleChanges){
    if(change.data && change.data.currentValue){
      this.data = change.data.currentValue;
    }
  }

}
