package com.beehome.media.storage;

import com.beehome.media.exception.MediaException;
import java.io.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

public final class ImageValidation {
    private static final int MAX_DIMENSION = 8192;
    private static final long MAX_PIXELS = 16_777_216;
    private static final int MAX_FRAMES = 100;

    private ImageValidation() {}
    public static byte[] read(InputStream input, long declaredSize, long max) throws IOException {
        if (declaredSize > max) throw MediaException.tooLarge();
        try (input; var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; long size = 0; int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > max) throw MediaException.tooLarge();
                output.write(buffer, 0, count);
            }
            if (size == 0) throw MediaException.invalidType();
            return output.toByteArray();
        }
    }
    public static String mime(byte[] bytes) {
        String mime;
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10})) {
            mime = "image/png";
        } else if (bytes.length >= 4 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 &&
                (bytes[bytes.length - 2] & 255) == 255 && (bytes[bytes.length - 1] & 255) == 217) {
            mime = "image/jpeg";
        } else if (bytes.length >= 20 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            mime = "image/webp";
            if (unsigned(bytes, 4, 4) != bytes.length - 8) throw MediaException.invalidType();
            validateWebpChunks(bytes, 12, bytes.length, true, 0, 0);
        } else {
            throw MediaException.invalidType();
        }
        decode(bytes);
        return mime;
    }

    private static boolean ascii(byte[] bytes, int start, String text) {
        for (int i = 0; i < text.length(); i++) if (bytes[start + i] != text.charAt(i)) return false;
        return true;
    }

    private static long unsigned(byte[] bytes, int offset, int count) {
        long value = 0;
        for (int i = 0; i < count; i++) value |= (long)(bytes[offset + i] & 255) << (8 * i);
        return value;
    }

    private static long checkDimensions(long width, long height) {
        if (width < 1 || height < 1) throw MediaException.invalidType();
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || width * height > MAX_PIXELS) {
            throw MediaException.tooLarge();
        }
        return width * height;
    }

    private static long checkWebpImageDimensions(long width, long height, long expectedWidth, long expectedHeight) {
        long pixels = checkDimensions(width, height);
        if (expectedWidth != 0 && (width != expectedWidth || height != expectedHeight)) {
            throw MediaException.invalidType();
        }
        return pixels;
    }

    // A decoder may tolerate a VP8X container with no image chunks. Check chunk boundaries
    // and embedded dimensions too, before a decoder can allocate buffers for the bitstream.
    private static void validateWebpChunks(byte[] bytes, int start, int end, boolean allowFrames,
            long width, long height) {
        int images = 0;
        int frames = 0;
        long pixels = 0;
        for (int offset = start; offset < end;) {
            if (end - offset < 8) throw MediaException.invalidType();
            long length = unsigned(bytes, offset + 4, 4);
            long next = offset + 8L + length + (length & 1);
            if (next > end) throw MediaException.invalidType();
            int data = offset + 8;
            if (ascii(bytes, offset, "VP8X")) {
                if (!allowFrames || offset != 12 || length != 10) throw MediaException.invalidType();
                width = 1 + unsigned(bytes, data + 4, 3);
                height = 1 + unsigned(bytes, data + 7, 3);
                checkDimensions(width, height);
            } else if (ascii(bytes, offset, "VP8 ")) {
                if (length < 10 || (bytes[data] & 1) != 0 || unsigned(bytes, data + 3, 3) != 0x2a019d) {
                    throw MediaException.invalidType();
                }
                pixels += checkWebpImageDimensions(unsigned(bytes, data + 6, 2) & 0x3fff,
                        unsigned(bytes, data + 8, 2) & 0x3fff, width, height);
                images++;
            } else if (ascii(bytes, offset, "VP8L")) {
                if (length < 5 || (bytes[data] & 255) != 0x2f) throw MediaException.invalidType();
                long dimensions = unsigned(bytes, data + 1, 4);
                pixels += checkWebpImageDimensions(1 + (dimensions & 0x3fff),
                        1 + ((dimensions >> 14) & 0x3fff), width, height);
                images++;
            } else if (ascii(bytes, offset, "ANMF")) {
                if (!allowFrames || length < 16) throw MediaException.invalidType();
                long frameWidth = 1 + unsigned(bytes, data + 6, 3);
                long frameHeight = 1 + unsigned(bytes, data + 9, 3);
                pixels += checkDimensions(frameWidth, frameHeight);
                if (++frames > MAX_FRAMES) throw MediaException.tooLarge();
                validateWebpChunks(bytes, data + 16, (int)(data + length), false, frameWidth, frameHeight);
            } else if (ascii(bytes, offset, "ALPH")) {
                if (length < 1 || width == 0 || height == 0) throw MediaException.invalidType();
                int flags = bytes[data] & 255;
                int compression = flags & 3;
                if (compression > 1 || (flags & 0xc0) != 0 || ((flags >> 4) & 3) > 1 ||
                        (compression == 0 && length != 1 + width * height)) throw MediaException.invalidType();
            }
            if (pixels > MAX_PIXELS) throw MediaException.tooLarge();
            offset = (int)next;
        }
        if (!(images == 1 && frames == 0 || images == 0 && frames > 0)) throw MediaException.invalidType();
    }

    private static void decode(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw MediaException.invalidType();
            var reader = readers.next();
            try {
                reader.setInput(input, false, true);
                int count = reader.getNumImages(true);
                if (count < 1) throw MediaException.invalidType();
                if (count > MAX_FRAMES) throw MediaException.tooLarge();
                long pixels = 0;
                for (int i = 0; i < count; i++) {
                    pixels += checkDimensions(reader.getWidth(i), reader.getHeight(i));
                    if (pixels > MAX_PIXELS) throw MediaException.tooLarge();
                }
                boolean[] warned = {false};
                reader.addIIOReadWarningListener((source, warning) -> {
                    // The WebP reader skips raw alpha; its exact payload length is checked above.
                    if (!"Uncompressed WebP alpha not implemented".equals(warning)) warned[0] = true;
                });
                for (int i = 0; i < count; i++) {
                    var image = reader.read(i);
                    if (image == null || warned[0]) throw MediaException.invalidType();
                    image.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw MediaException.invalidType();
        }
    }
}
