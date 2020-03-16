import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IslFlowDatatablesComponent } from './isl-flow-datatables.component';

describe('IslFlowDatatablesComponent', () => {
  let component: IslFlowDatatablesComponent;
  let fixture: ComponentFixture<IslFlowDatatablesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IslFlowDatatablesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IslFlowDatatablesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
