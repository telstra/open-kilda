import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { RoleAssignComponent } from './role-assign.component';

describe('RoleAssignComponent', () => {
  let component: RoleAssignComponent;
  let fixture: ComponentFixture<RoleAssignComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ RoleAssignComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(RoleAssignComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
