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
import com.center.common.util.BrandAssets;
import com.center.student.dto.StudentAnalyticsResponse;
import com.center.student.dto.StudentAnalyticsResponse.Entry;
import com.center.student.dto.StudentAnalyticsResponse.Summary;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.WhatsappDocumentSender;
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

    private final StudentRepository studentRepository;
    private final StudentAnalyticsService analyticsService;
    private final UserRepository userRepository;
    private final WhatsappDocumentSender documents;

    /** Reaches {@code reportHtml}'s transaction; a {@code this.} call would not. */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private StudentReportServiceImpl self;

    @Override
    @Transactional(readOnly = true)
    public String fileName(UUID studentId) {
        Student s = student(studentId);
        // Strip whatever a file system would reject; the name itself may be Arabic.
        String safe = s.getName().replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        return "تقرير - " + safe + ".pdf";
    }

    /**
     * The report's markup, read in one short transaction.
     *
     * <p>Split out of {@link #renderPdf} so the transaction covers the reads -
     * including the analytics aggregation, which is the expensive part - and
     * stops before the render. openhtmltopdf laying out a multi-page report with
     * a 434 KB font embedded is CPU work that must not hold one of eight pooled
     * connections while it runs.
     */
    @Transactional(readOnly = true)
    public String reportHtml(UUID studentId) {
        Student student = student(studentId);
        StudentAnalyticsResponse analytics = analyticsService.analytics(studentId);
        User teacher = TenantContext.get() == null
                ? null
                : userRepository.findById(TenantContext.get()).orElse(null);
        return html(student, analytics, teacher);
    }

    /** Deliberately NOT transactional - see {@link #reportHtml}. */
    @Override
    public byte[] renderPdf(UUID studentId) {
        String html = self.reportHtml(studentId);
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

    /**
     * Not transactional. Rendering the PDF and uploading it to WhatsApp both
     * happen here, and the upload is an unbounded network call to a third party;
     * holding a pooled database connection across it starved the pool whenever a
     * few reports were sent at once. Each database read below opens its own short
     * transaction instead.
     */
    @Override
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
        documents.send(phone, pdf, fileName(studentId), "تقرير الطالب: " + s.getName(), "REPORT",
                studentId);
        return phone;
    }

    /** Served from the cached copy - see {@link com.center.common.util.PdfFont}. */
    private InputStream font() {
        return com.center.common.util.PdfFont.stream();
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

        // The whole report sits inside the same blue framed sheet as the barcode.
        // NB: a distinct class from the inner metric ".card" cells - sharing the
        // name let the lighter ".card" rule win and stripped the blue frame.
        b.append("<div class=\"sheet\">");

        // Header cells in source order photo · name · title. openhtmltopdf lays the
        // head-row cells left-to-right in that order, so the teacher photo sits hard
        // against the left edge, the name beside it, and the title on the right.
        // (Inline order can't do this - the ICU bidi reorderer overrides it.)
        b.append("<div class=\"head\"><table class=\"head-row\"><tbody><tr>")
                .append(teacherPhotoCell(teacher))
                .append(teacherNameCell(teacher))
                .append("<td class=\"hb-title\">")
                .append("<div class=\"title\">تقرير الطالب</div>")
                .append("<div class=\"sub\">").append(esc(s.getName())).append("</div>")
                .append("</td>")
                .append("</tr></tbody></table></div>");

        b.append(personalSection(s));
        if (a.hasData()) {
            b.append(summarySection(a.summary()));
            b.append(timelineSection(a.timeline(), s.getLessonPrice()));
        } else {
            b.append("<div class=\"empty\">لا يوجد سجل حضور لهذا الطالب بعد.</div>");
        }

        b.append("</div>");

        // Outside the frame, below it, centred: our logo.
        b.append(brand());
        b.append("</body></html>");
        return b.toString();
    }

    private String personalSection(Student s) {
        StringBuilder b = new StringBuilder();
        b.append("<div class=\"section\">البيانات الشخصية</div><table class=\"info\"><tbody>");
        b.append(infoRow("الاسم", s.getName(), "رقم الطالب", s.getSerial() == null ? null : String.valueOf(s.getSerial())));
        // "الشعبة" is gone - the centre stopped tracking it, so this printed an
        // empty cell on every student. The school takes the freed half.
        b.append(infoRow("الصف", s.getGrade(), "المدرسة", s.getSchool()));
        // Which group the student sits with, and what the lesson costs them.
        b.append(infoRow("المنطقة السكنية", s.getCity(), "المجموعة", groupLabel(s)));
        b.append(infoRow("هاتف الطالب", join(s.getStudentPhones()), "هاتف ولي الأمر", join(s.getParentPhones())));
        b.append(infoRow("سعر الحصة", price(s), "", null));
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
                .append(card("أول حضور", sm.firstAttendance() == null ? "-" : com.center.common.util.ArabicFormat.digits(sm.firstAttendance().format(DATE))))
                .append(card("آخر حضور", sm.lastAttendance() == null ? "-" : com.center.common.util.ArabicFormat.digits(sm.lastAttendance().format(DATE))))
                .append(card("أطول التزام متصل", num(sm.longestStreak())))
                .append(card("ملاحظات الواجب", num(sm.homeworkIssues())))
                .append("</tr></tbody></table>");
        return b.toString();
    }

    private String timelineSection(List<Entry> timeline, BigDecimal studentPrice) {
        StringBuilder b = new StringBuilder();
        // Fixed column widths so every data cell sits exactly under its header;
        // with auto layout openhtmltopdf sizes the thead and tbody rows apart and
        // they drift. Widths sum to 100%.
        b.append("<div class=\"section\">سجل الحصص</div><table class=\"log\"><colgroup>")
                .append("<col style=\"width:4%\" /><col style=\"width:13%\" /><col style=\"width:12%\" />")
                .append("<col style=\"width:12%\" /><col style=\"width:21%\" /><col style=\"width:9%\" />")
                .append("<col style=\"width:11%\" /><col style=\"width:11%\" /><col style=\"width:7%\" />")
                .append("</colgroup><thead><tr>")
                .append("<th>#</th><th>الحصة</th><th>التاريخ</th><th>وقت الحضور</th>")
                .append("<th>المجموعة</th><th>الحالة</th><th>الدرجة</th><th>الواجب</th><th>دفع</th>")
                .append("</tr></thead><tbody>");
        int i = 1;
        for (Entry e : timeline) {
            b.append("<tr>")
                    .append("<td>").append(i++).append("</td>")
                    .append("<td>").append(esc(e.lectureName())).append("</td>")
                    .append("<td>").append(e.date() == null ? "—" : com.center.common.util.ArabicFormat.digits(e.date().format(DATE))).append("</td>")
                    .append("<td>").append(e.attendedAt() == null ? "—" : com.center.common.util.ArabicFormat.time(e.attendedAt())).append("</td>")
                    .append("<td>").append(esc(e.groupName())).append("</td>")
                    .append("<td class=\"").append(e.attended() ? "ok" : "no").append("\">")
                    .append(e.attended() ? "حاضر" : "غائب").append("</td>")
                    .append("<td>").append(gradeText(e)).append("</td>")
                    .append("<td>").append(homeworkText(e)).append("</td>")
                    .append("<td>").append(payText(e, studentPrice)).append("</td>")
                    .append("</tr>");
        }
        b.append("</tbody></table>");
        return b.toString();
    }

    /** Leftmost header cell: the teacher photo, hard against the left edge. */
    private String teacherPhotoCell(User teacher) {
        if (teacher == null || teacher.getPhotoData() == null || teacher.getPhotoData().length == 0) {
            return "";
        }
        String mime = teacher.getPhotoType() == null ? "image/png" : teacher.getPhotoType();
        return "<td class=\"hb-photo\"><img class=\"avatar\" src=\"data:" + esc(mime) + ";base64,"
                + Base64.getEncoder().encodeToString(teacher.getPhotoData()) + "\" alt=\"\" /></td>";
    }

    /** Middle header cell: the teacher name, between the photo and the title. */
    private String teacherNameCell(User teacher) {
        if (teacher == null) {
            return "";
        }
        return "<td class=\"hb-name\">" + esc(teacher.getUsername()) + "</td>";
    }

    /** The centred platform logo, below the framed report. */
    private String brand() {
        String logo = BrandAssets.logoBase64();
        if (logo.isEmpty()) {
            return "";
        }
        return "<div class=\"brand\"><img src=\"data:image/png;base64," + logo + "\" alt=\"\" /></div>";
    }

    /** Grade cell: the score when examined, else "لم يمتحن"; a dash when absent or no exam. */
    private static String gradeText(Entry e) {
        if (!e.attended()) {
            return "—";
        }
        if (e.examTaken() && e.examScore() != null) {
            String s = plain(e.examScore());
            if (e.examMaxScore() != null) {
                s = s + " / " + plain(e.examMaxScore());
            }
            return esc(s);
        }
        return e.hasExam() ? "لم يمتحن" : "—";
    }

    /** Homework cell: the flag when there was an issue, else "معمول"; a dash when absent. */
    private static String homeworkText(Entry e) {
        if (!e.attended()) {
            return "—";
        }
        return e.homeworkFlag() == null ? "معمول" : esc(e.homeworkFlag());
    }

    /** Payment cell: what the student pays per lesson, charged on the days attended. */
    private static String payText(Entry e, BigDecimal studentPrice) {
        if (!e.attended() || studentPrice == null) {
            return "—";
        }
        return esc(plain(studentPrice));
    }

    /**
     * openhtmltopdf lays a row's cells left to right in source order, so the
     * value cell is written first to land on the left of the label it belongs
     * to - the same order the barcode card uses, and the order the labels are
     * read in: title on the right, its value on the left.
     */
    private String infoRow(String l1, String v1, String l2, String v2) {
        return "<tr><td>" + esc(dash(v1)) + "</td><th>" + esc(l1) + "</th>"
                + "<td>" + esc(dash(v2)) + "</td><th>" + esc(l2) + "</th></tr>";
    }

    /** The group's slot - the same label the messages, invoices and card print. */
    private static String groupLabel(Student s) {
        return s.getGroup() == null ? null : com.center.messaging.service.MessageText.groupLabel(s.getGroup());
    }

    /** The student's own price, falling back to the group's official one. */
    private static String price(Student s) {
        BigDecimal v = s.getLessonPrice();
        if (v == null && s.getGroup() != null) {
            v = s.getGroup().getLessonPrice();
        }
        return v == null ? null : com.center.common.util.ArabicFormat.digits(v.stripTrailingZeros().toPlainString());
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
                .sheet { border: 3px solid #3B7A8C; border-radius: 10px; padding: 18px; }
                .head { border-bottom: 2px solid #3B7A8C; padding-bottom: 8px; margin-bottom: 14px; }
                .head-row { width: 100%; border-collapse: collapse; }
                .hb-photo { vertical-align: middle; text-align: left; width: 52px; }
                .hb-photo .avatar { width: 46px; height: 46px; border-radius: 23px;
                                    border: 2px solid #3B7A8C; vertical-align: middle; }
                .hb-name { vertical-align: middle; text-align: left; white-space: nowrap;
                           font-weight: bold; font-size: 11pt; padding-right: 8px; width: 1%; }
                .hb-title { vertical-align: middle; text-align: right; }
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
                .log { table-layout: fixed; }
                .log th { background: #0F172A; color: #fff; padding: 5px; font-size: 8pt;
                          text-align: center; }
                .log td { border-bottom: 1px solid #E2E8F0; padding: 4px 5px;
                          text-align: center; font-size: 8pt; word-wrap: break-word; }
                .log .ok { color: #15803D; }
                .log .no { color: #BE123C; }
                .empty { border: 1px dashed #CBD5E1; padding: 22px; text-align: center; color: #64748B; }
                .brand { text-align: center; margin-top: 16px; }
                .brand img { width: 130px; height: auto; }
                """;
    }
}
