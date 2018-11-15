import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PermissionAssignComponent } from './permission-assign.component';

describe('PermissionAssignComponent', () => {
  let component: PermissionAssignComponent;
  let fixture: ComponentFixture<PermissionAssignComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PermissionAssignComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PermissionAssignComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
