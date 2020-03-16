import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowGraphComponent } from './flow-graph.component';

describe('FlowGraphComponent', () => {
  let component: FlowGraphComponent;
  let fixture: ComponentFixture<FlowGraphComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowGraphComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowGraphComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
