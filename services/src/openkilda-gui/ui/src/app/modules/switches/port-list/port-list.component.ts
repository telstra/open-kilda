import { Component, OnInit, AfterViewInit, OnDestroy, ViewChild } from '@angular/core';
import { SwitchService } from '../../../common/services/switch.service';
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ToastrService } from 'ngx-toastr';
import { Router } from '@angular/router';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from '@angular/platform-browser';
import { CommonService } from 'src/app/common/services/common.service';

@Component({
  selector: 'app-port-list',
  templateUrl: './port-list.component.html',
  styleUrls: ['./port-list.component.css']
})
export class PortListComponent implements OnInit, AfterViewInit, OnDestroy {
  @ViewChild(DataTableDirective)
  dtElement: DataTableDirective;
  dtOptions: any = {};
  dtTrigger: Subject<any> = new Subject();

  currentActivatedRoute : string;
  switch_id: string;	
  switchPortDataSet: any;
  anyData: any;
  portListTimerId: any;
  constructor(private switchService:SwitchService,
    private toastr: ToastrService,
    private maskPipe: SwitchidmaskPipe,
    private router:Router,
    private loaderService: LoaderService,
    private titleService: Title,
    private commonService:CommonService,
  ) {}

  ngOnInit() {
    this.loaderService.show("Loading Port Details");
    //this.titleService.setTitle('OPEN KILDA - Ports');
  	let retrievedSwitchObject = JSON.parse(localStorage.getItem('switchDetailsJSON'));
    this.switch_id =retrievedSwitchObject.switch_id;
    this.dtOptions = {
      paging: false,
      retrieve: true,
      autoWidth: false,
      colResize: false,
      dom: 'tpl',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      "aoColumns": [
        { sWidth: '5%' },
        { sWidth:  '7%' },
        { sWidth: '14%' },
        { sWidth: '14%' },
        { sWidth: '14%' },
        { sWidth: '14%' },
        { sWidth: '7%' },
        { sWidth: '10%' },
        { sWidth: '7%' },
        { sWidth: '8%' } ],
      language: {
        searchPlaceholder: "Search"
        }
    }
    this.portListData();
  	this.getSwitchPortList()
  }
  
  fulltextSearch(e:any){ 
      var value = e.target.value;
        this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
          dtInstance.search(value)
                  .draw();
        });
  }

  showPortDetail(item){
     var portDataObject = item;
     // var keyName = "port_"+this.maskPipe.transform(this.switch_id,'legacy')+"_"+item.port_number;
     // console.log("setKeyName"+keyName);
    localStorage.setItem('portDataObject', JSON.stringify(portDataObject));
    this.currentActivatedRoute = 'port-details';
    this.router.navigate(['/switches/details/'+this.switch_id+'/port/'+item.port_number]);

  }

    getSwitchPortList(){
      this.portListTimerId = setInterval(() => {
        this.portListData();
      }, 30000);
  }

  portListData(){
      
      this.switchService.getSwitchPortsStats(this.maskPipe.transform(this.switch_id,'legacy')).subscribe((data : Array<object>) =>{
        this.loaderService.hide(); 
        this.rerender();
        this.ngAfterViewInit();
        localStorage.setItem('switchPortDetail', JSON.stringify(data));
        this.switchPortDataSet = data;
       
        for(let i = 0; i<this.switchPortDataSet.length; i++){
          if(this.switchPortDataSet[i].port_number === '' || this.switchPortDataSet[i].port_number === undefined){
              this.switchPortDataSet[i].port_number = '-';
          }
          if(this.switchPortDataSet[i].interfacetype === '' || this.switchPortDataSet[i].interfacetype === undefined){
              this.switchPortDataSet[i].interfacetype = '-';
          }

          if(this.switchPortDataSet[i].stats['tx-bytes'] === '' || this.switchPortDataSet[i].stats['tx-bytes'] === undefined){
              this.switchPortDataSet[i].stats['tx-bytes'] = '-';
          }
          else{
              this.switchPortDataSet[i].stats['tx-bytes'] =  this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['tx-bytes']);;
          }


          if(this.switchPortDataSet[i].stats['rx-bytes'] === '' || this.switchPortDataSet[i].stats['rx-bytes'] === undefined){
              this.switchPortDataSet[i].stats['rx-bytes'] = '-';
          }
          else{
              this.switchPortDataSet[i].stats['rx-bytes'] =  this.commonService.convertBytesToMbps(this.switchPortDataSet[i].stats['rx-bytes']);
          }

          if(this.switchPortDataSet[i].stats['tx-packets'] === '' || this.switchPortDataSet[i].stats['tx-packets'] === undefined){
              this.switchPortDataSet[i].stats['tx-packets']= '-';
          }

          if(this.switchPortDataSet[i].stats['rx-packets'] === '' || this.switchPortDataSet[i].stats['rx-packets'] === undefined){
              this.switchPortDataSet[i].stats['rx-packets']= '-';
          }

          if(this.switchPortDataSet[i].stats['tx-dropped'] === '' || this.switchPortDataSet[i].stats['tx-dropped'] === undefined){
              this.switchPortDataSet[i].stats['tx-dropped']= '-';
          }

          if(this.switchPortDataSet[i].stats['rx-dropped'] === '' || this.switchPortDataSet[i].stats['rx-dropped'] === undefined){
              this.switchPortDataSet[i].stats['rx-dropped']= '-';
          }


          if(this.switchPortDataSet[i].stats['tx-errors'] === '' || this.switchPortDataSet[i].stats['tx-errors'] === undefined){
              this.switchPortDataSet[i].stats['tx-errors']= '-';
          }

          if(this.switchPortDataSet[i].stats['rx-errors'] === '' || this.switchPortDataSet[i].stats['rx-errors'] === undefined){
              this.switchPortDataSet[i].stats['rx-errors']= '-';
          }

          if(this.switchPortDataSet[i].stats['collisions'] === '' || this.switchPortDataSet[i].stats['collisions'] === undefined){
              this.switchPortDataSet[i].stats['collisions']= '-';
          }

            if(this.switchPortDataSet[i].stats['rx-frame-error'] === '' || this.switchPortDataSet[i].stats['rx-frame-error'] === undefined){
              this.switchPortDataSet[i].stats['rx-frame-error']= '-';
          }

          if(this.switchPortDataSet[i].stats['rx-over-error'] === '' || this.switchPortDataSet[i].stats['rx-over-error'] === undefined){
              this.switchPortDataSet[i].stats['rx-over-error']= '-';
          }

          if(this.switchPortDataSet[i].stats['rx-crc-error'] === '' || this.switchPortDataSet[i].stats['rx-crc-error'] === undefined){
              this.switchPortDataSet[i].stats['rx-crc-error']= '-';
          }
      }

     },error=>{
      this.loaderService.hide(); 
      this.toastr.error("No Switch Port data",'Error');
     });
  }

   ngAfterViewInit(): void {
       try{
        this.dtTrigger.next();
       }catch(err){

       }

    

  }

  ngOnDestroy(): void {
    // Unsubscribe the event
    this.dtTrigger.unsubscribe();
    clearInterval(this.portListTimerId);
  }

  rerender(): void {
      try{
    this.dtElement.dtInstance.then((dtInstance: DataTables.Api) => {
      // Destroy the table first
      try{
        dtInstance.destroy();
        this.dtTrigger.next();
      }catch(err){}
    });
    }catch(err){}
  }

}

