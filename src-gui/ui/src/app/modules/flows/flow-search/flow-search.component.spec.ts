import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FlowSearchComponent } from './flow-search.component';

describe('FlowSearchComponent', () => {
  let component: FlowSearchComponent;
  let fixture: ComponentFixture<FlowSearchComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FlowSearchComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FlowSearchComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
