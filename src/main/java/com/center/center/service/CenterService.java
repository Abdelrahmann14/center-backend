package com.center.center.service;

import java.util.List;
import java.util.UUID;

import com.center.center.dto.CenterRequest;
import com.center.center.dto.CenterResponse;

public interface CenterService {

    List<CenterResponse> findAll();

    CenterResponse create(CenterRequest request);

    CenterResponse update(UUID centerId, CenterRequest request);

    void delete(UUID centerId);
}
