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
        return problem(HttpStatus.FORBIDDEN, "هذه العملية تتطلب صلاحية المدير");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail onOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "تم تعديل هذا السجل من مكان آخر، حدّث الصفحة وأعد المحاولة");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail onUnexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_ERROR);
    }
}
