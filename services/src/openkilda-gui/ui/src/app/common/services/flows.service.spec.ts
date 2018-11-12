import { TestBed, inject } from '@angular/core/testing';

import { FlowsService } from './flows.service';

describe('FlowsService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [FlowsService]
    });
  });

  it('should be created', inject([FlowsService], (service: FlowsService) => {
    expect(service).toBeTruthy();
  }));
});
