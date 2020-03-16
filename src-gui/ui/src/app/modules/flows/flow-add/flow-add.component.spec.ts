import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowAddComponent } from './flow-add.component';

describe('FlowAddComponent', () => {
  let component: FlowAddComponent;
  let fixture: ComponentFixture<FlowAddComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowAddComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
