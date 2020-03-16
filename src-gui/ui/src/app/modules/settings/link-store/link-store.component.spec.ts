import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { LinkStoreComponent } from './link-store.component';

describe('LinkStoreComponent', () => {
  let component: LinkStoreComponent;
  let fixture: ComponentFixture<LinkStoreComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ LinkStoreComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(LinkStoreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
