import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowPathGraphComponent } from './flow-path-graph.component';

describe('FlowPathGraphComponent', () => {
  let component: FlowPathGraphComponent;
  let fixture: ComponentFixture<FlowPathGraphComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowPathGraphComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowPathGraphComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
