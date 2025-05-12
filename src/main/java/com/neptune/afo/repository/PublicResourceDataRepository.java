package com.neptune.afo.repository;

import com.neptune.afo.entity.PublicResourceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicResourceDataRepository extends JpaRepository<PublicResourceData, Long> {

    @Query("SELECT prd FROM PublicResourceData prd WHERE prd.type = :type AND DATE(prd.createdOn) = DATE(:date)")
    Optional<PublicResourceData> findByTypeAndCreatedOn(@Param("type") String type, @Param("date") Date date);

    List<PublicResourceData> findByType(String type);
}
