import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SamlSettingComponent } from './saml-setting.component';

describe('SamlSettingComponent', () => {
  let component: SamlSettingComponent;
  let fixture: ComponentFixture<SamlSettingComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SamlSettingComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SamlSettingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
