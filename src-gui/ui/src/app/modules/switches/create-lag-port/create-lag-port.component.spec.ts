import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateLagPortComponent } from './create-lag-port.component';

describe('CreateLagPortComponent', () => {
  let component: CreateLagPortComponent;
  let fixture: ComponentFixture<CreateLagPortComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CreateLagPortComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateLagPortComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
