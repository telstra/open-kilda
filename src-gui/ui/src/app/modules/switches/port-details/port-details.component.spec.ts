import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PortDetailsComponent } from './port-details.component';

describe('PortDetailsComponent', () => {
  let component: PortDetailsComponent;
  let fixture: ComponentFixture<PortDetailsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PortDetailsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PortDetailsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
