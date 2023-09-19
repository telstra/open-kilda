import {
  Component,
  OnInit,
  Output,
  EventEmitter,
  ElementRef,
  ViewChild
} from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';

@Component({
  selector: 'app-otp',
  templateUrl: './otp.component.html',
  styleUrls: ['./otp.component.css']
})
export class OtpComponent implements OnInit {
  otpForm: FormGroup;
  submitted = false;
  @Output()
  emitService = new EventEmitter();
  @ViewChild('otpcontainer', { static: true })
  otpContainerElement: ElementRef;

  constructor(
    public activeModal: NgbActiveModal,
    private formBuilder: FormBuilder
  ) {}

  ngOnInit() {
    this.otpForm = this.formBuilder.group({
      input1: ['', Validators.required],
      input2: ['', Validators.required],
      input3: ['', Validators.required],
      input4: ['', Validators.required],
      input5: ['', Validators.required],
      input6: ['', Validators.required]
    });

    this.focusNextInput();
  }

  get f() {
    return this.otpForm.controls;
  }

  submittOtp() {
    this.submitted = true;

    if (this.otpForm.invalid) {
      return false;
    }

    const valueObject = this.otpForm.value;
    let otpCode = '';
    for (const input in valueObject) {
      otpCode += valueObject[input];
    }
    this.emitService.emit(otpCode);
  }

  /** Event Binding */
  focusNextInput() {
    const container = this.otpContainerElement.nativeElement;
    const invalidElements = container.querySelectorAll('input');
    if (invalidElements.length > 0) {
      invalidElements[0].focus();
    }

    container.onkeyup = function(e) {
      const target = e.srcElement || e.target;
      const maxLength = parseInt(target.attributes['maxlength'].value, 10);
      const myLength = target.value.length;
      if (myLength >= maxLength) {
        let next = target;
        while ((next = next.nextElementSibling)) {
          if (next == null) { break; }
          if (next.tagName.toLowerCase() === 'input') {
            next.focus();
            break;
          }
        }
      } else if (myLength === 0) {
        let previous = target;
        while ((previous = previous.previousElementSibling)) {
          if (previous == null) { break; }
          if (previous.tagName.toLowerCase() === 'input') {
            previous.focus();
            break;
          }
        }
      }
    };
  }

  validateOtpInput(evt) {
    const theEvent = evt || window.event;
    let key = theEvent.keyCode || theEvent.which;
    key = String.fromCharCode(key);
    const regex = /[0-9]|\./;

    if (theEvent.keyCode == 13) {
      this.submittOtp();
      if (theEvent.stopPropagation) { theEvent.stopPropagation(); }
      return;
    }

    if (!regex.test(key)) {
      theEvent.returnValue = false;
      if (theEvent.preventDefault) { theEvent.preventDefault(); }
    }


  }
}
