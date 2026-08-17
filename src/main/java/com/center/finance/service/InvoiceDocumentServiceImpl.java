package com.center.finance.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.center.common.enums.FinanceEntryKind;
import com.center.common.exception.BusinessRuleException;
import com.center.common.tenant.TenantContext;
import com.center.common.util.BrandAssets;
import com.center.finance.dto.FinanceEntryResponse;
import com.center.finance.dto.InvoiceLineResponse;
import com.center.finance.dto.InvoiceResponse;
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
 * Renders a lesson invoice to PDF and hands it to WhatsApp.
 *
 * <p>Built the same way as the student report: strict XHTML through
 * openhtmltopdf, with the embedded Noto Kufi Arabic font and the ICU bidi
 * splitter/reorderer, because PDF base fonts carry no Arabic glyphs and RTL runs
 * come out reversed without them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceDocumentServiceImpl implements InvoiceDocumentService {

    private static final String FONT_PATH = "/fonts/NotoKufiArabic.ttf";
    private static final String FONT_FAMILY = "Noto Kufi Arabic";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final FinanceService financeService;
    private final UserRepository userRepository;
    private final GreenApiClient greenApi;

    /** Reaches {@code invoiceHtml}'s transaction; a {@code this.} call would not. */
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private InvoiceDocumentServiceImpl self;

    @Override
    @Transactional(readOnly = true)
    public String fileName(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        InvoiceResponse invoice = financeService.invoice(lectureId, groupId, sessionDate);
        // The teacher receives these on WhatsApp, where the file name is all they
        // see before opening it - so it has to identify the session on its own:
        // lesson, stage, group (which carries the weekday and time), and date.
        return "فاتورة - " + String.join(" - ",
                safe(invoice.lectureName()),
                safe(invoice.grade()),
                safe(invoice.groupLabel()),
                sessionDate.toString()) + ".pdf";
    }

    /** Drops what a file system rejects, and the separators the name adds itself. */
    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\\\/:*?\"<>|]", " ").replace("·", " ").replaceAll("\\s+", " ").strip();
    }

    /**
     * The invoice's markup, read in one short transaction.
     *
     * <p>Split out of {@link #renderPdf} so the transaction covers the invoice
     * aggregation and stops before the render. openhtmltopdf embedding a 434 KB
     * font is CPU work; it must not hold one of eight pooled connections.
     */
    @Transactional(readOnly = true)
    public String invoiceHtml(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        InvoiceResponse invoice = financeService.invoice(lectureId, groupId, sessionDate);
        User teacher = TenantContext.get() == null
                ? null
                : userRepository.findById(TenantContext.get()).orElse(null);
        return html(invoice, teacher);
    }

    /** Deliberately NOT transactional - see {@link #invoiceHtml}. */
    @Override
    public byte[] renderPdf(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        String markup = self.invoiceHtml(lectureId, groupId, sessionDate);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useUnicodeBidiSplitter(new ICUBidiSplitter.ICUBidiSplitterFactory());
            builder.useUnicodeBidiReorderer(new ICUBidiReorderer());
            builder.defaultTextDirection(BaseRendererBuilder.TextDirection.RTL);
            builder.useFont(this::font, FONT_FAMILY);
            builder.withHtmlContent(markup, null);
            builder.toStream(out);
            builder.run();
        } catch (Exception ex) {
            log.error("Invoice render failed for {}/{}/{}: {}",
                    lectureId, groupId, sessionDate, ex.getMessage(), ex);
            throw new BusinessRuleException("تعذّر إنشاء ملف الفاتورة");
        }
        return out.toByteArray();
    }

    /**
     * Deliberately NOT transactional. It renders a PDF and then uploads it to
     * Green API; both were previously inside a read transaction, so a pooled
     * connection was held across a multipart upload to a third party. Each read
     * below opens its own short transaction instead - the same reasoning as the
     * student report and barcode senders.
     */
    @Override
    public String sendToAdmin(UUID lectureId, UUID groupId, LocalDate sessionDate) {
        UUID workspace = TenantContext.get();
        User admin = workspace == null ? null : userRepository.findById(workspace).orElse(null);
        String phone = admin == null ? null : admin.getPhone();
        if (phone == null || phone.isBlank()) {
            throw new BusinessRuleException("لا يوجد رقم واتساب مسجّل للمدرّس");
        }
        // The invoice aggregation used to be run here as well and then thrown
        // away unused; renderPdf and fileName each run it for what they need.
        byte[] pdf = renderPdf(lectureId, groupId, sessionDate);
        // The session (lesson/stage/group/date) is already spelled out in the file
        // name, so the accompanying message stays generic and repeats none of it.
        greenApi.sendDocument(phone, pdf, fileName(lectureId, groupId, sessionDate),
                "الفاتورة المالية للحصة", "INVOICE");
        return phone;
    }

    /** Served from the cached copy - see {@link com.center.common.util.PdfFont}. */
    private InputStream font() {
        return com.center.common.util.PdfFont.stream();
    }

    // ── HTML ────────────────────────────────────────────────────────────────

    private String html(InvoiceResponse i, User teacher) {
        StringBuilder b = new StringBuilder(6144);
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ar\" dir=\"rtl\"><head>")
                .append("<meta charset=\"UTF-8\" />")
                .append("<title>").append(esc(i.lectureName())).append("</title>")
                .append("<style>").append(css()).append("</style>")
                .append("</head><body>");

        // The whole invoice sits inside the same blue framed card as the barcode.
        b.append("<div class=\"card\">");

        // Header cells in source order photo · name · title. openhtmltopdf lays the
        // head-row cells left-to-right in that order, so the teacher photo sits hard
        // against the left edge, the name beside it, and the lesson name on the right.
        // (Inline order can't do this - the ICU bidi reorderer overrides it.)
        b.append("<div class=\"head\"><table class=\"head-row\"><tbody><tr>")
                .append(teacherPhotoCell(teacher))
                .append(teacherNameCell(teacher))
                .append("<td class=\"hb-title\">")
                .append("<div class=\"title\">").append(esc(i.lectureName())).append("</div>")
                .append("<div class=\"sub\">").append(esc(i.sessionDate().format(DATE)))
                .append(" · ").append(esc(i.groupLabel())).append("</div>")
                .append("</td>")
                .append("</tr></tbody></table></div>");

        b.append("<div class=\"section\">بيانات الحصة</div><table class=\"info\"><tbody>")
                .append(infoRow("الحصة", i.lectureName(), "التاريخ", i.sessionDate().format(DATE)))
                .append(infoRow("المجموعة", i.groupLabel(), "السنتر", i.centerName()))
                .append(infoRow("الصف", i.grade(), "عدد طلاب المجموعة", String.valueOf(i.students())))
                .append(infoRow("الحاضرون", String.valueOf(i.attended()),
                        "سعر الحصة", money(i.lessonPrice())))
                .append("</tbody></table>");

        // Who ran the session with the teacher. Omitted entirely when nobody was
        // marked, so an empty list never prints a bare heading.
        if (i.attendees() != null && !i.attendees().isEmpty()) {
            b.append("<div class=\"section\">المساعدون الحاضرون</div>")
                    .append("<div class=\"attendees\">")
                    .append(esc(String.join(" · ", i.attendees())))
                    .append("</div>");
        }

        // Cells are laid left to right in source order, so the columns are written
        // backwards: read from the right, a line says البيان · الفئة · العدد ·
        // الإجمالي - what it is, what one costs, how many, what that comes to.
        b.append("<div class=\"section\">التفاصيل المالية</div>")
                .append("<table class=\"lines\"><thead><tr>")
                .append("<th>الإجمالي</th><th>العدد</th><th>الفئة</th><th class=\"right\">البيان</th>")
                .append("</tr></thead><tbody>");
        for (InvoiceLineResponse line : i.lines()) {
            String label = line.price() == null
                    ? "بدون سعر محدد"
                    : line.price().signum() == 0 ? "إعفاء" : line.discounted() ? "بخصم" : "بالسعر الكامل";
            b.append("<tr>")
                    .append("<td>").append(esc(money(line.subtotal()))).append("</td>")
                    .append("<td>").append(line.count()).append("</td>")
                    .append("<td>").append(line.price() == null ? "-" : esc(money(line.price()))).append("</td>")
                    .append("<td class=\"right\">").append(esc(label)).append("</td>")
                    .append("</tr>");
        }
        b.append("</tbody></table>");

        b.append("<table class=\"totals\"><tbody>")
                .append(totalRow("إجمالي تحصيل الحصة", money(i.gross()), null))
                .append(totalRow("نسبة السنتر (" + plain(i.percentage()) + "%)",
                        "- " + money(i.centerCut()), "minus"))
                .append(totalRow("الصافي بعد نسبة السنتر", money(i.netAfterCut()), null));

        for (FinanceEntryResponse entry : i.entries()) {
            boolean income = entry.kind() == FinanceEntryKind.INCOME;
            b.append(totalRow((income ? "إيراد: " : "مصروف: ") + entry.description(),
                    (income ? "+ " : "- ") + money(entry.amount()),
                    income ? "plus" : "minus"));
        }
        b.append("</tbody></table>");

        b.append("<div class=\"stamp\"><span class=\"l\">الصافي المستحق</span>")
                .append("<span class=\"v\">").append(esc(money(i.total()))).append("</span></div>");

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

    /** The centred platform logo. */
    private String brand() {
        String logo = BrandAssets.logoBase64();
        if (logo.isEmpty()) {
            return "";
        }
        return "<div class=\"brand\"><img src=\"data:image/png;base64," + logo + "\" alt=\"\" /></div>";
    }

    /**
     * openhtmltopdf lays a row's cells left to right in source order, so the
     * value cell is written first to land on the left of the label it belongs
     * to - the same order the barcode card and the student report use: title on
     * the right, its value on the left.
     */
    private static String infoRow(String l1, String v1, String l2, String v2) {
        return "<tr><td>" + esc(dash(v1)) + "</td><th>" + esc(l1) + "</th>"
                + "<td>" + esc(dash(v2)) + "</td><th>" + esc(l2) + "</th></tr>";
    }

    /** Same rule as {@link #infoRow}: the amount on the left, what it is on the right. */
    private static String totalRow(String label, String value, String tone) {
        return "<tr class=\"" + (tone == null ? "" : tone) + "\">"
                + "<td class=\"num\">" + esc(value) + "</td>"
                + "<td class=\"right\">" + esc(label) + "</td></tr>";
    }

    /** Whole pounds, bare. The document is in EGP throughout, so it never says so. */
    private static String money(BigDecimal v) {
        return v == null ? "0" : v.setScale(0, java.math.RoundingMode.CEILING).toPlainString();
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
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
                @page { size: A5; margin: 14mm 12mm; }
                body { font-family: "Noto Kufi Arabic"; direction: rtl; color: #0F172A;
                       font-size: 9pt; line-height: 1.6; }
                .card { border: 3px solid #3B7A8C; border-radius: 10px; padding: 16px; }
                .head { border-bottom: 2px solid #3B7A8C; padding-bottom: 8px; margin-bottom: 12px; }
                .eyebrow { font-size: 7.5pt; color: #64748B; letter-spacing: 1px; }
                .title { font-size: 16pt; font-weight: bold; color: #3B7A8C; }
                .sub { font-size: 9pt; color: #475569; margin-top: 2px; }
                .section { background: #0F172A; color: #fff; padding: 4px 8px;
                           font-size: 9pt; font-weight: bold; margin: 12px 0 6px; }
                table { width: 100%; border-collapse: collapse; }
                .info th { background: #F1F5F9; text-align: right; width: 20%;
                           font-weight: normal; color: #475569; }
                .info th, .info td { border: 1px solid #E2E8F0; padding: 4px 6px; }
                .attendees { border: 1px solid #E2E8F0; padding: 5px 8px; font-size: 8.5pt;
                             color: #0F172A; }
                .lines th { background: #F1F5F9; color: #475569; padding: 4px;
                            font-size: 8pt; border-bottom: 1px solid #CBD5E1;
                            text-align: center; }
                .lines td { border-bottom: 1px solid #E2E8F0; padding: 4px 6px;
                            text-align: center; font-size: 8.5pt; }
                /* The numbers stay centred under their headings; only البيان,
                   which is words, hugs the right edge it is read from. Needs the
                   .lines prefix to outweigh the two rules above. */
                .lines th.right, .lines td.right { text-align: right; }
                .right { text-align: right; }
                .num { text-align: left; font-weight: bold; }
                .totals { margin-top: 10px; border-top: 1px dashed #94A3B8; }
                .totals td { padding: 4px 6px; font-size: 8.5pt; border-bottom: 1px solid #F1F5F9; }
                .totals .minus .num { color: #BE123C; }
                .totals .plus .num { color: #15803D; }
                .stamp { margin-top: 12px; background: #0F172A; color: #fff; padding: 9px 12px; }
                .stamp .l { font-size: 9pt; }
                .stamp .v { font-size: 15pt; font-weight: bold; float: left; }
                .head-row { width: 100%; border-collapse: collapse; }
                .hb-photo { vertical-align: middle; text-align: left; width: 52px; }
                .hb-photo .avatar { width: 46px; height: 46px; border-radius: 23px;
                                    border: 2px solid #3B7A8C; vertical-align: middle; }
                .hb-name { vertical-align: middle; text-align: left; white-space: nowrap;
                           font-weight: bold; font-size: 12pt; padding-right: 8px; width: 1%; }
                .hb-title { vertical-align: middle; text-align: right; }
                .brand { text-align: center; margin-top: 16px; }
                .brand img { width: 110px; height: auto; }
                """;
    }
}
