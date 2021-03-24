import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SamlAddComponent } from './saml-add.component';

describe('SamlAddComponent', () => {
  let component: SamlAddComponent;
  let fixture: ComponentFixture<SamlAddComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SamlAddComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SamlAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
