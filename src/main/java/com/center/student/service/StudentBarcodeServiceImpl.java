package com.center.student.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.exception.BusinessRuleException;
import com.center.common.exception.ResourceNotFoundException;
import com.center.common.tenant.TenantContext;
import com.center.common.util.BrandAssets;
import com.center.student.entity.Student;
import com.center.student.repository.StudentRepository;
import com.center.user.entity.User;
import com.center.user.repository.UserRepository;
import com.center.whatsapp.service.WhatsappDocumentSender;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.openhtmltopdf.bidi.support.ICUBidiReorderer;
import com.openhtmltopdf.bidi.support.ICUBidiSplitter;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds the student's barcode card as a PDF. The barcode encodes ONLY the
 * student code (serial), so scanning it - on paper or on screen - yields the
 * code directly, without opening the PDF. Same Arabic/RTL rendering setup as the
 * analytics report.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentBarcodeServiceImpl implements StudentBarcodeService {

    private static final String FONT_PATH = "/fonts/NotoKufiArabic.ttf";
    private static final String FONT_FAMILY = "Noto Kufi Arabic";
    private static final String NOT_FOUND = "الطالب غير موجود";

    /** Barcode bitmap size in pixels; wide bars scan reliably on a laser reader. */
    private static final int BARCODE_WIDTH = 900;
    private static final int BARCODE_HEIGHT = 240;

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final WhatsappDocumentSender documents;

    /** Reaches {@code cardHtml}'s transaction; a {@code this.} call would not. */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private StudentBarcodeServiceImpl self;

    @Override
    @Transactional(readOnly = true)
    public String fileName(UUID studentId) {
        Student s = student(studentId);
        String safe = s.getName().replaceAll("[\\\\/:*?\"<>|]", " ").trim();
        return "باركود - " + safe + ".pdf";
    }

    /**
     * The card's markup, read in one short transaction.
     *
     * <p>Split out of {@link #renderPdf} so a transaction covers the reads and
     * nothing else. openhtmltopdf laying out a page and embedding a 434 KB font
     * is CPU work; a pooled database connection has no reason to be checked out
     * for any of it, and there are only eight of them.
     */
    @Transactional(readOnly = true)
    public String cardHtml(UUID studentId) {
        Student student = student(studentId);
        String code = code(student);
        User teacher = TenantContext.get() == null
                ? null
                : userRepository.findById(TenantContext.get()).orElse(null);
        return html(student, code, barcodePngBase64(code), teacher);
    }

    /** Deliberately NOT transactional - see {@link #cardHtml}. */
    @Override
    public byte[] renderPdf(UUID studentId) {
        String html = self.cardHtml(studentId);

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
            log.error("Barcode card render failed for {}: {}", studentId, ex.getMessage(), ex);
            throw new BusinessRuleException("تعذّر إنشاء بطاقة الباركود");
        }
        return out.toByteArray();
    }

    /**
     * Not transactional: the WhatsApp upload below is a third party's network,
     * and a pooled database connection must not be held across it. Each read
     * opens its own short transaction. Same reasoning as the student report.
     */
    @Override
    public String send(UUID studentId) {
        Student s = student(studentId);
        String[] phones = s.getStudentPhones();
        String phone = phones == null || phones.length == 0 ? null : phones[0];
        if (phone == null || phone.isBlank()) {
            throw new BusinessRuleException("لا يوجد رقم هاتف للطالب");
        }
        byte[] pdf = renderPdf(studentId);
        documents.send(phone, pdf, fileName(studentId), "باركود الطالب: " + s.getName(), "BARCODE",
                studentId);
        return phone;
    }

    /** The student code the barcode carries. Never null for a saved student. */
    private static String code(Student s) {
        if (s.getSerial() == null) {
            throw new BusinessRuleException("لا يوجد كود لهذا الطالب بعد");
        }
        return String.valueOf(s.getSerial());
    }

    /** Code128 of the student code, as a base64 PNG for embedding in the HTML. */
    private static String barcodePngBase64(String code) {
        try {
            BitMatrix matrix = new Code128Writer()
                    .encode(code, BarcodeFormat.CODE_128, BARCODE_WIDTH, BARCODE_HEIGHT);
            BufferedImage img = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception ex) {
            throw new BusinessRuleException("تعذّر توليد الباركود");
        }
    }

    /** Served from the cached copy - see {@link com.center.common.util.PdfFont}. */
    private InputStream font() {
        return com.center.common.util.PdfFont.stream();
    }

    private Student student(UUID studentId) {
        return studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    // ── HTML ────────────────────────────────────────────────────────────────

    private String html(Student s, String code, String barcodePng, User teacher) {
        StringBuilder b = new StringBuilder(4096);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ar\" dir=\"rtl\"><head>")
                .append("<meta charset=\"UTF-8\" />")
                .append("<title>").append(esc(s.getName())).append("</title>")
                .append("<style>").append(css()).append("</style>")
                .append("</head><body>");

        b.append("<div class=\"card\">");

        // Header cells in source order photo · name · title. openhtmltopdf lays the
        // head-row cells left-to-right in that order, so the teacher photo sits hard
        // against the left edge, the name beside it, and the card title on the right.
        // (Inline order can't do this - the ICU bidi reorderer overrides it, so
        // separate cells are the only reliable control.)
        b.append("<div class=\"head\"><table class=\"head-row\"><tbody><tr>")
                .append(teacherPhotoCell(teacher))
                .append(teacherNameCell(teacher))
                .append("<td class=\"hb-title\">")
                .append("<div class=\"title\">بطاقة الطالب</div>")
                .append("<div class=\"sub\">").append(esc(s.getName())).append("</div>")
                .append("</td>")
                .append("</tr></tbody></table></div>");

        // The barcode is the centrepiece: large and high-contrast. The code itself
        // is not printed under it - it already appears in the table below.
        b.append("<div class=\"barcode\">")
                .append("<img src=\"data:image/png;base64,").append(barcodePng).append("\" alt=\"\" />")
                .append("</div>");

        b.append("<table class=\"info\"><tbody>");
        b.append(infoRow("الاسم", s.getName(), "كود الطالب", code));
        // "الشعبة" used to sit beside the grade and is gone - the centre stopped
        // tracking it, so the card was printing an empty cell on every student.
        // The school moves up to keep the row a pair rather than leaving a hole.
        b.append(infoRow("الصف", s.getGrade(), "المدرسة", s.getSchool()));
        b.append(infoRow("المنطقة السكنية", s.getCity(), "المجموعة", groupLabel(s)));
        b.append(infoRow("هاتف الطالب", join(s.getStudentPhones()),
                "هاتف ولي الأمر", join(s.getParentPhones())));
        b.append(infoRow("سعر الحصة", price(s), "", null));
        b.append("</tbody></table>");
        b.append("</div>");

        // Outside the frame, below it, centred: our logo.
        b.append(brand());

        b.append("</body></html>");
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

    /** The centred platform logo, below the framed card. */
    private String brand() {
        String logo = BrandAssets.logoBase64();
        if (logo.isEmpty()) {
            return "";
        }
        return "<div class=\"brand\"><img src=\"data:image/png;base64," + logo + "\" alt=\"\" /></div>";
    }

    /**
     * openhtmltopdf lays a row's cells left to right in source order, so the
     * value cell is written first to land on the left of the label it belongs to.
     */
    private String infoRow(String l1, String v1, String l2, String v2) {
        return "<tr><td>" + esc(dash(v1)) + "</td><th>" + esc(l1) + "</th>"
                + "<td>" + esc(dash(v2)) + "</td><th>" + esc(l2) + "</th></tr>";
    }

    /** The group's slot - the same label the messages and invoices print. */
    private static String groupLabel(Student s) {
        return s.getGroup() == null ? null : com.center.messaging.service.MessageText.groupLabel(s.getGroup());
    }

    /** The student's own price, falling back to the group's official one. */
    private static String price(Student s) {
        java.math.BigDecimal v = s.getLessonPrice();
        if (v == null && s.getGroup() != null) {
            v = s.getGroup().getLessonPrice();
        }
        return v == null ? null : com.center.common.util.ArabicFormat.digits(v.stripTrailingZeros().toPlainString());
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
                @page { size: A4; margin: 22mm 16mm; }
                body { font-family: "Noto Kufi Arabic"; direction: rtl; color: #0F172A;
                       font-size: 10pt; line-height: 1.6; }
                .card { border: 3px solid #3B7A8C; border-radius: 10px; padding: 20px; }
                .head { border-bottom: 2px solid #3B7A8C; padding-bottom: 10px; margin-bottom: 16px; }
                .head-row { width: 100%; border-collapse: collapse; }
                .hb-photo { vertical-align: middle; text-align: left; width: 56px; }
                .hb-photo .avatar { width: 50px; height: 50px; border-radius: 25px;
                                    border: 2px solid #3B7A8C; vertical-align: middle; }
                .hb-name { vertical-align: middle; text-align: left; white-space: nowrap;
                           font-weight: bold; font-size: 12pt; padding-right: 8px; width: 1%; }
                .hb-title { vertical-align: middle; text-align: right; }
                .title { font-size: 18pt; font-weight: bold; color: #3B7A8C; }
                .sub { font-size: 13pt; margin-top: 3px; }
                .barcode { text-align: center; margin: 8px 0 16px; }
                .barcode img { width: 78%; height: auto; }
                table.info { width: 100%; border-collapse: collapse; }
                .info th { background: #F1F5F9; text-align: right; width: 16%;
                           font-weight: normal; color: #475569; }
                .info th, .info td { border: 1px solid #E2E8F0; padding: 7px 9px; }
                .brand { text-align: center; margin-top: 18px; }
                .brand img { width: 130px; height: auto; }
                """;
    }
}
