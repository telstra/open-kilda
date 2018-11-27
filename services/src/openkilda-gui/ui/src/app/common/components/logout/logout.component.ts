import { Component, OnInit } from '@angular/core';
import { LoaderService } from '../../services/loader.service';

@Component({
  selector: 'app-logout',
  templateUrl: './logout.component.html',
  styleUrls: ['./logout.component.css']
})
export class LogoutComponent implements OnInit {

  constructor(
    private loaderService: LoaderService
  ) { }

  ngOnInit() {
    this.loaderService.show("Logout");
    setTimeout(() => {
      let link = `/openkilda/logout`;
      window.open(link, '_self');
      localStorage.clear();
    }, 2000);
  }

}
