import { Component, OnInit, AfterViewInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators, NgForm } from "@angular/forms";
import { FlowsService } from "../../../common/services/flows.service";
import { ToastrService } from "ngx-toastr";
import { SwitchService } from "../../../common/services/switch.service";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { Select2Data } from "ng-select2-component";
import { Router } from "@angular/router";
import { AlertifyService } from "../../../common/services/alertify.service";
import { LoaderService } from "../../../common/services/loader.service";
import { Location } from "@angular/common";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalconfirmationComponent } from "../../../common/components/modalconfirmation/modalconfirmation.component";
import { CommonService } from 'src/app/common/services/common.service';
import { MessageObj } from 'src/app/common/constants/constants';
import { Message } from '@angular/compiler/src/i18n/i18n_ast';

declare var jQuery: any;

@Component({
  selector: "app-flow-add",
  templateUrl: "./flow-add.component.html",
  styleUrls: ["./flow-add.component.css"]
})
export class FlowAddComponent implements OnInit {
  flowAddForm: FormGroup;
  submitted: boolean = false;
  switches: Select2Data = [];
  sourceSwitches: Select2Data = [];
  targetSwitches: Select2Data = [];
  enableSearch: Number = 1;
  sourcePorts = [];
  mainSourcePorts = [];
  targetPorts = [];
  mainTargetPorts = [];
  flowDetail: any;
  vlanPorts = [];
  diverseFlowList:any=[];
  virtualScrollFlag = true;
  allocate_protected_path:false;
  ignore_bandwidth:false;
  pinned:false;
  periodic_pings:false;

  constructor(
    private formBuilder: FormBuilder,
    private flowService: FlowsService,
    private toaster: ToastrService,
    private switchService: SwitchService,
    private switchIdMaskPipe: SwitchidmaskPipe,
    private router: Router,
    private loaderService: LoaderService,
    private alertService: AlertifyService,
    private _location:Location,
    private modalService: NgbModal,
    private commonService:CommonService,
    private toastr:ToastrService
  ) {
    if(!this.commonService.hasPermission('fw_flow_create')){
      this.toastr.error(MessageObj.unauthorised);  
       this.router.navigate(["/home"]);
      }
  }

  ngOnInit() {
    this.flowAddForm = this.formBuilder.group({
      flowname: [
        "",
        Validators.compose([
          Validators.required,
          Validators.pattern("[a-zA-Z0-9]*")
        ])
      ],
      description: [""],
      maximum_bandwidth: [
        "",
        Validators.compose([Validators.required, Validators.pattern("^[0-9]+")])
      ],
      source_switch:[null, Validators.required],
      source_port: [null, Validators.required],
      source_vlan: ["0"],
      target_switch:[null, Validators.required],
      target_port: [null, Validators.required],
      target_vlan: ["0"],
      diverse_flowid:[null],
      allocate_protected_path:[null],
      ignore_bandwidth:[null],
      pinned:[null],
      periodic_pings:[null],
      max_latency:[""],
      max_latency_tier2:[""]
    });

    this.vlanPorts = Array.from({ length: 4095 }, (v, k) => {
      return { label: k.toString() , value: k.toString()  };
    });
    this.getflowList();
    this.getSwitchList();
  }

 

  /** getter to get form fields */
  get f() {
    return this.flowAddForm.controls;
  }

  getflowList(){
      let filtersOptions = {controller:true,_:new Date().getTime()};
        this.flowService.getFlowsList(filtersOptions).subscribe((data : Array<object>) =>{
          this.diverseFlowList = data.filter((d:any)=>{
            return d.status != 'DOWN';
          }) || [];
        },error=>{
           this.diverseFlowList = [];  
        });
  }

  /** Get switches list via api call */
  getSwitchList() {
    this.loaderService.show("Loading Switches");
    let ref = this;
    this.switchService.getSwitchList().subscribe(
      response => {
        response.forEach(function(s) { 
          ref.switches.push({ label: s.name+' ('+(s.state.toLowerCase())+')', value: s.switch_id });
        });
        ref.targetSwitches = ref.switches;
        ref.sourceSwitches = ref.switches;
        this.loaderService.hide();
      },
      error => {
        var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'Unable to fetch switch list';
       
        this.toaster.error(errorMsg, "Error");
        this.loaderService.hide();

      }
    );
  }

