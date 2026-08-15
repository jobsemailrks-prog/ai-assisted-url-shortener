package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.model.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortCode(String shortCode);

    @Query("SELECT MAX(c.clickedAt) FROM ClickEvent c WHERE c.shortCode = :shortCode")
    OffsetDateTime findLastClickedAtByShortCode(@Param("shortCode") String shortCode);
}