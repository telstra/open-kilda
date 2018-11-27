import { Component, OnInit } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-modalconfirmation',
  templateUrl: './modalconfirmation.component.html',
  styleUrls: ['./modalconfirmation.component.css']
})
export class ModalconfirmationComponent implements OnInit {

  title: any;
  content: any;

  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit() {
  }

}
