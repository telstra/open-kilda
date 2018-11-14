import { Component, OnInit, Input,ViewChild, OnChanges, OnDestroy, AfterViewInit, SimpleChanges, Renderer2 } from '@angular/core';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { LoaderService } from 'src/app/common/services/loader.service';
import { Router } from '@angular/router';
import { Switch } from 'src/app/common/data-models/switch';

@Component({
  selector: 'app-switch-datatable',
  templateUrl: './switch-datatable.component.html',
  styleUrls: ['./switch-datatable.component.css']
})
export class SwitchDatatableComponent implements OnInit, OnChanges,OnDestroy,AfterViewInit {

  @ViewChild(DataTableDirective)  datatableElement: DataTableDirective;
  @Input() data =[];
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();
  wrapperHide = false;

  switch_id : boolean = false;
  name : boolean = false;
  address : boolean = false;
  hostname : boolean = false;
  description : boolean = false;
  state : boolean = false;

  constructor(private loaderService : LoaderService,
    private renderer: Renderer2, private router:Router) { 

  }

  ngOnInit() {
    this.wrapperHide = false;
    let ref = this;
    this.dtOptions = {
      pageLength: 10,
      retrieve: true,
      autoWidth: false,
      colResize:false,
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      "responsive": true,
      "aoColumns": [
        { sWidth: '15%' },
        { sWidth: '15%',"sType": "name","bSortable": true },
        { sWidth: '15%' },
        { sWidth: '15%' },
        { sWidth: '30%' },
        { sWidth: '10%' }],
      language: {
        searchPlaceholder: "Search"
      },
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },this.data.length/2);
      }
    };

  }

  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function() {
        const that = this;
        $("input", this.header()).on("keyup change", function() {
          if (that.search() !== this["value"]) {
            that.search(this["value"]).draw();
          }
        });
      });
    });
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

  ngOnChanges(change:SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }
  }

  showSwitch(switchObj: Switch) {
    var switchDetailsJSON = {
      switch_id: switchObj.switch_id,
      name: switchObj.name,
      address: switchObj.address,
      hostname: switchObj.hostname,
      description: switchObj.description,
      state: switchObj.state
    };

    localStorage.setItem(
      "switchDetailsJSON",
      JSON.stringify(switchDetailsJSON)
    );
    this.router.navigate(["/switches/details/" + switchObj.switch_id]);
  }

  checkValue(value) {
    if (value === "" || value == undefined) {
      return "-";
    } else {
      return value;
    }
  }

  toggleSearch(e, inputContainer) {
    event.stopPropagation();
    this[inputContainer] = this[inputContainer] ? false : true;
    if (this[inputContainer]) {
      setTimeout(() => {
        this.renderer.selectRootElement("#" + inputContainer).focus();
      });
    }else{
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).value = "";
        jQuery('#'+inputContainer).trigger('change');
      });
    }
  }

  stopPropagationmethod(e) {
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
    }
  }



}
