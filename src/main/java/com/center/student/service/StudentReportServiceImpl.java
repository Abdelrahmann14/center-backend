package com.center.student.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.common.tenant.TenantContext;
import com.center.student.dto.StudentAnalyticsResponse;
import com.center.student.dto.StudentAnalyticsResponse.Entry;
import com.center.student.dto.StudentAnalyticsResponse.Summary;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.GreenApiClient;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Renders the student analytics report to PDF.
 *
 * <p>The document is built as strict XHTML and rendered by openhtmltopdf. Arabic
 * needs three things to come out right: the embedded Noto Kufi Arabic font (PDF
 * base fonts have no Arabic glyphs), the ICU bidi splitter/reorderer for
 * right-to-left run ordering, and an RTL default direction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentReportServiceImpl implements StudentReportService {

    private static final String FONT_PATH = "/fonts/NotoKufiArabic.ttf";
    private static final String FONT_FAMILY = "Noto Kufi Arabic";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final StudentRepository studentRepository;
    private final StudentAnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final GreenApiClient greenApi;

    @Override
    @Transactional(readOnly = true)
    public String fileName(UUID studentId) {
        Student s = student(studentId);
        // Strip whatever a file system would reject; the name itself may be Arabic.
        String safe = s.getName().replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        return "تقرير - " + safe + ".pdf";
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] renderPdf(UUID studentId) {
        Student student = student(studentId);
        StudentAnalyticsResponse analytics = analyticsService.analytics(studentId);
        User teacher = TenantContext.get() == null
                ? null
                : userRepository.findById(TenantContext.get()).orElse(null);

        String html = html(student, analytics, teacher);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);
            builder.useFont(() -> font(), FONT_FAMILY);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception ex) {
            log.error("Student report render failed for {}: {}", studentId, ex.getMessage(), ex);
            throw new BusinessRuleException("تعذّر إنشاء ملف التقرير");
        }
        return out.toByteArray();
    }

    @Override
    @Transactional(readOnly = true)
    public String send(UUID studentId, Recipient recipient) {
        Student s = student(studentId);
        String[] phones = recipient == Recipient.PARENT ? s.getParentPhones() : s.getStudentPhones();
        String phone = phones == null || phones.length == 0 ? null : phones[0];
        if (phone == null || phone.isBlank()) {
            throw new BusinessRuleException(recipient == Recipient.PARENT
                    ? "لا يوجد رقم هاتف لولي الأمر"
                    : "لا يوجد رقم هاتف للطالب");
        }
        byte[] pdf = renderPdf(studentId);
        greenApi.sendDocument(phone, pdf, fileName(studentId), "تقرير الطالب: " + s.getName());
        return phone;
    }

    private InputStream font() {
        InputStream in = getClass().getResourceAsStream(FONT_PATH);
        if (in == null) {
            throw new BusinessRuleException("خط التقرير غير متوفر على الخادم");
        }
        return in;
    }

    private Student student(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("الطالب غير موجود"));
    }

    // ── HTML ────────────────────────────────────────────────────────────────

    private String html(Student s, StudentAnalyticsResponse a, User teacher) {
        StringBuilder b = new StringBuilder(8192);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ar\" dir=\"rtl\"><head>")
                .append("<meta charset=\"UTF-8\" />")
                .append("<title>").append(esc(s.getName())).append("</title>")
                .append("<style>").append(css()).append("</style>")
                .append("</head><body>");

        b.append("<div class=\"head\"><div class=\"title\">تقرير الطالب</div>")
                .append("<div class=\"sub\">").append(esc(s.getName())).append("</div></div>");

        b.append(personalSection(s));
        if (a.hasData()) {
            b.append(summarySection(a.summary()));
            b.append(timelineSection(a.timeline()));
        } else {
            b.append("<div class=\"empty\">لا يوجد سجل حضور لهذا الطالب بعد.</div>");
        }
        b.append(footer(teacher));
        b.append("</body></html>");
        return b.toString();
    }

    private String personalSection(Student s) {
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"section\">البيانات الشخصية</div><table class=\"info\"><tbody>");
        b.append(infoRow("الاسم", s.getName(), "رقم الطالب", s.getSerial() == null ? null : String.valueOf(s.getSerial())));
        b.append(infoRow("الصف", s.getGrade(), "الشعبة", s.getAcademicTrack() == null ? null : s.getAcademicTrack().getValue()));
        b.append(infoRow("المدرسة", s.getSchool(), "المدينة", s.getCity()));
        b.append(infoRow("النوع", s.getGender() == null ? null : s.getGender().getValue(),
                "الديانة", s.getReligion() == null ? null : s.getReligion().getValue()));
        b.append(infoRow("هاتف الطالب", join(s.getStudentPhones()), "هاتف ولي الأمر", join(s.getParentPhones())));
        b.append("</tbody></table>");
        return b.toString();
    }

    private String summarySection(Summary sm) {
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"section\">ملخص الأداء</div><table class=\"cards\"><tbody><tr>")
                .append(card("الحصص المحضورة", num(sm.attendedLessons())))
                .append(card("الحصص الغائبة", num(sm.missedLessons())))
                .append(card("نسبة الحضور", pct(sm.attendancePercent())))
                .append(card("متوسط الاختبارات", pct(sm.averageExamPercent())))
                .append("</tr><tr>")
                .append(card("اختبارات أُدّيت", num(sm.examsTaken())))
                .append(card("اختبارات فائتة", num(sm.examsMissed())))
                .append(card("أعلى درجة", pct(sm.bestExamPercent())))
                .append(card("أقل درجة", pct(sm.worstExamPercent())))
                .append("</tr><tr>")
                .append(card("أول حضور", sm.firstAttendance() == null ? "-" : sm.firstAttendance().format(DATE)))
                .append(card("آخر حضور", sm.lastAttendance() == null ? "-" : sm.lastAttendance().format(DATE)))
                .append(card("أطول التزام متصل", num(sm.longestStreak())))
                .append(card("ملاحظات الواجب", num(sm.homeworkIssues())))
                .append("</tr></tbody></table>");
        return b.toString();
    }

    private String timelineSection(List<Entry> timeline) {
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"section\">سجل الحصص</div><table class=\"log\"><thead><tr>")
                .append("<th>#</th><th>الحصة</th><th>التاريخ</th><th>وقت الحضور</th>")
                .append("<th>المجموعة</th><th>الحالة</th><th>الاختبار</th><th>الدرجة</th>")
                .append("</tr></thead><tbody>");
        int i = 1;
        for (Entry e : timeline) {
            b.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td>").append(esc(e.lectureName())).append("</td>")
                    .append("<td>").append(e.date() == null ? "-" : e.date().format(DATE)).append("</td>")
                    .append("<td>").append(e.attendedAt() == null ? "-" : e.attendedAt().format(TIME)).append("</td>")
                    .append("<td>").append(esc(e.groupName())).append("</td>")
                    .append("<td class=\"").append(e.attended() ? "ok" : "no").append("\">")
                    .append(e.attended() ? "حاضر" : "غائب").append("</td>")
                    .append("<td>").append(e.examTaken() ? "نعم" : "-").append("</td>")
                    .append("<td>").append(scoreText(e)).append("</td>")
                    .append("</tr>");
        }
        b.append("</tbody></table>");
        return b.toString();
    }

    private String footer(User teacher) {
        if (teacher == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"foot\">");
        if (teacher.getPhotoData() != null && teacher.getPhotoData().length > 0) {
            String mime = teacher.getPhotoType() == null ? "image/png" : teacher.getPhotoType();
            b.append("<img class=\"avatar\" src=\"data:").append(esc(mime)).append(";base64,")
                    .append(Base64.getEncoder().encodeToString(teacher.getPhotoData()))
                    .append("\" alt=\"\" />");
        }
        b.append("<div class=\"who\"><div class=\"name\">").append(esc(teacher.getUsername()))
                .append("</div><div class=\"role\">المدرّس</div></div></div>");
        return b.toString();
    }

    private static String scoreText(Entry e) {
        if (e.examScore() == null) {
            return "-";
        }
        String s = plain(e.examScore());
        if (e.examMaxScore() != null) {
            s = s + " / " + plain(e.examMaxScore());
        }
        return esc(s);
    }

    private String infoRow(String l1, String v1, String l2, String v2) {
        return "<tr><th>" + esc(l1) + "</th><td>" + esc(dash(v1)) + "</td>"
                + "<th>" + esc(l2) + "</th><td>" + esc(dash(v2)) + "</td></tr>";
    }

    private String card(String label, String value) {
        return "<td><div class=\"card\"><div class=\"v\">" + esc(value)
                + "</div><div class=\"l\">" + esc(label) + "</div></div></td>";
    }

    private static String num(long v) {
        return String.valueOf(v);
    }

    private static String pct(BigDecimal v) {
        return v == null ? "-" : plain(v) + "%";
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static String join(String[] values) {
        return values == null || values.length == 0 ? null : String.join("، ", values);
    }

    private static String dash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    /** XHTML must stay well-formed, so every interpolated value is escaped. */
    private static String esc(String v) {
        if (v == null) {
            return "-";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String css() {
        return """
                @page { size: A4; margin: 18mm 12mm; }
                body { font-family: "Noto Kufi Arabic"; direction: rtl; color: #0F172A;
                       font-size: 9pt; line-height: 1.6; }
                .head { border-bottom: 2px solid #3B7A8C; padding-bottom: 8px; margin-bottom: 14px; }
                .title { font-size: 17pt; font-weight: bold; color: #3B7A8C; }
                .sub { font-size: 12pt; margin-top: 2px; }
                .section { background: #0F172A; color: #fff; padding: 5px 9px;
                           font-size: 10pt; font-weight: bold; margin: 14px 0 7px; }
                table { width: 100%; border-collapse: collapse; }
                .info th { background: #F1F5F9; text-align: right; width: 15%;
                           font-weight: normal; color: #475569; }
                .info th, .info td { border: 1px solid #E2E8F0; padding: 5px 7px; }
                .cards td { width: 25%; padding: 3px; }
                .card { border: 1px solid #E2E8F0; border-radius: 6px; padding: 7px; text-align: center; }
                .card .v { font-size: 13pt; font-weight: bold; color: #3B7A8C; }
                .card .l { font-size: 7.5pt; color: #64748B; }
                .log th { background: #0F172A; color: #fff; padding: 5px; font-size: 8pt; }
                .log td { border-bottom: 1px solid #E2E8F0; padding: 4px 5px;
                          text-align: center; font-size: 8pt; }
                .log .ok { color: #15803D; }
                .log .no { color: #BE123C; }
                .empty { border: 1px dashed #CBD5E1; padding: 22px; text-align: center; color: #64748B; }
                .foot { margin-top: 22px; padding-top: 9px; border-top: 1px solid #E2E8F0; }
                .avatar { width: 42px; height: 42px; border-radius: 21px; vertical-align: middle; }
                .who { display: inline-block; vertical-align: middle; padding-right: 9px; }
                .who .name { font-weight: bold; font-size: 10pt; }
                .who .role { color: #64748B; font-size: 8pt; }
                """;
    }
}
