package com.center.whatsapp.check;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsappNumberCheckRepository extends JpaRepository<WhatsappNumberCheck, String> {

    /** Every answer still inside its trust window, for the numbers asked about. */
    @Query("SELECT c FROM WhatsappNumberCheck c WHERE c.phone IN :phones AND c.checkedAt >= :since")
    List<WhatsappNumberCheck> freshFor(@Param("phones") Collection<String> phones,
            @Param("since") OffsetDateTime since);

    /** Every answer still inside its trust window. Read once per page load. */
    @Query("SELECT c FROM WhatsappNumberCheck c WHERE c.checkedAt >= :since")
    List<WhatsappNumberCheck> allFresh(@Param("since") OffsetDateTime since);
}
