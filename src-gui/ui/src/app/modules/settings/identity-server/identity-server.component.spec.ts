import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { IdentityServerComponent } from './identity-server.component';

describe('IdentityServerComponent', () => {
  let component: IdentityServerComponent;
  let fixture: ComponentFixture<IdentityServerComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ IdentityServerComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IdentityServerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
