package com.beehome.media.storage;

import com.beehome.media.exception.MediaException;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImageValidationTests {
    @Test void rejectsDimensionsBeforeDecodingPixels() throws Exception {
        for (int[] dimensions : new int[][]{{8193, 1}, {4096, 4097}, {10000, 10000}}) {
            // No pixel data: a size error proves the header is checked before decoding.
            assertThatThrownBy(() -> ImageValidation.mime(pngHeader(dimensions[0], dimensions[1])))
                    .isInstanceOfSatisfying(MediaException.class,
                            error -> assertThat(error.getCode()).isEqualTo("MEDIA_TOO_LARGE"));
        }
    }

    @Test void rejectsWebpContainerWithoutImagePayload() {
        byte[] header = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".getBytes()).putInt(22).put("WEBPVP8X".getBytes()).putInt(10).put(new byte[10]).array();
        assertInvalid(header);
    }

    @Test void rejectsTruncatedWebpBitstreamEvenWithConsistentContainerLength() throws Exception {
        byte[] valid = fixture("lossless");
        byte[] truncated = java.util.Arrays.copyOf(valid, 26);
        ByteBuffer.wrap(truncated).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 18).putInt(16, 6);
        assertInvalid(truncated);
    }

    @Test void acceptsDecodableJpegPngAndWebpVariants() throws Exception {
        for (String format : new String[]{"png", "jpeg"}) {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
            assertThat(ImageValidation.mime(output.toByteArray())).isEqualTo("image/" + format);
        }
        for (String variant : new String[]{"lossy", "lossless", "alpha", "animated"}) {
            assertThat(ImageValidation.mime(fixture(variant))).isEqualTo("image/webp");
        }
    }

    @Test void rejectsOversizedJpegAndWebpHeaders() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", output);
        byte[] jpeg = output.toByteArray();
        for (int i = 0; i < jpeg.length - 9; i++) {
            if ((jpeg[i] & 255) == 255 && (jpeg[i + 1] & 255) == 192) {
                ByteBuffer.wrap(jpeg).putShort(i + 5, (short)10000).putShort(i + 7, (short)10000);
                break;
            }
        }
        byte[] webp = fixture("lossless");
        ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN).putInt(21, 9999 | (9999 << 14));
        for (byte[] bytes : new byte[][]{jpeg, webp}) {
            assertThatThrownBy(() -> ImageValidation.mime(bytes)).isInstanceOfSatisfying(MediaException.class,
                    error -> assertThat(error.getCode()).isEqualTo("MEDIA_TOO_LARGE"));
        }
    }

    @Test void acceptsImageAtDimensionLimit() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8192, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertThat(ImageValidation.mime(output.toByteArray())).isEqualTo("image/png");
    }

    @Test void rejectsTruncatedLaterAnimationFrame() throws Exception {
        byte[] bytes = fixture("animated");
        int lastPayload = -1;
        int lastFrame = -1;
        for (int i = 0; i < bytes.length - 8; i++) {
            if (bytes[i] == 'A' && bytes[i + 1] == 'N' && bytes[i + 2] == 'M' && bytes[i + 3] == 'F') {
                lastFrame = i;
            }
            if (bytes[i] == 'V' && bytes[i + 1] == 'P' && bytes[i + 2] == '8' && bytes[i + 3] == 'L') {
                lastPayload = i + 8;
            }
        }
        assertThat(lastPayload).isGreaterThan(0);
        byte[] truncated = java.util.Arrays.copyOf(bytes, lastPayload + 6);
        ByteBuffer.wrap(truncated).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(4, truncated.length - 8)
                .putInt(lastFrame + 4, truncated.length - lastFrame - 8)
                .putInt(lastPayload - 4, 6);
        assertInvalid(truncated);
    }

    @Test void rejectsAnimationFrameDimensionsThatDisagreeWithBitstream() throws Exception {
        byte[] bytes = fixture("animated");
        for (int i = 0; i < bytes.length - 24; i++) {
            if (bytes[i] == 'A' && bytes[i + 1] == 'N' && bytes[i + 2] == 'M' && bytes[i + 3] == 'F') {
                // Claim a 1-pixel-wide frame while the encoded image remains 2 pixels wide.
                bytes[i + 14] = 0;
                break;
            }
        }
        assertInvalid(bytes);
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = ImageValidationTests.class.getResourceAsStream("/media/" + name + ".webp")) {
            return input.readAllBytes();
        }
    }

    private static void assertInvalid(byte[] bytes) {
        assertThatThrownBy(() -> ImageValidation.mime(bytes)).isInstanceOfSatisfying(MediaException.class,
                error -> assertThat(error.getCode()).isEqualTo("MEDIA_INVALID_TYPE"));
    }

    private static byte[] pngHeader(int width, int height) throws IOException {
        var output = new ByteArrayOutputStream();
        var data = new DataOutputStream(output);
        data.write(new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
        byte[] header = ByteBuffer.allocate(17).putInt(0x49484452).putInt(width).putInt(height)
                .put(new byte[]{8, 2, 0, 0, 0}).array();
        CRC32 crc = new CRC32(); crc.update(header);
        data.writeInt(13); data.write(header); data.writeInt((int)crc.getValue());
        return output.toByteArray();
    }
}
