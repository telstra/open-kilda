import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PermissionViewComponent } from './permission-view.component';

describe('PermissionViewComponent', () => {
  let component: PermissionViewComponent;
  let fixture: ComponentFixture<PermissionViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PermissionViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PermissionViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
