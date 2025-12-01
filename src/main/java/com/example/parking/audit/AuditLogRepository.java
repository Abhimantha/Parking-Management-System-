package com.example.parking.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // Paged search with null-safe filters.
    @Query("""
        select a from AuditLog a
        where (:fromTs   is null or a.ts >= :fromTs)
          and (:toTs     is null or a.ts <  :toTs)
          and (:action   is null or lower(a.action)   like lower(concat('%', :action,   '%')))
          and (:username is null or lower(a.username) like lower(concat('%', :username, '%')))
          and (:status   is null or a.status = :status)
        order by a.ts desc
        """)
    Page<AuditLog> search(
            @Param("fromTs")   Instant fromTs,
            @Param("toTs")     Instant toTs,
            @Param("action")   String action,
            @Param("username") String username,
            @Param("status")   Integer status,
            Pageable pageable
    );

    // Explicit paged fetch (optional but handy).
    Page<AuditLog> findAll(Pageable pageable);

    // Retention purge: delete logs older than a cutoff timestamp.
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AuditLog a where a.ts < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
