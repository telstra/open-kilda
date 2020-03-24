import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslDetailComponent } from './isl-detail.component';

describe('IslDetailComponent', () => {
  let component: IslDetailComponent;
  let fixture: ComponentFixture<IslDetailComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslDetailComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
