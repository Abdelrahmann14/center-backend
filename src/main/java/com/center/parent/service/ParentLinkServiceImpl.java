package com.center.parent.service;
import com.center.google.event.GoogleContactEvents;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.parent.dto.LinkedParentResponse;
import com.center.parent.dto.LinkedStudentResponse;
import com.center.parent.dto.ParentPendingResponse;
import com.center.parent.dto.ParentRequestResponse;
import com.center.parent.entity.Parent;
import com.center.parent.entity.ParentStudentLink;
import com.center.student.entity.Student;
import com.center.user.entity.User;
import com.center.common.enums.LinkStatus;
import com.center.common.enums.NotificationType;
import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.parent.repository.ParentRepository;
import com.center.parent.repository.ParentStudentLinkRepository;
import com.center.student.repository.StudentRepository;
import com.center.student.repository.StudentRepository.StudentIdentity;
import com.center.user.repository.UserRepository;
import com.center.auth.security.AuthenticatedUser;
import com.center.whatsapp.service.GreenApiClient;
import com.center.notification.service.MessageTemplateService;
import com.center.notification.service.NotificationService;
import com.center.parent.service.ParentLinkService;
import com.center.settings.service.SettingsService;
import com.center.common.tenant.TenantContext;
import com.center.common.tenant.TenantScopedExecutor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Linking parents and students.
 *
 * <p>Student-side actions (list / approve / reject) run under the student's own
 * bound tenant, so they are ordinary {@code @Transactional} methods. Parent-side
 * actions have no bound tenant (a parent owns no workspace) and touch students in
 * other workspaces, so they bind each student's tenant with
 * {@link TenantContext#callAs} inside {@link TenantScopedExecutor#inTenantTx} - and
 * therefore must NOT be {@code @Transactional} themselves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ParentLinkServiceImpl implements ParentLinkService {

    private static final int MAX_PARENTS_PER_STUDENT = 2;

    private static final String NOT_A_STUDENT = "هذا الحساب ليس حساب طالب";
    private static final String NOT_A_PARENT = "هذا الحساب ليس حساب ولي أمر";
    private static final String REQUEST_NOT_FOUND = "الطلب غير موجود";
    private static final String NOT_YOUR_REQUEST = "هذا الطلب لا يخص حسابك";
    private static final String ALREADY_DECIDED = "تمت معالجة هذا الطلب بالفعل";
    private static final String PARENT_LIMIT = "لا يمكن ربط أكثر من ولي أمرين بهذا الطالب";
    private static final String STUDENT_NOT_FOUND = "لا يوجد طالب بهذا الكود";
    private static final String STUDENT_NO_ACCOUNT = "لم يُنشئ هذا الطالب حسابًا بعد، لا يمكن ربطه";
    private static final String ALREADY_LINKED = "لديك طلب أو ارتباط مع هذا الطالب بالفعل";

    private final StudentRepository studentRepository;
    private final ParentRepository parentRepository;
    private final ParentStudentLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final GreenApiClient greenApiClient;
    private final NotificationService notificationService;
    private final MessageTemplateService templateService;
    private final SettingsService settingsService;
    private final TenantScopedExecutor tx;
    private final org.springframework.context.ApplicationEventPublisher events;

    // --- Student side (own tenant bound by the JWT filter) ----------------

    @Override
    @Transactional(readOnly = true)
    public List<ParentRequestResponse> pendingRequests(AuthenticatedUser student) {
        Student me = requireStudent(student);
        return linkRepository.findByStudentIdAndStatusOrderByCreatedAtDesc(me.getId(), LinkStatus.PENDING)
                .stream()
                .map(link -> {
                    Parent parent = parentRepository.findById(link.getParentId()).orElseThrow();
                    User account = userRepository.findById(parent.getUserId()).orElseThrow();
                    return new ParentRequestResponse(link.getId(), parent.getName(),
                            parent.getPhone(), account.getEmail(), link.getCreatedAt());
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkedParentResponse> linkedParents(AuthenticatedUser student) {
        Student me = requireStudent(student);
        return linkRepository.findByStudentIdAndStatus(me.getId(), LinkStatus.APPROVED).stream()
                .map(link -> {
                    Parent parent = parentRepository.findById(link.getParentId()).orElseThrow();
                    User account = userRepository.findById(parent.getUserId()).orElseThrow();
                    return new LinkedParentResponse(parent.getName(), parent.getPhone(), account.getEmail());
                })
                .toList();
    }

    @Override
    @Transactional
    public void approve(UUID linkId, AuthenticatedUser student) {
        Student me = requireStudent(student);
        ParentStudentLink link = requireMyPendingRequest(linkId, me);

        if (linkRepository.countByStudentIdAndStatus(me.getId(), LinkStatus.APPROVED)
                >= MAX_PARENTS_PER_STUDENT) {
            throw new BusinessRuleException(PARENT_LIMIT);
        }

        Parent parent = parentRepository.findById(link.getParentId()).orElseThrow();
        User parentUser = userRepository.findById(parent.getUserId()).orElseThrow();
        // The very first approval flips a pending account live - the moment we
        // owe the parent a WhatsApp confirmation. Later links are in-app only.
        boolean firstApproval = !parentUser.isActive();

        link.setStatus(LinkStatus.APPROVED);
        link.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        linkRepository.save(link);

        if (!parentUser.isActive()) {
            parentUser.setActive(true);
            userRepository.save(parentUser);
        }

        // The parent's number becomes the student's trusted parent phone.
        me.setParentPhones(new String[] {link.getPhoneAtRequest()});
        studentRepository.save(me);
        // Re-sync the student's contacts so the parent's number is added/updated.
        events.publishEvent(new com.center.google.event.GoogleContactEvents.StudentChanged(
                me.getAdminId(), me.getId()));

        if (firstApproval) {
            greenApiClient.sendText("parent_link_approved_wa", parent.getPhone(), templateService.render("parent_link_approved_wa",
                    Map.of("name", me.getName())).body());
        }
        MessageTemplateService.Rendered approved = templateService.render("parent_link_approved",
                Map.of("name", me.getName()));
        notificationService.notify(parentUser.getId(), settingsService.senderName(),
                NotificationType.PARENT_LINK_APPROVED, approved.title(), approved.body(), link.getId());
        log.info("Student {} approved parent link {}", me.getId(), linkId);
    }

    @Override
    @Transactional
    public void reject(UUID linkId, AuthenticatedUser student) {
        Student me = requireStudent(student);
        ParentStudentLink link = requireMyPendingRequest(linkId, me);

        Parent parent = parentRepository.findById(link.getParentId()).orElseThrow();
        User parentUser = userRepository.findById(parent.getUserId()).orElseThrow();
        long otherApproved = linkRepository.countByParentIdAndStatus(parent.getId(), LinkStatus.APPROVED);

        if (!parentUser.isActive() && otherApproved == 0) {
            // Initial registration refused: tell the parent over WhatsApp and
            // remove the never-activated account so the login name frees up for
            // a retry. The DB cascades the parent profile, links and codes.
            greenApiClient.sendText("parent_link_rejected_wa", parent.getPhone(),
                    templateService.render("parent_link_rejected_wa", Map.of()).body());
            userRepository.delete(parentUser);
            log.info("Student {} rejected and removed pending parent {}", me.getId(), parentUser.getId());
        } else {
            link.setStatus(LinkStatus.REJECTED);
            link.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
            linkRepository.save(link);
            MessageTemplateService.Rendered rejected = templateService.render("parent_link_rejected",
                    Map.of("name", me.getName()));
            notificationService.notify(parentUser.getId(), settingsService.senderName(),
                    NotificationType.PARENT_LINK_REJECTED, rejected.title(), rejected.body(), link.getId());
            log.info("Student {} rejected parent link {}", me.getId(), linkId);
        }
    }

    // --- Parent side (no bound tenant; bind each student's workspace) ------

    @Override
    public ParentPendingResponse addStudent(int serial, AuthenticatedUser parentPrincipal) {
        Parent parent = parentRepository.findByUserId(parentPrincipal.getId())
                .orElseThrow(() -> new BusinessRuleException(NOT_A_PARENT));
        StudentIdentity identity = studentRepository.findIdentityBySerial(serial)
                .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));

        return TenantContext.callAs(identity.getAdminId(), () -> tx.inTenantTx(() -> {
            Student student = studentRepository.findById(identity.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(STUDENT_NOT_FOUND));
            if (student.getUserId() == null) {
                throw new BusinessRuleException(STUDENT_NO_ACCOUNT);
            }
            if (linkRepository.existsByParentIdAndStudentId(parent.getId(), student.getId())) {
                throw new BusinessRuleException(ALREADY_LINKED);
            }
            if (linkRepository.countByStudentIdAndStatus(student.getId(), LinkStatus.APPROVED)
                    >= MAX_PARENTS_PER_STUDENT) {
                throw new BusinessRuleException(PARENT_LIMIT);
            }

            ParentStudentLink link = new ParentStudentLink();
            link.setParentId(parent.getId());
            link.setStudentId(student.getId());
            link.setStudentAdminId(identity.getAdminId());
            link.setStatus(LinkStatus.PENDING);
            link.setPhoneAtRequest(parent.getPhone());
            linkRepository.save(link);

            notificationService.notify(student.getUserId(), NotificationType.SYSTEM_SENDER,
                    NotificationType.PARENT_LINK_REQUEST,
                    "طلب ربط ولي أمر",
                    "قام (" + parent.getName() + ") بطلب ربط حسابه بحسابك بصفته ولي أمر. "
                            + "افتح الإعدادات ثم أولياء الأمور للرد على الطلب.",
                    link.getId());
            log.info("Parent {} requested link to student serial {}", parent.getId(), serial);
            return new ParentPendingResponse(student.getName());
        }));
    }

    @Override
    public List<LinkedStudentResponse> linkedStudents(AuthenticatedUser parentPrincipal) {
        Parent parent = parentRepository.findByUserId(parentPrincipal.getId())
                .orElseThrow(() -> new BusinessRuleException(NOT_A_PARENT));
        List<LinkedStudentResponse> out = new ArrayList<>();
        for (ParentStudentLink link : linkRepository.findByParentIdAndStatus(parent.getId(),
                LinkStatus.APPROVED)) {
            LinkedStudentResponse row = TenantContext.callAs(link.getStudentAdminId(),
                    () -> tx.inTenantTx(() -> studentRepository.findById(link.getStudentId())
                            .map(s -> new LinkedStudentResponse(s.getSerial(), s.getName()))
                            .orElse(null)));
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    // --- Helpers ----------------------------------------------------------

    private Student requireStudent(AuthenticatedUser principal) {
        return studentRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new BusinessRuleException(NOT_A_STUDENT));
    }

    private ParentStudentLink requireMyPendingRequest(UUID linkId, Student me) {
        ParentStudentLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new ResourceNotFoundException(REQUEST_NOT_FOUND));
        if (!link.getStudentId().equals(me.getId())) {
            throw new BusinessRuleException(NOT_YOUR_REQUEST);
        }
        if (link.getStatus() != LinkStatus.PENDING) {
            throw new BusinessRuleException(ALREADY_DECIDED);
        }
        return link;
    }
}
