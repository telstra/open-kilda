import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslLayoutComponent } from './isl-layout.component';

describe('IslLayoutComponent', () => {
  let component: IslLayoutComponent;
  let fixture: ComponentFixture<IslLayoutComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslLayoutComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
