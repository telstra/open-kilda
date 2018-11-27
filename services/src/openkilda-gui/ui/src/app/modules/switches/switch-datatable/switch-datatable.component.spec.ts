import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchDatatableComponent } from './switch-datatable.component';

describe('SwitchDatatableComponent', () => {
  let component: SwitchDatatableComponent;
  let fixture: ComponentFixture<SwitchDatatableComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchDatatableComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchDatatableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
