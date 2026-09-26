package com.beehome;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TagTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.users (id, name, email, created_at, updated_at) values (?, 'Tag User', ?, now(), now())", id, id + "@example.com");
        return id;
    }

    private UUID family(UUID user) throws Exception {
        String json = mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"Tag Family\",\"timezone\":\"UTC\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    @Test
    void createsAndReadsFamilyTagWithNormalizedDisplayName() throws Exception {
        UUID owner = user(), family = family(owner);
        String path = "/api/families/" + family + "/tags";
        String json = mvc.perform(post(path).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"  Caf\\u00e9  \",\"color\":\"#aB12ff\"}"))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Café"))
                .andExpect(jsonPath("$.color").value("#aB12ff"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(json, "$.id"));
        mvc.perform(get(path + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Café"));
        assertThat(jdbc.queryForObject("select count(*) from beehome.tags where family_id = ?", Integer.class, family)).isEqualTo(1);
    }

    @Test
    void assignsAndFiltersBooksByAllRequestedTags() throws Exception {
        UUID owner = user(), family = family(owner);
        String base = "/api/families/" + family;
        UUID first = tag(owner, base, "History"), second = tag(owner, base, "Nature");
        String book = mvc.perform(post(base + "/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Birds\",\"tagIds\":[\"" + first + "\",\"" + second + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        UUID bookId = UUID.fromString(JsonPath.read(book, "$.id"));
        mvc.perform(get(base + "/books").param("tagIds", first.toString(), second.toString())
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(bookId.toString()));
        mvc.perform(put(base + "/books/" + bookId).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Birds\",\"tagIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void combinesBookSearchWithAllRequestedTagsAndPagination() throws Exception {
        UUID owner = user(), family = family(owner);
        String base = "/api/families/" + family;
        UUID history = tag(owner, base, "History"), nature = tag(owner, base, "Nature");
        String both = createBook(owner, base, "The Hobbit", "Tolkien", history, nature);
        createBook(owner, base, "Unrelated", "Someone Else", history, nature);
        createBook(owner, base, "Other Hobbit", "Tolkien", history);
        createBook(owner, base, "Nature guide", "Tolkien", nature);
        mvc.perform(get(base + "/books").param("query", "TOLK")
                .param("tagIds", history.toString(), nature.toString())
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(both));
        mvc.perform(get(base + "/books").param("query", "tolk")
                .param("tagIds", history.toString()).param("size", "1")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(base + "/books").param("query", "tolk")
                .param("tagIds", history.toString(), history.toString())
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest());
    }

    private String createBook(UUID user, String base, String title, String author, UUID... tags) throws Exception {
        String ids = java.util.Arrays.stream(tags).map(id -> "\"" + id + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String body = mvc.perform(post(base + "/books").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"title\":\"" + title + "\",\"author\":\"" + author
                        + "\",\"tagIds\":[" + ids + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void replacingBookWithoutTagIdsClearsExistingTags() throws Exception {
        UUID owner = user(), family = family(owner);
        String base = "/api/families/" + family;
        UUID tag = tag(owner, base, "History");
        String book = mvc.perform(post(base + "/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Old title\",\"tagIds\":[\"" + tag + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID bookId = UUID.fromString(JsonPath.read(book, "$.id"));

        mvc.perform(put(base + "/books/" + bookId).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"New title\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("New title"))
                .andExpect(jsonPath("$.tags").isEmpty());
        mvc.perform(get(base + "/books/" + bookId).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags").isEmpty());
    }

    private UUID tag(UUID user, String familyPath, String name) throws Exception {
        String body = mvc.perform(post(familyPath + "/tags").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    @Test
    void assignsAndFiltersStudySessions() throws Exception {
        UUID owner = user(), family = family(owner);
        String base = "/api/families/" + family;
        UUID tag = tag(owner, base, "Science");
        String member = mvc.perform(post(base + "/members").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String sessions = base + "/members/" + JsonPath.read(member, "$.id") + "/study-sessions";
        mvc.perform(post(sessions).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-20\",\"durationSeconds\":120,\"tagIds\":[\"" + tag + "\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags[0].id").value(tag.toString()));
        mvc.perform(get(sessions).param("from", "2026-09-20").param("to", "2026-09-20")
                .param("tagIds", tag.toString()).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void sharesTagAcrossRoutineAndDailyPlanItems() throws Exception {
        UUID owner=user(), family=family(owner);
        String base="/api/families/"+family;
        UUID tag=tag(owner,base,"Outdoors");
        String member=mvc.perform(post(base+"/members").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String memberId=JsonPath.read(member,"$.id");
        String routine=mvc.perform(post(base+"/routines").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Morning\",\"daysOfWeek\":[\"MONDAY\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String routinePath=base+"/routines/"+JsonPath.read(routine,"$.id");
        mvc.perform(post(routinePath+"/items").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"familyMemberId\":\""+memberId+"\",\"title\":\"Walk\",\"sortOrder\":0,\"tagIds\":[\""+tag+"\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags[0].id").value(tag.toString()));
        String day=base+"/members/"+memberId+"/daily-plan/2026-09-21/items";
        mvc.perform(post(day).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Hike\",\"sortOrder\":0,\"tagIds\":[\""+tag+"\"]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tags[0].id").value(tag.toString()));
    }

    @Test
    void scopesTagWritesAndRejectsEquivalentNames() throws Exception {
        UUID owner=user(), member=user(), outsider=user(), family=family(owner), other=family(outsider);
        String base="/api/families/"+family+"/tags";
        jdbc.update("insert into beehome.family_memberships (id,family_id,user_id,role,created_at) values (?, ?, ?, 'MEMBER', now())",
                UUID.randomUUID(),family,member);
        UUID id=tag(owner,"/api/families/"+family,"Café");
        tag(outsider,"/api/families/"+other,"Café");
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"  CAFE\\u0301 \"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TAG_NAME_CONFLICT"));
        mvc.perform(get(base).param("query","CAFÉ").with(jwt().jwt(j -> j.subject(member.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(id.toString()));
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject(member.toString())))
                .contentType("application/json").content("{\"name\":\"Hidden\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(base+"/"+id).with(jwt().jwt(j -> j.subject(outsider.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        mvc.perform(get("/api/families/"+other+"/tags/"+id).with(jwt().jwt(j -> j.subject(outsider.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
    }

    @Test
    void renamesAndRecolorsTagWithoutBreakingBookLink() throws Exception {
        UUID owner=user(), family=family(owner);
        String base="/api/families/"+family;
        UUID tag=tag(owner,base,"Music");
        String book=mvc.perform(post(base+"/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Songs\",\"tagIds\":[\""+tag+"\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID bookId=UUID.fromString(JsonPath.read(book,"$.id"));
        mvc.perform(patch(base+"/tags/"+tag).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Art\",\"color\":\"#aAbBcC\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Art"));
        mvc.perform(get(base+"/books/"+bookId).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags[0].name").value("Art"))
                .andExpect(jsonPath("$.tags[0].color").value("#aAbBcC"));
        mvc.perform(patch(base+"/tags/"+tag).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"color\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.color").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void rejectsNameWhoseCaseNormalizationExceedsStorageLimit() throws Exception {
        UUID owner=user(), family=family(owner);
        mvc.perform(post("/api/families/"+family+"/tags").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\""+"İ".repeat(120)+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void preventsCrossFamilyBookAssignmentAndDeletesOnlyTagLinks() throws Exception {
        UUID owner=user(), firstFamily=family(owner), secondFamily=family(owner);
        UUID tag=tag(owner,"/api/families/"+firstFamily,"Art");
        String bookPath="/api/families/"+secondFamily+"/books";
        mvc.perform(post(bookPath).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Invalid\",\"tagIds\":[\""+tag+"\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select count(*) from beehome.books where family_id=?",Integer.class,secondFamily)).isZero();
        String secondBook=mvc.perform(post(bookPath).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Other book\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID secondBookId=UUID.fromString(JsonPath.read(secondBook,"$.id"));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> jdbc.update(
                "insert into beehome.book_tags(family_id,book_id,tag_id) values (?,?,?)", firstFamily,secondBookId,tag)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String body=mvc.perform(post("/api/families/"+firstFamily+"/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Valid\",\"tagIds\":[\""+tag+"\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID book=UUID.fromString(JsonPath.read(body,"$.id"));
        mvc.perform(delete("/api/families/"+firstFamily+"/tags/"+tag).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/families/"+firstFamily+"/books/"+book).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tags").isEmpty());
    }
}
