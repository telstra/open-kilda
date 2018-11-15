import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslEditComponent } from './isl-edit.component';

describe('IslEditComponent', () => {
  let component: IslEditComponent;
  let fixture: ComponentFixture<IslEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