  /** Fetch ports by switch id via api call */
  getPorts(switchType) {
    
    if(this.flowAddForm.controls[switchType].value){
      let switchId = this.switchIdMaskPipe.transform(
        this.flowAddForm.controls[switchType].value,
        "legacy"
      );

      if(switchType == "source_switch"){ 
        this.flowAddForm.controls["source_port"].setValue(null);
        this.flowAddForm.controls["source_vlan"].setValue("0");
      }else{
        this.flowAddForm.controls["target_port"].setValue(null);
        this.flowAddForm.controls["target_vlan"].setValue("0");
      } 

      this.loaderService.show("Loading Ports");
      this.switchService.getSwitchPortsStats(switchId).subscribe(
        ports => {
          var filteredPorts = ports.filter(function(d){
            return d.assignmenttype !='ISL';
          })
          let sortedPorts = filteredPorts.sort(function(a, b) {
            return a.port_number - b.port_number;
          });
          sortedPorts = sortedPorts.map(portInfo => {
            return { label: portInfo.port_number, value: portInfo.port_number };
          });

          if (switchType == "source_switch") {
            this.sourcePorts = sortedPorts;
            this.mainSourcePorts = sortedPorts;
          } else {
            this.targetPorts = sortedPorts;
            this.mainTargetPorts = sortedPorts; 
            
          }
          if(sortedPorts.length == 0){
            this.toaster.info(MessageObj.no_ports, "Info");
          }
          this.loaderService.hide();
        },
        error => {
          var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'Unable to get port information';
          this.toaster.error(errorMsg, "Error");
          this.loaderService.hide();
        }
      );
    }else{
      if(switchType == "source_switch"){ 
        this.flowAddForm.controls["source_port"].setValue(null);
        this.flowAddForm.controls["source_vlan"].setValue("0");
      }else{
        this.flowAddForm.controls["target_port"].setValue(null);
        this.flowAddForm.controls["target_vlan"].setValue("0");
      } 
    }
  }

  addFlow() {
    this.submitted = true;
    if (this.flowAddForm.invalid) {
      return;
    }

    var flowData = {
      source: {
        "switch_id": this.flowAddForm.controls["source_switch"].value,
        "port_number": this.flowAddForm.controls["source_port"].value,
        "vlan_id": this.flowAddForm.controls["source_vlan"].value,
        "inner_vlan_id":0,
      },
      destination: {
        "switch_id": this.flowAddForm.controls["target_switch"].value,
        "port_number": this.flowAddForm.controls["target_port"].value,
        "vlan_id": this.flowAddForm.controls["target_vlan"].value,
        "inner_vlan_id":0,
      },
      "flow_id": this.flowAddForm.controls["flowname"].value,
      "maximum_bandwidth": this.flowAddForm.controls["maximum_bandwidth"].value,
      "description": this.flowAddForm.controls["description"].value,
      "diverse_flow_id": this.flowAddForm.controls["diverse_flowid"].value || null,
      "allocate_protected_path": this.flowAddForm.controls["allocate_protected_path"].value || null,
      'ignore_bandwidth':this.flowAddForm.controls['ignore_bandwidth'].value || null,
      "pinned":this.flowAddForm.controls['pinned'].value || null,
      "periodic_pings":this.flowAddForm.controls['periodic_pings'].value || null,
      "max_latency":this.flowAddForm.controls['max_latency'].value || 0,
      "max_latency_tier2":this.flowAddForm.controls['max_latency_tier2'].value || 0
    };
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to create a new flow ?';
    modalReff.result.then((response) => {
      if(response && response == true){
        this.loaderService.show("Adding Flow");
        this.flowService.createFlow(flowData).subscribe(
          response => {
            this.toaster.success(MessageObj.flow_created, "Success!");
            localStorage.removeItem('flows');
            localStorage.removeItem('filterFlag');          
            localStorage.removeItem('flowsinventory'); 
            this.router.navigate(["/flows/details/" + response.flow_id]);
            this.loaderService.hide();
          },
          error => {
            if(error.error) {
             var errorMsg = error && error.error && error.error['error-description'] ? error.error['error-description'] : (error && error.error && error.error['error-description']) ? error.error['error-auxiliary-message']: "Unable to update";
             this.toaster.error(
                errorMsg,
                "Error!"
              );
            }else{
              this.toaster.error(MessageObj.flow_not_created,
                "Error!"
              );
            }
           
            this.loaderService.hide();
          }
        );
      }
    });

  }
  setProtectedpath(e){
    this.flowAddForm.controls['allocate_protected_path'].setValue(e.target.checked);
    this.allocate_protected_path = e.target.checked;
  }
  goToBack(){
    this._location.back();
  }


  getVLAN(type){
    if(type == "source_port"){ 
      this.flowAddForm.controls["source_vlan"].setValue("0");
    }else{
      this.flowAddForm.controls["target_vlan"].setValue("0");
    } 
  }
  

}
