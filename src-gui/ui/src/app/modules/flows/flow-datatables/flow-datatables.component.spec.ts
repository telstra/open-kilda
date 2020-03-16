import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowDatatablesComponent } from './flow-datatables.component';

describe('FlowDatatablesComponent', () => {
  let component: FlowDatatablesComponent;
  let fixture: ComponentFixture<FlowDatatablesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowDatatablesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowDatatablesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
