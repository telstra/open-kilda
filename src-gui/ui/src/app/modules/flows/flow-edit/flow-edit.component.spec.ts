import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowEditComponent } from './flow-edit.component';

describe('FlowEditComponent', () => {
  let component: FlowEditComponent;
  let fixture: ComponentFixture<FlowEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
