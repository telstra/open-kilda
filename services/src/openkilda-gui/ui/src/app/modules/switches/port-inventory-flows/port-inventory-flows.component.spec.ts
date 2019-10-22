import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PortInventoryFlowsComponent } from './port-inventory-flows.component';

describe('PortInventoryFlowsComponent', () => {
  let component: PortInventoryFlowsComponent;
  let fixture: ComponentFixture<PortInventoryFlowsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PortInventoryFlowsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PortInventoryFlowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
