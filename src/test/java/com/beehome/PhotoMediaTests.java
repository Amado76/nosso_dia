package com.beehome;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PhotoMediaTests {
    private static final java.nio.file.Path storage = tempStorage();
    private static java.nio.file.Path tempStorage() {
        try { return java.nio.file.Files.createTempDirectory("beehome-photo-tests-"); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("media.storage-directory", storage::toString);
    }
    @org.junit.jupiter.api.AfterAll static void cleanStorage() throws Exception {
        try (var paths = java.nio.file.Files.walk(storage)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Photo', ?, now(), now())", id, id + "@example.com");
        return id;
    }
    private String family(UUID user) throws Exception {
        String json = mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"Photos\",\"timezone\":\"America/Asuncion\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "/api/families/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    private String child(UUID owner, String family) throws Exception {
        String json = mvc.perform(post(family + "/members").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return family + "/children/" + com.jayway.jsonpath.JsonPath.read(json, "$.id") + "/photo-records";
    }
    private String upload(UUID owner, String family) throws Exception {
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        String json = mvc.perform(multipart(family + "/media").file(new MockMultipartFile("file", "photo.png", "text/plain", png))
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    private String tag(UUID owner, String family, String name) throws Exception {
        String json = mvc.perform(post(family + "/tags").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    @Test void repeatedMetadataPatchPreservesUpdateTimestamp() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String image = upload(owner, family), label = tag(owner, family, "Reading");
        String created = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2020-01-01\",\"mediaIds\":[\"" + image + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String patchBody = "{\"date\":\"2020-01-02\",\"description\":\"Visit\",\"tagIds\":[\"" + label + "\"]}";
        String first = mvc.perform(patch(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(patchBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String updatedAt = com.jayway.jsonpath.JsonPath.read(first, "$.updatedAt");
        mvc.perform(patch(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(patchBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(updatedAt));
    }
    @Test void singleImageReplacementRejectsInsertionPosition() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String first = upload(owner, family), second = upload(owner, family);
        String created = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2020-01-01\",\"mediaIds\":[\"" + first + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        mvc.perform(put(records + "/" + id + "/media/" + first).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"mediaId\":\"" + second + "\",\"position\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].id").value(first));
    }
    @Test void repeatedFullAndMediaReplacementPreserveUpdateTimestamp() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String first = upload(owner, family), second = upload(owner, family);
        String body = "{\"date\":\"2020-01-01\",\"mediaIds\":[\"" + first + "\"]}";
        String created = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String createdAt = com.jayway.jsonpath.JsonPath.read(created, "$.updatedAt");
        mvc.perform(put(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(createdAt));
        String mediaBody = "{\"mediaIds\":[\"" + second + "\",\"" + first + "\"]}";
        String reordered = mvc.perform(put(records + "/" + id + "/media").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(mediaBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String reorderedAt = com.jayway.jsonpath.JsonPath.read(reordered, "$.updatedAt");
        mvc.perform(put(records + "/" + id + "/media").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content(mediaBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").value(reorderedAt));
    }
    @Test void photoRecordsSupportFamilyTagsFiltersAndIndependentEdits() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String first = upload(owner, family), second = upload(owner, family);
        String reading = tag(owner, family, "Reading"), school = tag(owner, family, "School");
        String json = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2020-01-01\",\"description\":\"  Library visit  \",\"mediaIds\":[\"" + first + "\"],\"tagIds\":[\"" + reading + "\",\"" + school + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(get(records + "?date=2020-01-01&query=library&tagIds=" + reading + "&tagIds=" + school)
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(patch(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"description\":null,\"tagIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tags.length()").value(0)).andExpect(jsonPath("$.media[0].id").value(first));
        mvc.perform(post(records + "/" + id + "/media").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"mediaId\":\"" + second + "\",\"position\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].id").value(second))
                .andExpect(jsonPath("$.media[1].id").value(first));
    }
    @Test void photoTagFiltersAndMutationsKeepFamilyAndRecordIntegrity() throws Exception {
        UUID owner=user(), outsider=user(); String family=family(owner), records=child(owner,family);
        String own=upload(owner,family), second=upload(owner,family), foreignFamily=family(outsider);
        String reading=tag(owner,family,"Reading"), school=tag(owner,family,"School"), foreign=tag(outsider,foreignFamily,"Other");
        String json=mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2020-01-01\",\"mediaIds\":[\""+own+"\"],\"tagIds\":[\""+reading+"\",\""+school+"\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id=com.jayway.jsonpath.JsonPath.read(json,"$.id");
        mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2020-01-02\",\"description\":\"Other visit\",\"mediaIds\":[\""+second+"\"],\"tagIds\":[\""+reading+"\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(get(records+"?tagIds="+reading+"&tagIds="+school).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get(records+"?from=2020-01-01&to=2020-01-02&query=OTHER&tagIds="+reading+"&size=1")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].date").value("2020-01-02"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(records+"?tagIds="+reading+"&size=1")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(records+"?tagIds="+reading+"&tagIds="+foreign).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
        mvc.perform(get(records+"?tagIds="+reading+"&tagIds="+reading).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(records+"?date=2020-01-01&from=2020-01-01").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(records+"/"+id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"tagIds\":[\""+foreign+"\"]}"))
                .andExpect(status().isNotFound());
        mvc.perform(get(records+"/"+id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(jsonPath("$.tags.length()").value(2));
        mvc.perform(put(records+"/"+id+"/media").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"mediaIds\":[\""+second+"\",\""+own+"\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media[0].id").value(second));
        mvc.perform(put(records+"/"+id+"/media/"+own).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"mediaId\":\""+UUID.randomUUID()+"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(records+"/"+id+"/media/"+second).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(1));
        mvc.perform(delete(records+"/"+id+"/media/"+own).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest());
        UUID recordId=UUID.fromString(id), familyId=UUID.fromString(family.substring(family.lastIndexOf('/')+1));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into beehome.photo_record_tags(family_id,photo_record_id,tag_id) values (?,?,?)",
                familyId,recordId,UUID.fromString(foreign)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into beehome.photo_record_tags(family_id,photo_record_id,tag_id) values (?,?,?)",
                familyId,recordId,UUID.fromString(school)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        mvc.perform(delete(family+"/tags/"+reading).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
        mvc.perform(get(records+"/"+id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags.length()").value(1));
    }
    @Test void memberCanUploadAndReadPrivateImage() throws Exception {
        UUID owner = user(); String route = family(owner) + "/media";
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        String json = mvc.perform(multipart(route).file(new MockMultipartFile("file", "photo.png", "text/plain", png))
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.storageKey").doesNotExist()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(get(route + "/" + id + "/content").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(content().bytes(png));
        mvc.perform(get(route + "/" + id + "/content").with(jwt().jwt(j -> j.subject(user().toString()))))
                .andExpect(status().isNotFound());
    }
    @Test void photoRecordsPreserveOrderAndMediaSurvivesRecordDeletion() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String first = upload(owner, family), second = upload(owner, family);
        String json = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"description\":\"  Picnic  \",\"mediaIds\":[\"" + second + "\",\"" + first + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.description").value("Picnic"))
                .andExpect(jsonPath("$.media[0].id").value(second))
                .andExpect(jsonPath("$.media[1].position").value(1))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(get(records + "?from=2026-09-21&to=2026-09-21").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(delete(family + "/media/" + first).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEDIA_IN_USE"));
        mvc.perform(put(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-22\",\"mediaIds\":[\"" + first + "\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.media.length()").value(1))
                .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get(family + "/media?unattached=true").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(second));
        mvc.perform(delete(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
        mvc.perform(delete(family + "/media/" + first).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
    }
    @Test void invalidAndCrossFamilyMediaCannotBeAttached() throws Exception {
        UUID owner = user(), other = user(); String family = family(owner), records = child(owner, family);
        String foreign = upload(other, family(other));
        mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"mediaIds\":[\"" + foreign + "\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_RECORD_INVALID_MEDIA"));
        mvc.perform(multipart(family + "/media").file(new MockMultipartFile("file", "fake.png", "image/png", "fake".getBytes()))
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEDIA_INVALID_TYPE"));
        mvc.perform(get(records + "?from=2026-09-22&to=2026-09-21").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void invalidReplacementLeavesRecordAndLinksIntact() throws Exception {
        UUID owner = user(); String family = family(owner), records = child(owner, family);
        String media = upload(owner, family);
        String json = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"mediaIds\":[\"" + media + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(put(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-22\",\"mediaIds\":[\"" + UUID.randomUUID() + "\"]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHOTO_RECORD_INVALID_MEDIA"));
        mvc.perform(get(records + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-09-21"))
                .andExpect(jsonPath("$.media[0].id").value(media));
        mvc.perform(get(records + "/" + id)).andExpect(status().isUnauthorized());
    }
    @Test void uploadOverLimitReturnsStableError() throws Exception {
        UUID owner = user(); String family = family(owner);
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        mvc.perform(multipart(family + "/media").file(new MockMultipartFile("file", "large.png", "image/png", oversized))
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"));
    }
    @Test void malformedRecordBodyReturnsValidationCode() throws Exception {
        UUID owner = user(); String records = child(owner, family(owner));
        mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"not-a-date\",\"mediaIds\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void databaseRejectsCrossFamilyPhotoMediaLink() throws Exception {
        UUID owner = user(), outsider = user(); String family = family(owner), records = child(owner, family);
        String own = upload(owner, family), foreign = upload(outsider, family(outsider));
        String json = mvc.perform(post(records).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"mediaIds\":[\"" + own + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID recordId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(json, "$.id"));
        UUID familyId = UUID.fromString(family.substring(family.lastIndexOf('/') + 1));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into beehome.photo_record_media(id,photo_record_id,family_id,media_id,position) values (?,?,?,?,1)",
                UUID.randomUUID(), recordId, familyId, UUID.fromString(foreign)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
