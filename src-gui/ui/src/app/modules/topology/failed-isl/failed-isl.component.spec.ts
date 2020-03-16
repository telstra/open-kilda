import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { FailedIslComponent } from './failed-isl.component';

describe('FailedIslComponent', () => {
  let component: FailedIslComponent;
  let fixture: ComponentFixture<FailedIslComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ FailedIslComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(FailedIslComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
