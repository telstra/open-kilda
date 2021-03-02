import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowPingModalComponent } from './flow-ping-modal.component';

describe('FlowPingModalComponent', () => {
  let component: FlowPingModalComponent;
  let fixture: ComponentFixture<FlowPingModalComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowPingModalComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowPingModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
