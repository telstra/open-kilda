import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PortFlowsComponent } from './port-flows.component';

describe('PortFlowsComponent', () => {
  let component: PortFlowsComponent;
  let fixture: ComponentFixture<PortFlowsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PortFlowsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PortFlowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
