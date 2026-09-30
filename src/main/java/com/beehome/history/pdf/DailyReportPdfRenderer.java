package com.beehome.history.pdf;

import com.beehome.history.dto.HistoryDetail;
import com.beehome.history.exception.ReportException;
import com.beehome.media.service.MediaService;
import java.awt.image.BufferedImage;
import java.awt.*;
import java.io.*;
import java.text.Normalizer;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DailyReportPdfRenderer {
    private static final Logger log = LoggerFactory.getLogger(DailyReportPdfRenderer.class);
    private static final PDFont FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final MediaService media;
    private final int maxBytes;
    private final Semaphore slots;
    public DailyReportPdfRenderer(MediaService media,
            @Value("${app.reports.max-pdf-bytes:8388608}") int maxBytes,
            @Value("${app.reports.max-concurrent-pdfs:2}") int maxConcurrent) {
        if (maxBytes < 1 || maxConcurrent < 1) throw new IllegalArgumentException("PDF budgets must be positive");
        this.media = media;
        this.maxBytes = maxBytes;
        this.slots = new Semaphore(maxConcurrent);
    }

    public byte[] render(UUID user, UUID family, HistoryDetail day) {
        return render(user, family, () -> day).bytes();
    }

    public RenderedReport render(UUID user, UUID family, Supplier<HistoryDetail> loadDay) {
        if (!slots.tryAcquire()) throw ReportException.busy();
        long assemblyStart = System.nanoTime();
        try {
            var day = loadDay.get();
            long renderStart = System.nanoTime();
            log.info("Daily PDF sources assembled: durationMs={}",
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(renderStart - assemblyStart));
            return new RenderedReport(day, generate(user, family, day, renderStart));
        } finally {
            slots.release();
        }
    }

    public record RenderedReport(HistoryDetail day, byte[] bytes) {}

    private byte[] generate(UUID user, UUID family, HistoryDetail day, long start) {
        try (var document = new PDDocument(); var output = new LimitedOutputStream(maxBytes)) {
            try (var page = new Writer(document)) {
                page.heading("Daily report");
                page.line(day.childName());
                page.line(day.date().toString());
                if (!day.photos().isEmpty()) {
                    page.section("Photos");
                    for (var record : day.photos()) {
                        page.optional("Description", record.description());
                        if (!record.tags().isEmpty()) page.line("Tags: " + record.tags().stream().map(t -> t.name()).reduce((a,b) -> a + ", " + b).orElse(""));
                        for (var image : record.media()) {
                            try {
                                var content = media.content(user, family, image.id());
                                try (var input = content.resource().getInputStream()) {
                                    BufferedImage bitmap = preview(input);
                                    if (bitmap != null) {
                                        try { page.image(bitmap); }
                                        finally { bitmap.flush(); }
                                    }
                                }
                            } catch (RuntimeException | IOException ignored) {
                                // A missing or unreadable image does not discard the rest of the report.
                            }
                        }
                    }
                }
                if (!day.studies().isEmpty()) {
                    page.section("Studies");
                    for (var study : day.studies()) {
                        page.subheading(study.subjectName() == null ? "Study" : study.subjectName());
                        page.optional("Topic", study.topic() == null ? study.title() : study.topic());
                        if (study.accumulatedDurationSeconds() > 0)
                            page.line("Duration: " + study.accumulatedDurationSeconds() / 60 + " minutes");
                        page.optional("Description", study.description() == null ? study.notes() : study.description());
                        page.optional("Comments", study.comments());
                        page.optional("Material", study.material());
                        page.pages(study.startPage(), study.endPage());
                    }
                }
                if (!day.extracurricularActivities().isEmpty()) {
                    page.section("Extracurricular activities");
                    for (var activity : day.extracurricularActivities()) {
                        page.subheading(activity.activity().name());
                        page.optional("Topic", activity.topic());
                        if (activity.durationMinutes() != null) page.line("Duration: " + activity.durationMinutes() + " minutes");
                        page.optional("Description", activity.description());
                        page.optional("Comments", activity.comments());
                        page.optional("Material", activity.material());
                        page.pages(activity.startPage(), activity.endPage());
                    }
                }
                if (!day.reading().isEmpty()) {
                    page.section("Reading");
                    for (var item : day.reading()) {
                        page.subheading(item.bookTitle());
                        if (item.minutes() != null) page.line("Duration: " + item.minutes() + " minutes");
                        if (item.pagesRead() != null) page.line("Pages read: " + item.pagesRead());
                    }
                }
                if (day.routine() != null) {
                    page.section("Routine");
                    for (var item : day.routine().items()) {
                        page.line(item.title() + " - " + item.status().name());
                        page.optional("Description", item.description());
                    }
                }
            }
            document.save(output);
            byte[] result = output.toByteArray();
            log.info("Daily PDF generated: pages={}, bytes={}, durationMs={}", document.getNumberOfPages(),
                    result.length, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
            return result;
        } catch (LimitExceededException e) { throw ReportException.tooLarge(); }
        catch (IOException e) { throw new IllegalStateException("PDF rendering failed", e); }
    }

    private static final class LimitExceededException extends IOException {}

    private static BufferedImage preview(InputStream input) throws IOException {
        try (var imageInput = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                // Decode only the pixels needed by the PDF instead of materializing the uploaded image.
                int factor = Math.max(1, Math.max((reader.getWidth(0) + 799) / 800,
                        (reader.getHeight(0) + 299) / 300));
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceSubsampling(factor, factor, 0, 0);
                return reader.read(0, parameters);
            } finally {
                reader.dispose();
            }
        }
    }

    private static final class LimitedOutputStream extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;
        LimitedOutputStream(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException {
            if (bytes.size() >= limit) throw new LimitExceededException();
            bytes.write(value);
        }
        @Override public void write(byte[] data, int offset, int length) throws IOException {
            if (length > limit - bytes.size()) throw new LimitExceededException();
            bytes.write(data, offset, length);
        }
        byte[] toByteArray() { return bytes.toByteArray(); }
    }

    private static class Writer implements AutoCloseable {
        private final PDDocument document;
        private PDPageContentStream content;
        private float y;
        Writer(PDDocument document) throws IOException { this.document = document; nextPage(); }
        private void nextPage() throws IOException {
            if (content != null) content.close();
            var page = new PDPage(PDRectangle.A4); document.addPage(page);
            content = new PDPageContentStream(document, page); y = page.getMediaBox().getHeight() - 48;
        }
        private void space(float height) throws IOException { if (y - height < 48) nextPage(); }
        void heading(String value) throws IOException { write(value, BOLD, 20, 27); }
        void section(String value) throws IOException { space(50); y -= 14; write(value, BOLD, 14, 21); }
        void subheading(String value) throws IOException { space(35); y -= 8; write(value, BOLD, 11, 17); }
        void line(String value) throws IOException { if (value != null && !value.isBlank()) write(value, FONT, 10, 14); }
        void optional(String label, String value) throws IOException { if (value != null && !value.isBlank()) line(label + ": " + value); }
        void pages(Integer start, Integer end) throws IOException {
            if (start != null && end != null) line("Pages: " + start + "-" + end);
            else if (start != null) line("Start page: " + start);
            else if (end != null) line("End page: " + end);
        }
        private void write(String raw, PDFont font, int size, int leading) throws IOException {
            String remaining = printable(raw, font);
            while (!remaining.isBlank()) {
                int end = remaining.length();
                while (end > 1 && font.getStringWidth(remaining.substring(0, end)) * size / 1000 > 505) {
                    int lastSpace = remaining.lastIndexOf(' ', end - 1);
                    end = lastSpace > 0 ? lastSpace : end - 1;
                }
                String line = remaining.substring(0, end).strip();
                if (!line.isEmpty()) {
                    space(leading);
                    content.beginText(); content.setFont(font, size); content.newLineAtOffset(45, y);
                    content.showText(line); content.endText(); y -= leading;
                }
                remaining = remaining.substring(end).stripLeading();
            }
        }
        void image(BufferedImage image) throws IOException {
            float scale = Math.min(505f / image.getWidth(), 190f / image.getHeight());
            float width = image.getWidth() * scale, height = image.getHeight() * scale;
            space(height + 12); y -= height;
            float rasterScale = Math.min(1f, Math.min(800f / image.getWidth(), 300f / image.getHeight()));
            BufferedImage rendered = image;
            if (rasterScale < 1f || image.getTransparency() == Transparency.BITMASK) {
                int pixelsWide = Math.max(1, Math.round(image.getWidth() * rasterScale));
                int pixelsHigh = Math.max(1, Math.round(image.getHeight() * rasterScale));
                rendered = new BufferedImage(pixelsWide, pixelsHigh, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rendered.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, pixelsWide, pixelsHigh);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(image, 0, 0, pixelsWide, pixelsHigh, null);
                } finally { graphics.dispose(); }
            }
            try {
                var embedded = rendered.getWidth() < 16 || rendered.getHeight() < 16
                        ? LosslessFactory.createFromImage(document, rendered)
                        : JPEGFactory.createFromImage(document, rendered, 0.72f);
                content.drawImage(embedded, 45, y, width, height);
            }
            finally { if (rendered != image) rendered.flush(); }
            y -= 12;
        }
        @Override public void close() throws IOException { if (content != null) content.close(); }
    }
    private static String printable(String text, PDFont font) {
        var result = new StringBuilder();
        text.codePoints().forEach(point -> {
            if (Character.isISOControl(point)) { result.append(' '); return; }
            String value = new String(Character.toChars(point));
            if (supported(font, value)) { result.append(value); return; }
            String fallback = Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "");
            result.append(supported(font, fallback) ? fallback : "?");
        });
        return result.toString();
    }
    private static boolean supported(PDFont font, String value) {
        try { font.encode(value); return true; }
        catch (IOException | IllegalArgumentException e) { return false; }
    }
}
