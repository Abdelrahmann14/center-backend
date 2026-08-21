package com.center.whatsapp.check;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WhatsappNumberCheckRepository extends JpaRepository<WhatsappNumberCheck, String> {

    /**
     * Which of these numbers already have an answer.
     *
     * <p>No age test. An answer is kept for good and a number is asked about
     * exactly once, ever - see {@link WhatsappNumberCheckService} for why the
     * trust window was dropped.
     */
    @Query("SELECT c FROM WhatsappNumberCheck c WHERE c.phone IN :phones")
    List<WhatsappNumberCheck> answeredAmong(@Param("phones") Collection<String> phones);
}
