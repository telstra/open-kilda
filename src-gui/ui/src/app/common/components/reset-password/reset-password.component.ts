import { Component, OnInit } from "@angular/core";
import { NgbActiveModal, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ClipboardService } from "ngx-clipboard";
import { ToastrService } from "ngx-toastr";

@Component({
  selector: "app-reset-password",
  templateUrl: "./reset-password.component.html",
  styleUrls: ["./reset-password.component.css"]
})
export class ResetPasswordComponent implements OnInit {
  title: any;
  content: any;
  constructor(
    public activeModal: NgbActiveModal,
    private clipboardService: ClipboardService,
    private toaster: ToastrService
  ) {}
 
  ngOnInit() {}

  copyPassword(content) {
    this.clipboardService.copyFromContent(content);
    this.toaster.clear();
    this.toaster.success("Password Copied");
  }
}
