import { Component, OnInit,ViewChild,Input,OnChanges, Renderer2, SimpleChanges } from '@angular/core';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { LoaderService } from 'src/app/common/services/loader.service';
@Component({
  selector: 'app-useractivity-list',
  templateUrl: './useractivity-list.component.html',
  styleUrls: ['./useractivity-list.component.css']
})
export class UseractivityListComponent implements OnInit,OnChanges {

  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  @Input() data=[];
  dtOptions = {};  
  wrapperHide=true;
  dtTrigger: Subject<any> = new Subject();
  constructor(
    private loaderService:LoaderService,
    private renderer:Renderer2
  ) { 
    this.wrapperHide=false;
  }

  ngOnInit() {
    var ref = this;
    this.dtOptions = {
      pageLength: 10,
      deferRender: true,
      info:true,
      dom: 'tpli',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      "order": [],
      language: {
        searchPlaceholder: "Search"
      },
      drawCallback:function(){
        if(jQuery('#flowDataTable tbody tr').length < 10){
          jQuery('#flowDataTable_next').addClass('disabled');
        }else{
          jQuery('#flowDataTable_next').removeClass('disabled');
        }
      },
      "aoColumns": [
        { sWidth: '15%' ,"sType": "name","bSortable": true},
        { sWidth: '15%' },
        { sWidth: '15%' },
        { sWidth: '15%' },
        { sWidth: '20%' ,"bSortable": false},
       
       ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }
  }

 

  ngOnChanges(change:SimpleChanges){
    var ref = this;
    if( typeof(change.data)!='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
        this.dtTrigger.next();
      }
    }
  }
  

  ngAfterViewInit(){
    this.dtTrigger.next();
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

}
