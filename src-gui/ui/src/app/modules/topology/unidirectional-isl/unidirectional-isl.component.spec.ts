import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { UnidirectionalIslComponent } from './unidirectional-isl.component';

describe('UnidirectionalIslComponent', () => {
  let component: UnidirectionalIslComponent;
  let fixture: ComponentFixture<UnidirectionalIslComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ UnidirectionalIslComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UnidirectionalIslComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
