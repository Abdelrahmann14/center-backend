package com.center.messaging.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.center.common.exception.BusinessRuleException;

import lombok.extern.slf4j.Slf4j;

/**
 * Renders a message body into a small white PNG, sized to its content, for the
 * templates whose "send as image" flag is on. The text is laid out right-to-left
 * with the same Noto Kufi Arabic font the PDF reports use, so Arabic shapes and
 * wraps correctly (raw {@code drawString} would not reorder or shape it).
 */
@Component
@Slf4j
public class MessageImageRenderer {

    private static final String FONT_PATH = "/fonts/NotoKufiArabic.ttf";
    private static final float FONT_SIZE = 30f;
    private static final int PADDING = 40;
    private static final float MAX_TEXT_WIDTH = 620f;
    private static final Color BG = Color.WHITE;
    private static final Color FG = new Color(0x0F, 0x17, 0x2A);

    private static final Font FONT = loadFont();

    private static Font loadFont() {
        try (InputStream in = MessageImageRenderer.class.getResourceAsStream(FONT_PATH)) {
            if (in == null) {
                log.warn("Font {} not found on classpath; falling back to a system font", FONT_PATH);
                return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(FONT_SIZE));
            }
            return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(FONT_SIZE);
        } catch (Exception ex) {
            log.warn("Failed to load font {}: {}", FONT_PATH, ex.getMessage());
            return new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(FONT_SIZE));
        }
    }

    /** The rendered PNG bytes for {@code text}. Bold {@code *markers*} are stripped. */
    public byte[] render(String text) {
        String clean = (text == null ? "" : text).replace("*", "").strip();
        if (clean.isEmpty()) {
            clean = " ";
        }

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        List<TextLayout> lines = layout(clean, pg.getFontRenderContext());
        float maxWidth = 0f;
        float totalHeight = 0f;
        for (TextLayout line : lines) {
            maxWidth = Math.max(maxWidth, line.getAdvance());
            totalHeight += line.getAscent() + line.getDescent() + line.getLeading();
        }
        pg.dispose();

        int width = Math.max(160, Math.round(maxWidth) + PADDING * 2);
        int height = Math.max(110, Math.round(totalHeight) + PADDING * 2);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, width, height);
        g.setColor(FG);

        float y = PADDING;
        for (TextLayout line : lines) {
            y += line.getAscent();
            // Right-align each line (RTL): the pen sits at the line's right edge.
            float x = width - PADDING - line.getAdvance();
            line.draw(g, x, y);
            y += line.getDescent() + line.getLeading();
        }
        g.dispose();

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessRuleException("تعذّر إنشاء صورة الرسالة");
        }
    }

    /** Wraps each paragraph to {@link #MAX_TEXT_WIDTH}, honouring RTL and newlines. */
    private List<TextLayout> layout(String text, FontRenderContext frc) {
        List<TextLayout> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add(new TextLayout(" ", FONT, frc));
                continue;
            }
            AttributedString styled = new AttributedString(paragraph);
            styled.addAttribute(TextAttribute.FONT, FONT);
            styled.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
            AttributedCharacterIterator it = styled.getIterator();
            LineBreakMeasurer measurer = new LineBreakMeasurer(it, frc);
            measurer.setPosition(it.getBeginIndex());
            while (measurer.getPosition() < it.getEndIndex()) {
                lines.add(measurer.nextLayout(MAX_TEXT_WIDTH));
            }
        }
        return lines;
    }
}
