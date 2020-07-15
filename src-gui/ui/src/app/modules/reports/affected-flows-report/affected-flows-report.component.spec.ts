import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AffectedFlowsReportComponent } from './affected-flows-report.component';

describe('AffectedFlowsReportComponent', () => {
  let component: AffectedFlowsReportComponent;
  let fixture: ComponentFixture<AffectedFlowsReportComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AffectedFlowsReportComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AffectedFlowsReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
