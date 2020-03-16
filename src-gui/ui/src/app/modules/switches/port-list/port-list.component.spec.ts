import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PortListComponent } from './port-list.component';

describe('PortListComponent', () => {
  let component: PortListComponent;
  let fixture: ComponentFixture<PortListComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PortListComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PortListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
