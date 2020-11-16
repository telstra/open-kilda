import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AffectedFlowListComponent } from './affected-flow-list.component';

describe('AffectedFlowListComponent', () => {
  let component: AffectedFlowListComponent;
  let fixture: ComponentFixture<AffectedFlowListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AffectedFlowListComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AffectedFlowListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
