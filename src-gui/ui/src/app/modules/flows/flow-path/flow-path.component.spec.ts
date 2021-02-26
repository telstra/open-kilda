import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowPathComponent } from './flow-path.component';

describe('FlowPathComponent', () => {
  let component: FlowPathComponent;
  let fixture: ComponentFixture<FlowPathComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowPathComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowPathComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
