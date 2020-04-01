import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslmaintenancemodalComponent } from './islmaintenancemodal.component';

describe('IslmaintenancemodalComponent', () => {
  let component: IslmaintenancemodalComponent;
  let fixture: ComponentFixture<IslmaintenancemodalComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslmaintenancemodalComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslmaintenancemodalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
