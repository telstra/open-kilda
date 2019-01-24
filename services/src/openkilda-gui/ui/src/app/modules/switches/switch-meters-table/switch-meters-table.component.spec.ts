import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchMetersTableComponent } from './switch-meters-table.component';

describe('SwitchMetersTableComponent', () => {
  let component: SwitchMetersTableComponent;
  let fixture: ComponentFixture<SwitchMetersTableComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchMetersTableComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchMetersTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
