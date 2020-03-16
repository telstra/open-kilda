import { Component, OnInit } from "@angular/core";
import { FormBuilder, FormGroup, Validators, NgForm } from "@angular/forms";
import { FlowsService } from "../../../common/services/flows.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ToastrService } from "ngx-toastr";
import { SwitchService } from "../../../common/services/switch.service";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { LoaderService } from "../../../common/services/loader.service";
import { NgbModal, NgbActiveModal } from "@ng-bootstrap/ng-bootstrap";
import { OtpComponent } from "../../../common/components/otp/otp.component"
import { Location } from "@angular/common";
import { Title } from "@angular/platform-browser";
import { ModalconfirmationComponent } from '../../../common/components/modalconfirmation/modalconfirmation.component';
import { ModalComponent } from '../../../common/components/modal/modal.component';
import { CommonService } from "../../../common/services/common.service";

@Component({
  selector: "app-flow-edit",
  templateUrl: "./flow-edit.component.html",
  styleUrls: ["./flow-edit.component.css"]
})
export class FlowEditComponent implements OnInit {
  flowId: any;
  flowEditForm: FormGroup;
  submitted: boolean = false;
  switches: any = [];
  sourceSwitches: Array<any>;
  targetSwitches: Array<any>;
  enableSearch: Number = 1;
  sourcePorts = [];
  mainSourcePorts = [];
  targetPorts = [];
  mainTargetPorts = [];
  flowDetailData  = {}
  flowDetail: any;
  vlanPorts: Array<any>;
  diverseFlowList:any=[];
  storeLinkSetting = false;
  allocate_protected_path = false;

  constructor(
    private formBuilder: FormBuilder,
    private flowService: FlowsService,
    private router: Router,
    private route: ActivatedRoute,
    private toaster: ToastrService,
    private switchService: SwitchService,
    private switchIdMaskPipe: SwitchidmaskPipe,
    private loaderService: LoaderService,
    private modalService: NgbModal,
    private _location:Location,
    private titleService: Title,
    public commonService: CommonService
  ) {
    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Edit Flow');
    this.flowEditForm = this.formBuilder.group({
      flowid: [""],
      description: [""],
      maximum_bandwidth: [
        "",
        Validators.compose([Validators.required, Validators.pattern("^[0-9]+")])
      ],
      source_switch: [null, Validators.required],
      source_port: [null, Validators.required],
      source_vlan: ["0"],
      target_switch: [null, Validators.required],
      target_port: [null, Validators.required],
      target_vlan: ["0"],
      diverse_flowid:[null],
      allocate_protected_path:[null],
    });

    this.vlanPorts = Array.from({ length: 4095 }, (v, k) => {
      return { label: (k).toString(), value: (k).toString() };
    });
    let flowId: string = this.route.snapshot.paramMap.get("id");
    var filterFlag = localStorage.getItem('filterFlag') || 'controller';

    this.getFlowDetail(flowId,filterFlag);
  }

  ngAfterViewInit() {}
  /** getter to get form fields */
  get f() {
    return this.flowEditForm.controls;
  }

  /**Get flow detail via api call */
  getFlowDetail(flowId,filterFlag) {
    this.loaderService.show("Loading Flow Detail");
    this.flowService.getFlowDetailById(flowId,filterFlag).subscribe(
      flow => {
        this.flowDetailData = flow;
        this.flowDetail = {
          flowid: flow.flowid,
          description: flow.description || "",
          maximum_bandwidth: flow.maximum_bandwidth || 0,
          source_switch: flow.source_switch,
          source_port: flow.src_port.toString(),
          source_vlan: flow.src_vlan.toString(),
          target_switch: flow.target_switch,
          target_port: flow.dst_port.toString(),
          target_vlan: flow.dst_vlan.toString(),
          diverse_flowid:( typeof(flow['diverse_with'])!='undefined' && flow['diverse_with'].length > 0 )? flow['diverse_with'][0] : null,
          allocate_protected_path:flow['allocate_protected_path'] || null,
        };
        this.flowId = flow.flowid;
        this.flowEditForm.setValue(this.flowDetail);

        this.getflowList();
        this.getSwitchList();
        this.getPorts("source_switch" , true);
        this.getPorts("target_switch", true);
      },
      error => {
        var errorMsg =error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No flow found';
       this.toaster.error(errorMsg, "Error");
        this.goToBack();
        this.loaderService.hide();
      }
    );
  }

  /** Get switches list via api call */
  getSwitchList() {
    let ref = this;
    this.loaderService.show("Loading Flow Detail");
    this.switchService.getSwitchList().subscribe(
      response => {
        response.forEach(function(s) {
          ref.switches.push({ label: s.name+' ('+(s.state.toLowerCase())+')', value: s.switch_id });
        });
        ref.targetSwitches = ref.switches;
        ref.sourceSwitches = ref.switches;

        
      },
      error => {
        var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'Unable to fetch switch list';
         this.toaster.error(errorMsg, "Error");
      }
    );
  }

