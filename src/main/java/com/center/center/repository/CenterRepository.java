package com.center.center.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.center.center.entity.Center;

public interface CenterRepository extends JpaRepository<Center, UUID> {

    List<Center> findAllByOrderByCreatedAtAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
