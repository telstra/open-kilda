import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowReRouteModalComponent } from './flow-re-route-modal.component';

describe('FlowReRouteModalComponent', () => {
  let component: FlowReRouteModalComponent;
  let fixture: ComponentFixture<FlowReRouteModalComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowReRouteModalComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowReRouteModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
