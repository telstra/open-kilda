import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SwitchStoreComponent } from './switch-store.component';

describe('SwitchStoreComponent', () => {
  let component: SwitchStoreComponent;
  let fixture: ComponentFixture<SwitchStoreComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SwitchStoreComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SwitchStoreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
