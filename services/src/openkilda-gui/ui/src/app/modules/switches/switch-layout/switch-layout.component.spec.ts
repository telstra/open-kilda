import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchLayoutComponent } from './switch-layout.component';

describe('SwitchLayoutComponent', () => {
  let component: SwitchLayoutComponent;
  let fixture: ComponentFixture<SwitchLayoutComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchLayoutComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
