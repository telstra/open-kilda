import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchesComponent } from './switches.component';

describe('SwitchesComponent', () => {
  let component: SwitchesComponent;
  let fixture: ComponentFixture<SwitchesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
