import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowDetailComponent } from './flow-detail.component';

describe('FlowDetailComponent', () => {
  let component: FlowDetailComponent;
  let fixture: ComponentFixture<FlowDetailComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowDetailComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
