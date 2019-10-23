import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NetworkpathComponent } from './networkpath.component';

describe('NetworkpathComponent', () => {
  let component: NetworkpathComponent;
  let fixture: ComponentFixture<NetworkpathComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NetworkpathComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NetworkpathComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
