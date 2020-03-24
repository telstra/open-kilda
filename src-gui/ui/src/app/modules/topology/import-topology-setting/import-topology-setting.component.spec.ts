import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ImportTopologySettingComponent } from './import-topology-setting.component';

describe('ImportTopologySettingComponent', () => {
  let component: ImportTopologySettingComponent;
  let fixture: ComponentFixture<ImportTopologySettingComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ImportTopologySettingComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ImportTopologySettingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
