import { Component, OnInit } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { ToastrService } from 'ngx-toastr';
import { NgxSpinnerService } from "ngx-spinner";
import { ClipboardService } from "ngx-clipboard";
import { LoaderService } from "../../../common/services/loader.service";
import { CommonService } from '../../../common/services/common.service';
import {ActivatedRoute, Router} from '@angular/router';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-rule-details',
  templateUrl: './rule-details.component.html',
  styleUrls: ['./rule-details.component.css']
})
export class RuleDetailsComponent implements OnInit {
	switchedRules : any;
  switch_id: string;
  showRulesJSON: boolean = true;
  loading = false;
  clipBoardItems :any= {
    
    switchRulesVal:""
  }
	constructor( private switchService:SwitchService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private clipboardService: ClipboardService,
    private router:Router,
    private route: ActivatedRoute,
    public commonService: CommonService
  ) {
    if(!this.commonService.hasPermission('menu_switches')){
      this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }
  }

    ngOnInit() {
        let switchDetailsKey = 'switchDetailsKey_';
        this.route.params.subscribe(params => {
            const id = params['id'];
            switchDetailsKey = switchDetailsKey + id;
        });
        const retrievedSwitchObject = JSON.parse(localStorage.getItem(switchDetailsKey));
        this.switch_id = retrievedSwitchObject.switch_id;
        this.switchRules();
    }

  switchRules() {
    this.loading = true;
    this.switchService.getSwitchRulesList(this.switch_id).subscribe(
      data => {
        this.switchedRules = this.commonService.convertNumberToString(data);
        this.clipBoardItems.switchRulesVal = this.switchedRules;
        this.showRulesJSON = false;
        this.loading = false;
      },
      error => {
        this.loading = false;
        this.toastr.error(error["error-auxiliary-message"], "Error!");
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

}
