import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SamlListTableComponent } from './saml-list-table.component';

describe('SamlListTableComponent', () => {
  let component: SamlListTableComponent;
  let fixture: ComponentFixture<SamlListTableComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SamlListTableComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SamlListTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
