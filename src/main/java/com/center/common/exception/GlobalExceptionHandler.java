package com.center.common.exception;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders every error as an RFC 7807 {@link ProblemDetail}. Its {@code detail}
 * member is what the frontend reads, so the standard shape and the existing
 * client agree without either side changing.
 *
 * <p>Internal details never reach the response - unexpected failures are logged
 * server-side and answered with a generic Arabic message.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String GENERIC_ERROR = "حدث خطأ غير متوقع";

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail onApplication(ApplicationException ex) {
        return problem(ex.getStatus(), ex.getMessage());
    }

    /** Bean-validation failures answer 422, matching the previous API. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalidBody(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("البيانات المُرسلة غير صالحة");
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail onMissingParam(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "بيانات ناقصة: " + ex.getParameterName());
    }

    /** A malformed uuid or enum in the path/query is bad input, not a 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "قيمة غير صالحة: " + ex.getName());
    }

    /**
     * Unparseable body - malformed JSON, or a value outside a closed enum such
     * as homework_flag. Bad input, so 422 rather than a 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause() instanceof InvalidFormatException invalid
                ? "قيمة غير صالحة: " + invalid.getValue()
                : "البيانات المُرسلة غير صالحة";
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }

    /** An unknown sort/filter property is bad input, not a server fault. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail onBadProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "حقل ترتيب غير صالح: " + ex.getPropertyName());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail onBadCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "اسم المستخدم أو كلمة المرور غير صحيحة");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail onAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "هذه العملية تتطلب صلاحية المدرّس");
    }

    /**
     * The database is unreachable - answer 503 fast, and say so plainly.
     *
     * <p>Without this it fell through to the catch-all below and went out as a
     * 500, which the browser reads as "the server considered your request and
     * refused it". It is the opposite: the request was never processed at all,
     * and the client's offline mirror can stand in for it. A 500 makes the app
     * show an error toast; a 503 makes it fall back to the mirror and queue the
     * write, which is the behaviour a dropped line should produce.
     *
     * <p>This is the ordinary case when the workspace runs on a laptop whose
     * internet drops: the browser still reaches the backend on localhost, but the
     * backend can no longer reach its own hosted Postgres.
     *
     * <p>Logged as one line, not a stack. An outage produces one of these per
     * request plus one per scheduler tick, and the stack is the same every time.
     */
    @ExceptionHandler({
        org.springframework.dao.DataAccessResourceFailureException.class,
        org.springframework.transaction.CannotCreateTransactionException.class,
        org.springframework.jdbc.CannotGetJdbcConnectionException.class,
    })
    public ProblemDetail onDatabaseUnreachable(Exception ex) {
        log.warn("Database unreachable: {}", rootMessage(ex));
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "تعذّر الوصول لقاعدة البيانات");
    }

    /** The innermost cause's message - the only informative part of these. */
    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "تم تعديل هذا السجل من مكان آخر، حدّث الصفحة وأعد المحاولة");
    }

    /**
     * A database constraint said no.
     *
     * <p>Without this, every one of these fell through to the catch-all and came
     * back as "حدث خطأ غير متوقع" - a 500 that says nothing, cannot be acted on,
     * and reads like the server broke when in fact it refused on purpose. That
     * is exactly how a stale unique constraint on `groups` went undiagnosed:
     * removing the service's own pre-check left the database as the only thing
     * still enforcing it, and its answer arrived as gibberish.
     *
     * <p>409, not 500: the request was well formed and the server understood it.
     * The constraint name is logged rather than returned - it is a schema detail
     * the user cannot use - but it is what makes the next one findable in one
     * look at the log instead of a bisect.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail onIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        log.warn("Database constraint rejected a write (constraint={})", constraint, ex);
        return problem(HttpStatus.CONFLICT, switch (constraint == null ? "" : constraint) {
            case "groups_admin_day_time_key" ->
                "يوجد مجموعة أخرى في نفس اليوم والوقت";
            case "centers_admin_name_key" -> "يوجد سنتر بنفس الاسم";
            case "grades_admin_name_key" -> "يوجد صف بنفس الاسم";
            default -> "لا يمكن حفظ هذه البيانات - تتعارض مع سجل موجود بالفعل";
        });
    }

    /**
     * The constraint Postgres named in its error, or null.
     *
     * <p>Reached for through the cause chain because Spring wraps the driver's
     * exception twice, and read reflectively so this class does not have to
     * import the Postgres driver to answer one question about it.
     */
    private static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sql
                    && sql.getClass().getName().startsWith("org.postgresql")) {
                try {
                    Object dm = t.getClass().getMethod("getServerErrorMessage").invoke(t);
                    if (dm != null) {
                        Object name = dm.getClass().getMethod("getConstraint").invoke(dm);
                        if (name != null) {
                            return name.toString();
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not a Postgres error we can read - fall through to the
                    // generic message, which is still better than a 500.
                }
            }
        }
        return null;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }
}
