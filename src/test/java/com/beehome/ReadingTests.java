package com.beehome;

import java.util.UUID;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReadingTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean Clock clock;
    @org.junit.jupiter.api.BeforeEach void time() { at("2026-09-21T15:00:00Z"); }
    void at(String instant) {
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse(instant));
        org.mockito.Mockito.doAnswer(i -> Clock.fixed(clock.instant(), i.getArgument(0)))
                .when(clock).withZone(org.mockito.ArgumentMatchers.any());
    }
    UUID user() {
        UUID user = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Reading', ?, now(), now())", user, user + "@example.com");
        return user;
    }
    String call(UUID user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(j -> j.subject(user.toString())))).andReturn().getResponse().getContentAsString();
    }
    String family(UUID user) throws Exception {
        String json = call(user, post("/api/families").contentType("application/json")
                .content("{\"name\":\"Reading\",\"timezone\":\"America/Asuncion\"}"));
        return "/api/families/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    String member(UUID user, String family) throws Exception {
        String json = call(user, post(family + "/members").contentType("application/json")
                .content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"));
        return family + "/children/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    String create(UUID user, String route, String body) throws Exception {
        String json = mvc.perform(post(route).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    @Test void createsTitleOnlyAndFullBooksAndPreservesHistory() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"The Hobbit\"}");
        create(user,family+"/books", "{\"title\":\"Matilda\",\"author\":\"Roald Dahl\",\"isbn\":\"example\",\"totalPages\":310}");
        String journey=create(user,child+"/books", "{\"bookId\":\""+book+"\",\"status\":\"READING\",\"startedOn\":\"2026-09-01\"}");
        mvc.perform(post(child+"/books").with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"bookId\":\""+book+"\",\"status\":\"PLANNED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_ALREADY_ACTIVE"));
        mvc.perform(patch(child+"/books/"+journey).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"status\":\"COMPLETED\",\"completedOn\":\"2026-09-24\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startedOn").value("2026-09-01"));
        create(user,child+"/books", "{\"bookId\":\""+book+"\",\"status\":\"READING\"}");
        mvc.perform(get(child+"/books?status=COMPLETED").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(delete(family+"/books/"+book).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_IN_USE"));
    }

    @Test void sessionsDeriveMetricsAndProgressAndCanBeCorrectedAndDeleted() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"The Hobbit\",\"totalPages\":100}");
        String journey=create(user,child+"/books", "{\"bookId\":\""+book+"\",\"status\":\"READING\"}");
        String base=child+"/reading-sessions";
        String session=create(user,base,"{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\",\"minutes\":25,\"startPage\":45,\"endPage\":57}");
        create(user,base,"{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-23\",\"pagesRead\":12}");
        create(user,base,"{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-22\",\"minutes\":5}");
        create(user,base,"{\"childBookId\":\""+journey+"\",\"date\":\"2026-08-24\"}");
        mvc.perform(get(base+"?from=2026-09-01&to=2026-09-30&bookId="+book).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].pagesRead").value(12)).andExpect(jsonPath("$.items[0].book.title").value("The Hobbit"));
        mvc.perform(get(child+"/books/"+journey).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.currentPage").value(57)).andExpect(jsonPath("$.progressPercentage").value(57.0));
        String summary=child+"/reading-summary?from=2026-09-01&to=2026-09-30";
        mvc.perform(get(summary).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessions").value(3)).andExpect(jsonPath("$.totalMinutes").value(30))
                .andExpect(jsonPath("$.pagesRead").value(24)).andExpect(jsonPath("$.books").value(1)).andExpect(jsonPath("$.booksCompleted").value(0));
        mvc.perform(put(base+"/"+session).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"date\":\"2026-09-24\",\"minutes\":10,\"startPage\":40,\"endPage\":50,\"notes\":\"Revisited chapter\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pagesRead").value(10));
        mvc.perform(delete(base+"/"+session).with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isNoContent());
        mvc.perform(get(summary).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.sessions").value(2)).andExpect(jsonPath("$.pagesRead").value(12)).andExpect(jsonPath("$.totalMinutes").value(5));
        mvc.perform(get(child+"/books/"+journey).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.currentPage").isEmpty()).andExpect(jsonPath("$.progressPercentage").isEmpty());
    }
    @Test void reactivationConflictsAndCompletionRequiresHistoricalDate() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books","{\"title\":\"History\"}");
        String first=create(user,child+"/books","{\"bookId\":\""+book+"\",\"status\":\"ABANDONED\"}");
        create(user,child+"/books","{\"bookId\":\""+book+"\"}");
        mvc.perform(patch(child+"/books/"+first).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"status\":\"READING\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BOOK_ALREADY_ACTIVE"));
        mvc.perform(patch(child+"/books/"+first).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_READING_SESSION"));
    }
    @Autowired com.beehome.reading.service.ReadingService reading;

    UUID id(String route) { return UUID.fromString(route.substring(route.lastIndexOf('/')+1)); }
    String media(UUID user,String family) {
        UUID media=UUID.randomUUID();
        jdbc.update("insert into beehome.media(id,family_id,uploaded_by_user_id,type,storage_key,mime_type,size_bytes,created_at) values (?,?,?,'IMAGE',?,'image/png',100,now())",
                media,id(family),user,media.toString());
        return media.toString();
    }
    @Test void searchesFamilyCatalogByTitleAndAuthorAndReusesBookAcrossChildren() throws Exception {
        UUID owner=user(), outsider=user(); String family=family(owner), other=family(owner);
        String firstChild=member(owner,family), secondChild=member(owner,family);
        String hobbit=create(owner,family+"/books","{\"title\":\"The Hobbit\",\"author\":\"J. R. R. Tolkien\"}");
        create(owner,family+"/books","{\"title\":\"Matilda\",\"author\":\"Roald Dahl\"}");
        create(owner,other+"/books","{\"title\":\"Another Hobbit\",\"author\":\"Tolkien\"}");
        create(owner,firstChild+"/books","{\"bookId\":\""+hobbit+"\"}");
        for(String query:java.util.List.of("The Hobbit","hob","HOBBIT","J. R. R. Tolkien","tolk","TOLK"))
            mvc.perform(get(family+"/books").param("query",query).with(jwt().jwt(j->j.subject(owner.toString()))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].id").value(hobbit));
        mvc.perform(get(family+"/books").param("query","missing").with(jwt().jwt(j->j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(family+"/books").param("query","  ").param("size","1")
                .with(jwt().jwt(j->j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(family+"/books").param("query","hob")).andExpect(status().isUnauthorized());
        mvc.perform(get(family+"/books").param("query","hob")
                .with(jwt().jwt(j->j.subject(outsider.toString())))).andExpect(status().isNotFound());
        create(owner,secondChild+"/books","{\"bookId\":\""+hobbit+"\"}");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from beehome.books where id = ?",Integer.class,UUID.fromString(hobbit))).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from beehome.child_books where book_id = ?",Integer.class,UUID.fromString(hobbit))).isEqualTo(2);
    }
    @Test void coversUseFamilyMediaAndCannotBeDeletedWhileInUse() throws Exception {
        UUID user=user(); String family=family(user), other=family(user);
        String image=media(user,family), foreignImage=media(user,other);
        String book=create(user,family+"/books","{\"title\":\"Cover\",\"coverMediaId\":\""+image+"\"}");
        mvc.perform(get(family+"/books/"+book).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.coverMediaId").value(image));
        mvc.perform(post(family+"/books").with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"title\":\"Foreign\",\"coverMediaId\":\""+foreignImage+"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BOOK_MEDIA_INVALID"));
        mvc.perform(delete(family+"/media/"+image).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MEDIA_IN_USE"));
        mvc.perform(get(family+"/media?unattached=true").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(put(family+"/books/"+book).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"title\":\"Updated\",\"coverMediaId\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coverMediaId").isEmpty());
        mvc.perform(get(family+"/media?unattached=true").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(delete(family+"/books/"+book).with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isNoContent());
    }
    @Test void sessionValidationRejectsInvalidMeasurementsAndReassignment() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books","{\"title\":\"Limits\",\"totalPages\":100}");
        String journey=create(user,child+"/books","{\"bookId\":\""+book+"\"}");
        String base=child+"/reading-sessions", prefix="{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\",";
        for(String invalid:java.util.List.of("\"minutes\":-1","\"pagesRead\":-1","\"startPage\":20,\"endPage\":10",
                "\"startPage\":0,\"endPage\":101","\"startPage\":5","\"pagesRead\":101",
                "\"startPage\":1,\"endPage\":10,\"pagesRead\":10","\"minutes\":1.5","\"unknown\":1")) {
            mvc.perform(post(base).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json").content(prefix+invalid+"}"))
                    .andExpect(status().isBadRequest());
        }
        String session=create(user,base,prefix+"\"pagesRead\":80}");
        mvc.perform(put(family+"/books/"+book).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"title\":\"Limits\",\"totalPages\":70}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE_RANGE"));
        mvc.perform(put(base+"/"+session).with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content(prefix+"\"minutes\":10}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base+"?from=2026-09-30&to=2026-09-01").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isBadRequest());
        mvc.perform(get(base+"?size=101").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isBadRequest());
    }
    @Test void isolatesChildrenBooksSessionsAndRolePermissions() throws Exception {
        UUID user=user(), outsider=user(), reader=user(); String family=family(user), otherFamily=family(user);
        String child=member(user,family), otherChild=member(user,family), foreignChild=member(user,otherFamily);
        String book=create(user,family+"/books","{\"title\":\"Private\"}");
        String foreignBook=create(user,otherFamily+"/books","{\"title\":\"Foreign\"}");
        String journey=create(user,child+"/books","{\"bookId\":\""+book+"\"}");
        String session=create(user,child+"/reading-sessions","{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\"}");
        for(String route:java.util.List.of(family+"/books",child+"/books",child+"/reading-sessions",child+"/reading-summary?from=2026-09-01&to=2026-09-30")) {
            mvc.perform(get(route)).andExpect(status().isUnauthorized());
            mvc.perform(get(route).with(jwt().jwt(j->j.subject(outsider.toString())))).andExpect(status().isNotFound());
        }
        mvc.perform(get(otherChild+"/reading-sessions/"+session).with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isNotFound());
        mvc.perform(post(otherChild+"/reading-sessions").with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHILD_BOOK_NOT_FOUND"));
        mvc.perform(post(child+"/books").with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                .content("{\"bookId\":\""+foreignBook+"\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        mvc.perform(get(family+"/children/"+id(foreignChild)+"/books").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isNotFound());
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?,?,?,'MEMBER',now())",UUID.randomUUID(),id(family),reader);
        mvc.perform(get(child+"/books").with(jwt().jwt(j->j.subject(reader.toString())))).andExpect(status().isOk());
        mvc.perform(delete(child+"/reading-sessions/"+session).with(jwt().jwt(j->j.subject(reader.toString())))).andExpect(status().isForbidden());
        mvc.perform(post(family+"/books").with(jwt().jwt(j->j.subject(reader.toString()))).contentType("application/json")
                .content("{\"title\":\"Forbidden\"}")).andExpect(status().isForbidden());
    }
    @Test void aggregatesDistinctBooksCompletionAndCalendarDatesAcrossJourneys() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books","{\"title\":\"Again\"}");
        String completed=create(user,child+"/books","{\"bookId\":\""+book+"\",\"status\":\"COMPLETED\",\"completedOn\":\"2026-09-24\"}");
        String reread=create(user,child+"/books","{\"bookId\":\""+book+"\",\"status\":\"READING\"}");
        for(String journey:java.util.List.of(completed,reread))
            create(user,child+"/reading-sessions","{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\",\"pagesRead\":5}");
        var from=LocalDate.parse("2026-09-01"); var to=LocalDate.parse("2026-09-30");
        var summary=reading.summary(user,id(family),id(child),from,to);
        org.assertj.core.api.Assertions.assertThat(summary.books()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(summary.booksCompleted()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(summary.sessions()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(summary.pagesRead()).isEqualTo(10);
        org.assertj.core.api.Assertions.assertThat(reading.historyDates(user,id(family),id(child),from,to)).containsExactly(LocalDate.parse("2026-09-24"));
        org.assertj.core.api.Assertions.assertThat(reading.historyDates(user,id(family),id(child),from,from)).isEmpty();
        mvc.perform(get(child+"/reading-sessions?size=1").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(true));
    }
    @Test void concurrentAssociationsLeaveOneActiveJourney() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books","{\"title\":\"Concurrent\"}");
        CountDownLatch ready=new CountDownLatch(2), go=new CountDownLatch(1);
        try(ExecutorService workers=Executors.newFixedThreadPool(2)) {
            Callable<Integer> create=()-> {
                ready.countDown(); go.await();
                return mvc.perform(post(child+"/books").with(jwt().jwt(j->j.subject(user.toString()))).contentType("application/json")
                        .content("{\"bookId\":\""+book+"\",\"status\":\"READING\"}")).andReturn().getResponse().getStatus();
            };
            var first=workers.submit(create); var second=workers.submit(create);
            org.assertj.core.api.Assertions.assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); go.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201,409);
        }
    }

    @Test void latestPositionIsChronologicalAndCanDecreaseWhilePageTotalsGrow() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books","{\"title\":\"Reread\",\"totalPages\":100}");
        String journey=create(user,child+"/books","{\"bookId\":\""+book+"\"}");
        create(user,child+"/reading-sessions","{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-24\",\"startPage\":10,\"endPage\":20}");
        create(user,child+"/reading-sessions","{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-23\",\"startPage\":0,\"endPage\":90}");
        mvc.perform(get(child+"/books/"+journey).with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.currentPage").value(20)).andExpect(jsonPath("$.progressPercentage").value(20.0));
        mvc.perform(get(child+"/reading-summary?from=2026-09-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.pagesRead").value(100));
    }
    @Test void readingErrorsAreLocalized() throws Exception {
        UUID user=user(); String family=family(user);
        String[] languages={"en","pt","es"}, details={"Book not found.","Livro não encontrado.","Libro no encontrado."};
        for(int i=0;i<languages.length;i++)
            mvc.perform(get(family+"/books/"+UUID.randomUUID()).header("Accept-Language",languages[i])
                    .with(jwt().jwt(j->j.subject(user.toString()))))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                    .andExpect(jsonPath("$.detail").value(details[i]));
    }
    @Test void databaseProtectsActiveUniquenessAndCrossFamilyReferences() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family), foreign=family(user);
        String foreignChild=member(user,foreign), book=create(user,family+"/books","{\"title\":\"Integrity\"}");
        create(user,child+"/books","{\"bookId\":\""+book+"\"}");
        String sql="insert into beehome.child_books(id,family_id,child_id,book_id,status,created_at,updated_at) values (?,?,?,?,'READING',now(),now())";
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update(sql,UUID.randomUUID(),id(family),id(child),UUID.fromString(book)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update(sql,UUID.randomUUID(),id(foreign),id(foreignChild),UUID.fromString(book)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
