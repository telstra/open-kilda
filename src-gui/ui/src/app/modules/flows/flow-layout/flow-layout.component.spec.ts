import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowLayoutComponent } from './flow-layout.component';

describe('FlowLayoutComponent', () => {
  let component: FlowLayoutComponent;
  let fixture: ComponentFixture<FlowLayoutComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowLayoutComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
