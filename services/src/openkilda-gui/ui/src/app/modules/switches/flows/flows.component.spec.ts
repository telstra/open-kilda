import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowsComponent } from './flows.component';

describe('FlowsComponent', () => {
  let component: FlowsComponent;
  let fixture: ComponentFixture<FlowsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
