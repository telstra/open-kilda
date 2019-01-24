import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PortGraphComponent } from './port-graph.component';

describe('PortGraphComponent', () => {
  let component: PortGraphComponent;
  let fixture: ComponentFixture<PortGraphComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PortGraphComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PortGraphComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
