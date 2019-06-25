import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowDiverseReversepathComponent } from './flow-diverse-reversepath.component';

describe('FlowDiverseReversepathComponent', () => {
  let component: FlowDiverseReversepathComponent;
  let fixture: ComponentFixture<FlowDiverseReversepathComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowDiverseReversepathComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowDiverseReversepathComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
