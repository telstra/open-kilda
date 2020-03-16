import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { DygraphComponent } from './dygraph.component';

describe('DygraphComponent', () => {
  let component: DygraphComponent;
  let fixture: ComponentFixture<DygraphComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ DygraphComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DygraphComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
