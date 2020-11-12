import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { WorldMapViewComponent } from './world-map-view.component';

describe('WorldMapViewComponent', () => {
  let component: WorldMapViewComponent;
  let fixture: ComponentFixture<WorldMapViewComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ WorldMapViewComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(WorldMapViewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
