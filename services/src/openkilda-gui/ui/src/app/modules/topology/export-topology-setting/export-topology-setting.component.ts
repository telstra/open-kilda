import { Component, OnInit } from '@angular/core';
import { TopologyService } from 'src/app/common/services/topology.service';
import { DomSanitizer } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ClipboardService } from 'ngx-clipboard';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-export-topology-setting',
  templateUrl: './export-topology-setting.component.html',
  styleUrls: ['./export-topology-setting.component.css']
})
export class ExportTopologySettingComponent implements OnInit {
  settings: any;
  downloadCoordinatesHref:any;
  viewJson:boolean=false;
  constructor(private topologyService:TopologyService,
    private sanitizer:DomSanitizer,
    public activeModal:NgbActiveModal,
    private clipboardService:ClipboardService,
    private toastr:ToastrService) { }

  ngOnInit() {
    this.settings = this.topologyService.getCoordinates();
    var theJSON = JSON.stringify(this.settings);
    var uri = this.sanitizer.bypassSecurityTrustUrl("data:text/json;charset=UTF-8," + encodeURIComponent(theJSON));
    this.downloadCoordinatesHref = uri;
  }

  enableViewJson(){
    this.viewJson = true;
  }

  copyToClip() {
    let dataToCopy  = JSON.stringify(this.settings);
    this.clipboardService.copyFromContent(dataToCopy);
    this.toastr.success('Copied');
  }

}
