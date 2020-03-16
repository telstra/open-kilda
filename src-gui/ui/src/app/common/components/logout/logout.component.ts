import { Component, OnInit } from '@angular/core';
import { LoaderService } from '../../services/loader.service';
import { environment } from 'src/environments/environment';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-logout',
  templateUrl: './logout.component.html',
  styleUrls: ['./logout.component.css']
})
export class LogoutComponent implements OnInit {

  constructor(
    private loaderService: LoaderService,
    private toastr: ToastrService
  ) { }

  ngOnInit() {
    this.toastr.clear();
    window.location.href=environment.appEndPoint;
  }

}
