import { TestBed, inject } from '@angular/core/testing';

import { DygraphService } from './dygraph.service';

describe('DygraphService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DygraphService]
    });
  });

  it('should be created', inject([DygraphService], (service: DygraphService) => {
    expect(service).toBeTruthy();
  }));
});
