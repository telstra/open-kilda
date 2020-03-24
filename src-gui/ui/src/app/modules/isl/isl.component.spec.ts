import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslComponent } from './isl.component';

describe('IslComponent', () => {
  let component: IslComponent;
  let fixture: ComponentFixture<IslComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
