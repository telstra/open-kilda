import { Component, OnInit } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { ToastrService } from 'ngx-toastr';
import { ClipboardService } from "ngx-clipboard";
import { LoaderService } from "../../../common/services/loader.service";
import { CommonService } from '../../../common/services/common.service';

@Component({
  selector: 'app-switch-meters',
  templateUrl: './switch-meters.component.html',
  styleUrls: ['./switch-meters.component.css']
})
export class SwitchMetersComponent implements OnInit {

  switchedMeters : any;
  switch_id: string;
  showMetersJSON: boolean = true;
  jsonViewer = true;
  tabularViewer = false;
  property = 'jsonViewer';

  loading = false;
  clipBoardItems :any= {
    switchMeterVal:""
  }
	constructor( private switchService:SwitchService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private clipboardService: ClipboardService,
    public commonService: CommonService
  ) {}

  ngOnInit() {
      let retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
      this.switch_id =retrievedSwitchObject.switch_id;
      this.tabularViewer = false;
      this.switchMeters();
  }

  switchMeters() {
    this.loading = true;
    if( this.property == 'tabularViewer'){
      this.tabularViewer = true;
    this.jsonViewer = false;
    }else{
      this.tabularViewer = false;
    this.jsonViewer = true;
    }
    
    this.switchService.getSwitchMetersList(this.switch_id).subscribe(
      data => {
        this.switchedMeters = data;
        this.clipBoardItems.switchMeterVal = data;
        this.showMetersJSON = false;
        this.loading = false;
      },
      error => {
        this.loading = false;
        this.toastr.error(error['error'] && error['error']["error-auxiliary-message"] ? error['error']["error-auxiliary-message"] : 'Error in api response' , "Error!");
      }
    );
  }

   

  showMenu(e){
    e.preventDefault();
    $('.clip-board-button').hide();
    $('.clip-board-button').css({
      top: e.pageY+'px',
         left: (e.pageX-220)+'px',
         "z-index":2,
     }).toggle();
     
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  copyToClipHtml(event, copyHtmlItem){
    this.clipboardService.copyFromContent(jQuery('.code').text());
  }

  toggleView(e){
   
    if (e.target.checked) {
      this.property = 'tabularViewer';
      this.jsonViewer = false ;
    } else {
      this.property = 'jsonViewer';   
      this.tabularViewer = false;   
   }
    this[this.property] = true;
  }

}
