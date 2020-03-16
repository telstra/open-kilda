import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RuleDetailsComponent } from './rule-details.component';

describe('RuleDetailsComponent', () => {
  let component: RuleDetailsComponent;
  let fixture: ComponentFixture<RuleDetailsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RuleDetailsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RuleDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
