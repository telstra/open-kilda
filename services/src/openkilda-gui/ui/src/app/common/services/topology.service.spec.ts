import { TestBed, inject } from '@angular/core/testing';

import { TopologyService } from './topology.service';

describe('TopologyService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [TopologyService]
    });
  });

  it('should be created', inject([TopologyService], (service: TopologyService) => {
    expect(service).toBeTruthy();
  }));
});
