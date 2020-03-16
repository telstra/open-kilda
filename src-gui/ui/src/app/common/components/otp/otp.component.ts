import {
  Component,
  OnInit,
  Output,
  EventEmitter,
  ElementRef,
  ViewChild
} from "@angular/core";
import { NgbActiveModal } from "@ng-bootstrap/ng-bootstrap";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";

@Component({
  selector: "app-otp",
  templateUrl: "./otp.component.html",
  styleUrls: ["./otp.component.css"]
})
export class OtpComponent implements OnInit {
  otpForm: FormGroup;
  submitted = false;
  @Output()
  emitService = new EventEmitter();
  @ViewChild("otpcontainer")
  otpContainerElement: ElementRef;

  constructor(
    public activeModal: NgbActiveModal,
    private formBuilder: FormBuilder
  ) {}

  ngOnInit() {
    this.otpForm = this.formBuilder.group({
      input1: ["", Validators.required],
      input2: ["", Validators.required],
      input3: ["", Validators.required],
      input4: ["", Validators.required],
      input5: ["", Validators.required],
      input6: ["", Validators.required]
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

    let valueObject = this.otpForm.value;
    let otpCode = "";
    for (let input in valueObject) {
      otpCode += valueObject[input];
    }
    this.emitService.emit(otpCode);
  }

  /** Event Binding */
  focusNextInput() {
    var container = this.otpContainerElement.nativeElement;
    let invalidElements = container.querySelectorAll('input');
    if (invalidElements.length > 0) {
      invalidElements[0].focus();
    }
    
    container.onkeyup = function(e) {
      var target = e.srcElement || e.target;
      var maxLength = parseInt(target.attributes["maxlength"].value, 10);
      var myLength = target.value.length;
      if (myLength >= maxLength) {
        var next = target;
        while ((next = next.nextElementSibling)) {
          if (next == null) break;
          if (next.tagName.toLowerCase() === "input") {
            next.focus();
            break;
          }
        }
      }
      // Move to previous field if empty (user pressed backspace)
      else if (myLength === 0) {
        var previous = target;
        while ((previous = previous.previousElementSibling)) {
          if (previous == null) break;
          if (previous.tagName.toLowerCase() === "input") {
            previous.focus();
            break;
          }
        }
      }
    };
  }

  validateOtpInput(evt) {
    var theEvent = evt || window.event;
    var key = theEvent.keyCode || theEvent.which;
    key = String.fromCharCode(key);
    var regex = /[0-9]|\./;

    if(theEvent.keyCode == 13){
      this.submittOtp();
      if (theEvent.stopPropagation) theEvent.stopPropagation();
      return;
    }

    if (!regex.test(key)) {
      theEvent.returnValue = false;
      if (theEvent.preventDefault) theEvent.preventDefault();
    }

   
  }
}
