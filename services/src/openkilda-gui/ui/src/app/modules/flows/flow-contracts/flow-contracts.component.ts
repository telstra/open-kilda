import { Component, OnInit, Renderer2,ViewChild, OnChanges, SimpleChanges, AfterViewInit, Input } from '@angular/core';
import { LoaderService } from 'src/app/common/services/loader.service';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { FlowsService } from 'src/app/common/services/flows.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ModalconfirmationComponent } from 'src/app/common/components/modalconfirmation/modalconfirmation.component';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-flow-contracts',
  templateUrl: './flow-contracts.component.html',
  styleUrls: ['./flow-contracts.component.css']
})
export class FlowContractsComponent implements OnInit,OnChanges, AfterViewInit {
  @ViewChild(DataTableDirective) datatableElement: DataTableDirective;
  dtOptions = {};
  dtTrigger: Subject<any> = new Subject();
  @Input() data = [];
  @Input() flowId;

  expandedId : boolean = false;
  expandedStatus : boolean = false;
  expandedBandwidth : boolean = false;
  expandedStart : boolean = false;
  expandedExpiry : boolean = false;
  expandedRenewal : boolean = false;
  expandedPrice : boolean = false;

  wrapperHide = false;

  

  constructor(private renderer: Renderer2,private loaderService: LoaderService,private flowService:FlowsService,
    private modalService: NgbModal,private toaster:ToastrService) { }

  ngOnInit() {
    let ref = this;
    this.dtOptions = {
      pageLength: 10,
      deferRender: true,
      dom: 'tpl',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      retrieve: true,
      autoWidth: false,
      colResize: false,
      stateSave: false,
      language: {
        searchPlaceholder: "Search"
      },
      "aoColumns": [
        { sWidth: '13%' },
        { sWidth: '9%' },
        { sWidth: '12%' },
        { sWidth: '15%' },
        { sWidth: '15%'},
        { sWidth: '13%' },
        { sWidth: '8%' },
        { sWidth: '10%', "bSortable": false },
        ],
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.wrapperHide = true;
        },ref.data.length/2);
      }
    }

  }

  ngOnChanges(change: SimpleChanges){
    if(change.data){
      if(change.data.currentValue){
        this.data  = change.data.currentValue;
      }
    }
  }

  fulltextSearch(e:any){ 
    var value = e.target.value;
      this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
        dtInstance.search(value)
                .draw();
      });
  }

  ngAfterViewInit(){
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () {
        const that = this;
        $('input', this.header()).on('keyup change', function () {
          if (that.search() !== this['value']) {
            that
              .search(this['value'])
              .draw();
          }
        });
      });
    });
  }
  deleteContract(contractid){
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    var self = this;
    modalReff.componentInstance.title = "Delete Contract";
    modalReff.componentInstance.content = 'Are you sure you want to perform delete action ?';
    modalReff.result.then((response) => {
      if(response && response == true){
        self.loaderService.show('Deleting contract')
        this.flowService.deletecontract(this.flowId,contractid).subscribe((response:any) =>{
          self.loaderService.hide();
          self.toaster.success('Contract deleted successfully.');
        },function(err){
          self.loaderService.hide();
          var Err = err.error;
          var msg  = (Err && typeof(Err['error-auxiliary-message'])!='undefined') ? Err['error-auxiliary-message']:'';
          self.toaster.error(msg,"Error");
        })
      }

    });
   
  }
  
  toggleSearch(e,inputContainer){ 
    this[inputContainer] = this[inputContainer] ? false : true;
    if(this[inputContainer]){
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).focus();
      });
    }else{
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).value = "";
        jQuery('#'+inputContainer).trigger('change');
      });
    }
    event.stopPropagation();
  }

  stopPropagationmethod(e){
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
   }
  }

}