  /** Fetch ports by switch id via api call */
  getPorts(switchType, flag) {
    
    if(this.flowEditForm.controls[switchType].value) {
      let switchId = this.switchIdMaskPipe.transform(
        this.flowEditForm.controls[switchType].value,
        "legacy"
      );
      if(!flag){
        this.loaderService.show("Loading Ports");
      }
      
      this.switchService.getSwitchPortsStats(switchId).subscribe(
        ports => {
          var filteredPorts = ports.filter(function(d){
            return d.assignmenttype !='ISL';
          })
          let sortedPorts = filteredPorts.sort(function(a, b) {
            return a.port_number - b.port_number;
          });
          sortedPorts = sortedPorts.map(portInfo => {
            if (portInfo.port_number == this.flowDetail.source_port) {
              return {
                label: portInfo.port_number,
                value: portInfo.port_number
              };
            }
            return { label: portInfo.port_number, value: portInfo.port_number };
          });
          if (switchType == "source_switch") {
            this.sourcePorts = sortedPorts;
            this.mainSourcePorts = sortedPorts;
            if(!flag){
              this.flowEditForm.controls["source_port"].setValue(null);
              this.flowEditForm.controls["source_vlan"].setValue("0");
            }
    
          } else {
            this.targetPorts = sortedPorts;
            this.mainTargetPorts = sortedPorts;
            if(!flag){
              this.flowEditForm.controls["target_port"].setValue(null);
              this.flowEditForm.controls["target_vlan"].setValue("0");
            }
          }
          
          if(sortedPorts.length == 0){
            this.toaster.info("No Ports available", "Info");
            if(switchType == "source_switch"){ 
              this.flowEditForm.controls["source_port"].setValue(null);
              this.flowEditForm.controls["source_vlan"].setValue("0");
            }else{
              this.flowEditForm.controls["target_port"].setValue(null);
              this.flowEditForm.controls["target_vlan"].setValue("0");
            } 
            
          }

          this.loaderService.hide();
        },
        error => {
          var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'Unable to get port information';
          this.toaster.error(errorMsg, "Error");
          this.loaderService.hide();
        }
      );
    } else {

      if(switchType == "source_switch"){ 
        this.flowEditForm.controls["source_port"].setValue(null);
        this.flowEditForm.controls["source_vlan"].setValue("0");
      }else{
        this.flowEditForm.controls["target_port"].setValue(null);
        this.flowEditForm.controls["target_vlan"].setValue("0");
      } 
    }
  }

  /**Update Flow  */
  updateFlow() {
    this.submitted = true;
    if (this.flowEditForm.invalid) {
      return;
    }

    var flowData = {
      source: {
        "switch-id": this.flowEditForm.controls["source_switch"].value,
        "port-id": this.flowEditForm.controls["source_port"].value,
        "vlan-id": this.flowEditForm.controls["source_vlan"].value
      },
      destination: {
        "switch-id": this.flowEditForm.controls["target_switch"].value,
        "port-id": this.flowEditForm.controls["target_port"].value,
        "vlan-id": this.flowEditForm.controls["target_vlan"].value
      },
      flowid: this.flowEditForm.controls["flowid"].value,
      "maximum-bandwidth": this.flowEditForm.controls["maximum_bandwidth"].value,
       description: this.flowEditForm.controls["description"].value,
      "diverse-flowid":this.flowEditForm.controls["diverse_flowid"].value,
      "allocate_protected_path":this.flowEditForm.controls["allocate_protected_path"].value,
    };

    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to update flow?';
    
    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show("Updating flow");
        this.flowService.updateFlow(this.flowDetail.flowid, flowData).subscribe(
          response => {
            this.toaster.success("Flow updated successfully on controller", "Success!");
            localStorage.removeItem('flows');
            localStorage.removeItem('filterFlag');          
            localStorage.removeItem('flowsinventory'); 
            this.router.navigate(["/flows/details/" + response.flowid]);
            this.loaderService.hide();
          },
          error => {
            this.loaderService.hide();
            var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: "Unable to update";
            this.toaster.error(errorMsg, "Error!");
          }
        );
      }
    });

   
  }

  setProtectedpath(e){
    this.flowEditForm.controls['allocate_protected_path'].setValue(e.target.checked);
    this.allocate_protected_path = e.target.checked;
  }

  /**Delete flow */
  deleteFlow() {

    let is2FaEnabled  = localStorage.getItem('is2FaEnabled')
    var self = this;
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Delete Flow";
    modalReff.componentInstance.content = 'Are you sure you want to perform delete action ?';
    
    modalReff.result.then((response) => {
      if(response && response == true){
        if(is2FaEnabled == 'true'){
          const modalRef = this.modalService.open(OtpComponent);
          modalRef.componentInstance.emitService.subscribe(
            otp => {
              
              if (otp) {
                this.loaderService.show("Deleting Flow");
                this.flowService.deleteFlow(
                  this.flowDetail.flowid,
                  { code: otp },
                  response => {
                    modalRef.close();
                    this.toaster.success("Flow deleted successfully", "Success!");
                    this.loaderService.hide();
                    localStorage.removeItem('flows');
                    this.router.navigate(["/flows"]);
                  },
                  error => {
                    this.loaderService.hide();
                    this.toaster.error(
                      error["error-auxiliary-message"],
                      "Error!"
                    );
                    
                  }
                );
              } else {
                this.toaster.error("Unable to detect OTP", "Error!");
              }
            },
            error => {
            }
          );
        }else{
          const modalRef2 = this.modalService.open(ModalComponent);
          modalRef2.componentInstance.title = "Warning";
          modalRef2.componentInstance.content = 'You are not authorised to delete the flow.';
        }
        
      }
    });
  }
  getflowList(){
    var ref = this;
    let filtersOptions = {controller:true,_:new Date().getTime()};
      this.flowService.getFlowsList(filtersOptions).subscribe((data : Array<object>) =>{
        this.diverseFlowList = data || [];
        if(this.diverseFlowList && this.diverseFlowList.length){
          this.diverseFlowList = this.diverseFlowList.filter(function(d){
              return d.flowid != ref.flowDetail.flowid;
          })
        }
      },error=>{
         this.diverseFlowList = [];  
      });
}
  goToBack(){
    this._location.back();
  }

  getVLAN(type){
    if(type == "source_port"){ 
      this.flowEditForm.controls["source_vlan"].setValue("0");
    }else{
      this.flowEditForm.controls["target_vlan"].setValue("0");
    } 
  }
}
