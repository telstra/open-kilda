import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowContractsComponent } from './flow-contracts.component';

describe('FlowContractsComponent', () => {
  let component: FlowContractsComponent;
  let fixture: ComponentFixture<FlowContractsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowContractsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowContractsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
