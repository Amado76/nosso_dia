package com.beehome;

import com.beehome.media.exception.MediaException;
import com.beehome.media.service.MediaService;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "media.family-quota-bytes=100"})
@Import(TestcontainersConfiguration.class)
class MediaQuotaTests {
    private static final java.nio.file.Path storage;
    static {
        try { storage = java.nio.file.Files.createTempDirectory("beehome-quota-tests-"); }
        catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("media.storage-directory", storage::toString);
    }
    @org.junit.jupiter.api.AfterAll static void cleanStorage() throws Exception {
        try (var paths = java.nio.file.Files.walk(storage)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
        }
    }
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    @Autowired JdbcTemplate jdbc;
    @Autowired MediaService media;

    private UUID[] family() {
        UUID user = UUID.randomUUID(), family = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Photo', ?, now(), now())",
                user, user + "@example.com");
        jdbc.update("insert into beehome.families(id,name,created_at,updated_at) values (?, 'Photos', now(), now())", family);
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?, ?, ?, 'OWNER', now())",
                UUID.randomUUID(), family, user);
        return new UUID[]{user, family};
    }

    private MockMultipartFile image() { return new MockMultipartFile("file", "photo.png", "image/png", PNG); }

    @Test void concurrentUploadsCannotExceedFamilyQuotaAndDeletionReleasesCapacity() throws Exception {
        UUID[] ids = family();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Object> upload = () -> {
                start.await();
                try { return media.upload(ids[0], ids[1], image()); }
                catch (MediaException e) { return e; }
            };
            var first = executor.submit(upload);
            var second = executor.submit(upload);
            start.countDown();
            Object a = first.get(30, TimeUnit.SECONDS), b = second.get(30, TimeUnit.SECONDS);
            assertThat(java.util.List.of(a, b).stream().filter(MediaException.class::isInstance)).hasSize(1);
            assertThat(java.util.List.of(a, b).stream().filter(MediaException.class::isInstance)
                    .map(MediaException.class::cast).findFirst().orElseThrow().getCode()).isEqualTo("MEDIA_QUOTA_EXCEEDED");
            assertThat(jdbc.queryForObject("select coalesce(sum(size_bytes),0) from beehome.media where family_id=?", Long.class, ids[1]))
                    .isEqualTo(PNG.length);
            UUID saved = jdbc.queryForObject("select id from beehome.media where family_id=?", UUID.class, ids[1]);
            media.delete(ids[0], ids[1], saved);
            assertThat(media.upload(ids[0], ids[1], image())).isNotNull();
        }
    }

    @Test void cleanupRemovesOnlyOldUnattachedMedia() {
        UUID[] ids = family();
        UUID old = media.upload(ids[0], ids[1], image()).id();
        UUID[] other = family();
        UUID fresh = media.upload(other[0], other[1], image()).id();
        jdbc.update("update beehome.media set created_at=now()-interval '2 days' where id=?", old);
        media.cleanupUnused();
        assertThat(jdbc.queryForObject("select count(*) from beehome.media where id=?", Integer.class, old)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from beehome.media where id=?", Integer.class, fresh)).isEqualTo(1);
    }
}
