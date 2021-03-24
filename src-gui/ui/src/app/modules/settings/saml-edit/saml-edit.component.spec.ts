import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SamlEditComponent } from './saml-edit.component';

describe('SamlEditComponent', () => {
  let component: SamlEditComponent;
  let fixture: ComponentFixture<SamlEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SamlEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SamlEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
