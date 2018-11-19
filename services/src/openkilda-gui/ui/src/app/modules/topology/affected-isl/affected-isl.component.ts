import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-affected-isl',
  templateUrl: './affected-isl.component.html',
  styleUrls: ['./affected-isl.component.css']
})
export class AffectedIslComponent implements OnInit {
  openedTab :string = 'failed';
  constructor(public activeModal: NgbActiveModal) { }
  ngOnInit() { }
   toggleTab(tab){
  	if(tab === 'failed'){
  		this.openedTab = 'failed';
  	}
  	else{
  		this.openedTab = 'unidirectional';
  	}
  }
}
