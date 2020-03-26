import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AffectedIslComponent } from './affected-isl.component';

describe('AffectedIslComponent', () => {
  let component: AffectedIslComponent;
  let fixture: ComponentFixture<AffectedIslComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AffectedIslComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AffectedIslComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
