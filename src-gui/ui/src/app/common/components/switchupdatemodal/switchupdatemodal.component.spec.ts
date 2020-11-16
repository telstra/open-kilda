import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchupdatemodalComponent } from './switchupdatemodal.component';

describe('SwitchupdatemodalComponent', () => {
  let component: SwitchupdatemodalComponent;
  let fixture: ComponentFixture<SwitchupdatemodalComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchupdatemodalComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchupdatemodalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
