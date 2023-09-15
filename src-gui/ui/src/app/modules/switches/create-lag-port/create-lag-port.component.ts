import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {FormBuilder, FormGroup, Validators} from '@angular/forms';
import {NgbActiveModal} from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'app-create-lag-port',
    templateUrl: './create-lag-port.component.html',
    styleUrls: ['./create-lag-port.component.css']
})
export class CreateLagPortComponent implements OnInit {
    createLogPortForm: FormGroup;
    data: any;
    createLagPortModel: CreateLagPortModel;
    @Output() emitService = new EventEmitter();
    submitted = false;

    constructor(public activeModal: NgbActiveModal, public formBuilder: FormBuilder) {
    }

    ngOnInit() {
        const result = JSON.parse(localStorage.getItem('switchPortDetail'));
        this.data = result.filter(element => {
            return ((element.assignmenttype === 'PORT' || element.assignmenttype === 'Unallocated') && !element.is_logical_port);
        });
        this.createLogPortForm = this.formBuilder.group({
            port_numbers: ['', Validators.required],
            lacp_reply: [true],
        });
    }

    get f() {
        return this.createLogPortForm.controls;
    }

    createPort() {
        this.submitted = true;
        if (this.createLogPortForm.invalid) {
            return;
        }
        const portNumbers = this.createLogPortForm.controls['port_numbers'].value;
        const portNumbersArray: number[] = Array.isArray(portNumbers) ? portNumbers : [portNumbers];

        this.createLagPortModel = {
            logical_port_number: null,
            port_numbers: portNumbersArray.map(i => Number(i)),
            lacp_rely: this.createLogPortForm.controls['lacp_reply'].value
        };
        this.emitService.emit(this.createLagPortModel);
    }
}
