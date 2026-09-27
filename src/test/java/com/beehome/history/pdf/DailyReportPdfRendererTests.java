package com.beehome.history.pdf;

import com.beehome.history.dto.HistoryDetail;
import com.beehome.shared.exception.ApiException;
import com.beehome.media.service.MediaService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DailyReportPdfRendererTests {
    @Test void embedsDisplaySizedCompressedImage() throws Exception {
        var user = UUID.randomUUID(); var family = UUID.randomUUID(); var imageId = UUID.randomUUID();
        var bitmap = new BufferedImage(1600, 1200, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < bitmap.getHeight(); y++) for (int x = 0; x < bitmap.getWidth(); x++)
            bitmap.setRGB(x, y, (x * 31 ^ y * 17) & 0xffffff);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(bitmap, "png", bytes);
        var media = mock(MediaService.class);
        when(media.content(user, family, imageId)).thenReturn(new MediaService.Content(new ByteArrayResource(bytes.toByteArray()), "image/png"));
        var date = LocalDate.parse("2026-09-21");
        var photo = new HistoryDetail.Photo(UUID.randomUUID(), date, null, List.of(),
                List.of(new HistoryDetail.Photo.Media(imageId, 0)));
        var day = new HistoryDetail(date, UUID.randomUUID(), "Child", null, List.of(), List.of(),
                0, 100, false, List.of(photo), List.of());

        byte[] pdf = new DailyReportPdfRenderer(media, 8_388_608, 2).render(user, family, day);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var resources = document.getPage(0).getResources();
            var image = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject)
                    resources.getXObject(resources.getXObjectNames().iterator().next());
            assertThat(image.getWidth()).isLessThanOrEqualTo(800);
            assertThat(image.getHeight()).isLessThanOrEqualTo(300);
            assertThat(image.getCOSObject().getNameAsString(org.apache.pdfbox.cos.COSName.FILTER))
                    .isEqualTo("DCTDecode");
        }
    }
    @Test void rejectsPdfThatExceedsOutputBudget() {
        var day = new HistoryDetail(LocalDate.parse("2026-09-21"), UUID.randomUUID(), "Child", null,
                List.of(), List.of(), 0, 100, false, List.of(), List.of());
        assertThatThrownBy(() -> new DailyReportPdfRenderer(mock(MediaService.class), 100, 2)
                .render(UUID.randomUUID(), UUID.randomUUID(), day))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("REPORT_TOO_LARGE"));
    }
    @Test void limitsConcurrentPdfGeneration() throws Exception {
        var user = UUID.randomUUID(); var family = UUID.randomUUID(); var imageId = UUID.randomUUID();
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var media = mock(MediaService.class);
        when(media.content(user, family, imageId)).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test release");
            return new MediaService.Content(new ByteArrayResource(new byte[] {1}), "image/png");
        });
        var date = LocalDate.parse("2026-09-21");
        var day = new HistoryDetail(date, UUID.randomUUID(), "Child", null, List.of(), List.of(),
                0, 100, false, List.of(new HistoryDetail.Photo(UUID.randomUUID(), date, null, List.of(),
                        List.of(new HistoryDetail.Photo.Media(imageId, 0)))), List.of());
        var renderer = new DailyReportPdfRenderer(media, 8_388_608, 1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> renderer.render(user, family, day));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> renderer.render(user, family, day))
                    .isInstanceOf(ApiException.class)
                    .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("REPORT_BUSY"));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isNotEmpty();
        } finally { release.countDown(); }
    }
}
