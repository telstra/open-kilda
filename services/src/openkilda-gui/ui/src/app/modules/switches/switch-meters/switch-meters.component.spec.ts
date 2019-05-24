import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchMetersComponent } from './switch-meters.component';

describe('SwitchMetersComponent', () => {
  let component: SwitchMetersComponent;
  let fixture: ComponentFixture<SwitchMetersComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchMetersComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchMetersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
