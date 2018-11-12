import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslListComponent } from './isl-list.component';

describe('IslListComponent', () => {
  let component: IslListComponent;
  let fixture: ComponentFixture<IslListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslListComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
