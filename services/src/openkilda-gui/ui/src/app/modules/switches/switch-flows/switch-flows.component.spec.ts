import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchFlowsComponent } from './switch-flows.component';

describe('SwitchFlowsComponent', () => {
  let component: SwitchFlowsComponent;
  let fixture: ComponentFixture<SwitchFlowsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchFlowsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchFlowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
