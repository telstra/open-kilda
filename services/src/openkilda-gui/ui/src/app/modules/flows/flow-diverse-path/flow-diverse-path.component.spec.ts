import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowDiversePathComponent } from './flow-diverse-path.component';

describe('FlowDiversePathComponent', () => {
  let component: FlowDiversePathComponent;
  let fixture: ComponentFixture<FlowDiversePathComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowDiversePathComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowDiversePathComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
