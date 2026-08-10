package com.center.whatsapp.service;
import com.center.notification.service.NotificationService;
import com.center.settings.service.SettingsService;
import com.center.whatsapp.repository.WhatsappConfigRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.center.common.config.ApplicationProperties;
import com.center.whatsapp.dto.WhatsappInstanceRequest;
import com.center.whatsapp.dto.WhatsappQrResponse;
import com.center.whatsapp.dto.WhatsappResponsibilityResponse;
import com.center.whatsapp.dto.WhatsappStatusResponse;
import com.center.user.entity.User;
import com.center.whatsapp.entity.WhatsappInstance;
import com.center.whatsapp.entity.WhatsappResponsibility;
import com.center.whatsapp.entity.WhatsappResponsibilityId;
import com.center.common.enums.Role;
import com.center.common.exception.BusinessRuleException;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.repository.WhatsappInstanceRepository;
import com.center.whatsapp.repository.WhatsappResponsibilityRepository;
import com.center.whatsapp.service.WhatsappResponsibilityCatalog.Responsibility;
import com.center.common.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages a WhatsApp number pool + in-app QR linking, for BOTH the super admin
 * (owner = null) and each admin (owner = their id). Each owner has their own
 * numbers, their own responsibility assignments, and their own automatic failover.
 *
 * <p>Sending resolves by the caller's tenant: an admin's messages go through the
 * admin's connected number for that purpose, falling back to any of the admin's
 * connected numbers, then the super admin's, then {@code .env}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WhatsappInstanceService {

    private static final String DEFAULT_BASE = "https://api.green-api.com";
    /** Responsibility rows for the super-admin scope use this owner sentinel. */
    private static final UUID SUPER = new UUID(0L, 0L);

    private final WhatsappInstanceRepository repo;
    private final WhatsappResponsibilityRepository respRepo;
    private final com.center.whatsapp.repository.WhatsappConfigRepository configRepo;
    private final ApplicationProperties properties;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final SettingsService settings;
    private final RestClient rest = RestClient.create();

    public record Creds(String baseUrl, String instanceId, String apiToken, boolean configured) {}

    private static boolean isAuthorized(String state) {
        return "authorized".equalsIgnoreCase(state);
    }

    private static UUID respOwner(UUID scope) {
        return scope == null ? SUPER : scope;
    }

    /** The super admin (owner null) is always enabled; an admin needs the flag on. */
    public boolean enabledFor(UUID owner) {
        return owner == null
                || configRepo.findById(owner).map(c -> c.isEnabled()).orElse(false);
    }

    private void requireEnabled(UUID owner) {
        if (!enabledFor(owner)) {
            throw new BusinessRuleException("ميزة واتساب غير مُفعّلة لحسابك من قبل الإدارة");
        }
    }

    private List<WhatsappInstance> pool(UUID owner) {
        return owner == null
                ? repo.findByOwnerAdminIdIsNullOrderByCreatedAtAsc()
                : repo.findByOwnerAdminIdOrderByCreatedAtAsc(owner);
    }

    // ---- credential resolution for sending (scope = current tenant) ---------

    @Transactional(readOnly = true)
    public Creds resolve() {
        UUID scope = TenantContext.get();
        return firstConnected(scope)
                .or(() -> scope == null ? Optional.empty() : firstConnected(null))
                .map(WhatsappInstanceService::credsOf)
                .orElseGet(this::envCreds);
    }

    @Transactional(readOnly = true)
    public Creds resolveFor(String responsibilityCode) {
        UUID scope = TenantContext.get();
        WhatsappInstance assigned = respRepo.findById(new WhatsappResponsibilityId(respOwner(scope), responsibilityCode))
                .flatMap(r -> repo.findById(r.getInstanceId()))
                .filter(w -> isAuthorized(w.getState()))
                .orElse(null);
        if (assigned != null) {
            return credsOf(assigned);
        }
        return resolve();
    }

    private Optional<WhatsappInstance> firstConnected(UUID owner) {
        return pool(owner).stream().filter(w -> isAuthorized(w.getState())).findFirst();
    }

    private static Creds credsOf(WhatsappInstance w) {
        return new Creds(w.getBaseUrl(), w.getInstanceId(), w.getApiToken(), true);
    }

    private Creds envCreds() {
        ApplicationProperties.GreenApi c = properties.greenApi();
        return new Creds(c.baseUrl(), c.instanceId(), c.apiToken(), c.configured());
    }

    // ---- number pool (UI) - scoped by owner ---------------------------------

    @Transactional
    public List<WhatsappStatusResponse> list(UUID owner) {
        if (!enabledFor(owner)) {
            return List.of();
        }
        List<WhatsappStatusResponse> out = new ArrayList<>();
        for (WhatsappInstance w : pool(owner)) {
            refreshAndReconcile(w);
            out.add(toStatus(w));
        }
        return out;
    }

    @Transactional
    public WhatsappStatusResponse add(UUID owner, WhatsappInstanceRequest req) {
        requireEnabled(owner);
        String instanceId = req.instanceId().trim();
        repo.findByInstanceId(instanceId).ifPresent(w -> {
            throw new BusinessRuleException("هذا الـ Instance مضاف بالفعل");
        });
        WhatsappInstance w = new WhatsappInstance();
        w.setOwnerAdminId(owner);
        w.setInstanceId(instanceId);
        w.setApiToken(req.apiToken().trim());
        w.setBaseUrl(DEFAULT_BASE);
        w.setLabel(req.label() == null || req.label().isBlank() ? null : req.label().trim());
        repo.save(w);
        return toStatus(w);
    }

    @Transactional
    public void delete(UUID owner, UUID id) {
        WhatsappInstance w = require(owner, id);
        handleDown(w, w.getState(), true);
        respRepo.deleteByInstanceId(id);
        repo.delete(w);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public WhatsappQrResponse qr(UUID owner, UUID id) {
        WhatsappInstance w = require(owner, id);
        try {
            Map<String, Object> res = rest.get()
                    .uri(w.getBaseUrl() + "/waInstance{id}/qr/{token}", w.getInstanceId(), w.getApiToken())
                    .retrieve()
                    .body(Map.class);
            return new WhatsappQrResponse(str(res == null ? null : res.get("type")),
                    str(res == null ? null : res.get("message")));
        } catch (RestClientException ex) {
            log.error("Green API qr failed: {}", ex.getMessage());
            throw new BusinessRuleException("تعذّر جلب رمز QR، تأكد من صحة بيانات الـ Instance");
        }
    }

    @Transactional
    public WhatsappStatusResponse logout(UUID owner, UUID id) {
        WhatsappInstance w = require(owner, id);
        String prev = w.getState();
        try {
            rest.get()
                    .uri(w.getBaseUrl() + "/waInstance{id}/logout/{token}", w.getInstanceId(), w.getApiToken())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Green API logout failed: {}", ex.getMessage());
            throw new BusinessRuleException("تعذّر فصل الرقم، حاول مرة أخرى");
        }
        if (isAuthorized(prev)) {
            handleDown(w, "notAuthorized", false);
        }
        w.setPhone(null);
        w.setState("notAuthorized");
        return toStatus(w);
    }

    // ---- responsibilities - scoped by owner ---------------------------------

    @Transactional(readOnly = true)
    public List<WhatsappResponsibilityResponse> responsibilities(UUID owner) {
        List<WhatsappResponsibilityResponse> out = new ArrayList<>();
        for (Responsibility r : WhatsappResponsibilityCatalog.ALL) {
            UUID inst = respRepo.findById(new WhatsappResponsibilityId(respOwner(owner), r.code()))
                    .map(WhatsappResponsibility::getInstanceId).orElse(null);
            out.add(new WhatsappResponsibilityResponse(r.code(), r.label(), r.description(), inst));
        }
        return out;
    }

    @Transactional
    public List<WhatsappResponsibilityResponse> assign(UUID owner, String code, UUID instanceId) {
        requireEnabled(owner);
        if (!WhatsappResponsibilityCatalog.isValid(code)) {
            throw new BusinessRuleException("مسؤولية غير معروفة");
        }
        WhatsappResponsibilityId key = new WhatsappResponsibilityId(respOwner(owner), code);
        if (instanceId == null) {
            respRepo.findById(key).ifPresent(respRepo::delete);
        } else {
            require(owner, instanceId);
            respRepo.save(new WhatsappResponsibility(respOwner(owner), code, instanceId));
        }
        return responsibilities(owner);
    }

    // ---- monitoring / failover (all scopes) ---------------------------------

    @Transactional
    public void monitor() {
        for (WhatsappInstance w : repo.findAll()) {
            refreshAndReconcile(w);
        }
    }

    private void refreshAndReconcile(WhatsappInstance w) {
        String live = fetchState(w);
        if (live == null) {
            return;
        }
        String prev = w.getState();
        if (isAuthorized(prev) && !isAuthorized(live)) {
            handleDown(w, live, false);
        }
        w.setState(live);
        if (isAuthorized(live)) {
            String phone = fetchPhone(w);
            if (phone != null) {
                w.setPhone(phone);
            }
        }
    }

    private void handleDown(WhatsappInstance down, String reason, boolean manual) {
        UUID owner = down.getOwnerAdminId();
        List<WhatsappResponsibility> owned = respRepo.findByInstanceId(down.getId());
        WhatsappInstance backup = pool(owner).stream()
                .filter(w -> !w.getId().equals(down.getId()) && isAuthorized(w.getState()))
                .findFirst()
                .orElse(null);

        String who = displayName(down);
        String cause = manual ? "تمت إزالته يدويًا" : "انقطع الاتصال" + reasonSuffix(reason);

        if (backup != null && !owned.isEmpty()) {
            for (WhatsappResponsibility r : owned) {
                r.setInstanceId(backup.getId());
                respRepo.save(r);
            }
            notifyOwner(owner, "تحويل مسؤوليات رقم واتساب",
                    "الرقم (" + who + ") " + cause + ". تم تحويل " + owned.size()
                            + " من مسؤولياته تلقائيًا إلى الرقم (" + displayName(backup) + ").");
        } else if (backup == null) {
            notifyOwner(owner, "انقطاع رقم واتساب دون بديل",
                    "الرقم (" + who + ") " + cause
                            + " ولا يوجد رقم احتياطي متصل. سيتم الاكتفاء بإشعارات التطبيق فقط "
                            + "حتى يتم ربط رقم آخر.");
        } else {
            notifyOwner(owner, "انقطاع رقم واتساب",
                    "الرقم (" + who + ") " + cause + ". لا مسؤوليات مرتبطة به.");
        }
    }

    /** Notify the pool owner: the admin themselves, or every super admin. */
    private void notifyOwner(UUID owner, String title, String body) {
        String sender = settings.senderName();
        if (owner == null) {
            for (User u : userRepository.findByRoleAndActiveTrue(Role.SUPER_ADMIN)) {
                notificationService.notify(u.getId(), sender, "whatsapp", title, body, null);
            }
        } else {
            notificationService.notify(owner, sender, "whatsapp", title, body, null);
        }
    }

    private static String reasonSuffix(String reason) {
        return reason == null || reason.isBlank() ? "" : " (الحالة: " + reason + ")";
    }

    private static String displayName(WhatsappInstance w) {
        if (w.getLabel() != null && !w.getLabel().isBlank()) return w.getLabel();
        if (w.getPhone() != null && !w.getPhone().isBlank()) return "+" + w.getPhone();
        return w.getInstanceId();
    }

    // ---- Green API calls ----------------------------------------------------

    @SuppressWarnings("unchecked")
    private String fetchState(WhatsappInstance w) {
        try {
            Map<String, Object> res = rest.get()
                    .uri(w.getBaseUrl() + "/waInstance{id}/getStateInstance/{token}",
                            w.getInstanceId(), w.getApiToken())
                    .retrieve()
                    .body(Map.class);
            return res == null ? null : str(res.get("stateInstance"));
        } catch (RestClientException ex) {
            log.warn("Green API getStateInstance failed: {}", ex.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private String fetchPhone(WhatsappInstance w) {
        try {
            Map<String, Object> res = rest.get()
                    .uri(w.getBaseUrl() + "/waInstance{id}/getWaSettings/{token}",
                            w.getInstanceId(), w.getApiToken())
                    .retrieve()
                    .body(Map.class);
            return res == null ? null : str(res.get("phone"));
        } catch (RestClientException ex) {
            log.warn("Green API getWaSettings failed: {}", ex.getMessage());
            return null;
        }
    }

    private WhatsappInstance require(UUID owner, UUID id) {
        return repo.findById(id)
                .filter(w -> Objects.equals(w.getOwnerAdminId(), owner))
                .orElseThrow(() -> new BusinessRuleException("الرقم غير موجود"));
    }

    private WhatsappStatusResponse toStatus(WhatsappInstance w) {
        boolean connected = isAuthorized(w.getState());
        return new WhatsappStatusResponse(
                w.getId(), w.getLabel(), connected, w.getState(),
                connected ? w.getPhone() : null, w.getInstanceId());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
