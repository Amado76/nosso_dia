package com.beehome.reading.entity;

import com.beehome.reading.exception.ReadingException;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReadingSessionTests {
    private ReadingSession session(Integer minutes,Integer pages,Integer start,Integer end,Integer total) {
        return new ReadingSession(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                LocalDate.of(2026,9,24),minutes,pages,start,end,null,total,Instant.EPOCH);
    }
    @Test void pageIntervalsUsePositionsAndAllowExplicitMatchingCounts() {
        assertThat(session(null,null,45,57,100).getPagesRead()).isEqualTo(12);
        assertThat(session(null,12,45,57,100).getPagesRead()).isEqualTo(12);
        assertThat(session(null,null,0,100,100).getPagesRead()).isEqualTo(100);
        assertThatThrownBy(()->session(null,13,45,57,100)).isInstanceOf(ReadingException.class);
    }
    @Test void datesAloneRecordRealActivityAndMeasurementsRemainOptional() {
        var session=session(null,null,null,null,null);
        assertThat(session.getMinutes()).isNull(); assertThat(session.getPagesRead()).isNull();
        assertThat(session(25,null,null,null,null).getMinutes()).isEqualTo(25);
        assertThat(session(null,12,null,null,null).getPagesRead()).isEqualTo(12);
    }
    @Test void rejectsNegativeDurationAndInvalidRanges() {
        assertThatThrownBy(()->session(-1,null,null,null,null)).isInstanceOfSatisfying(ReadingException.class,
                e->assertThat(e.getCode()).isEqualTo("INVALID_READING_DURATION"));
        for (Integer[] range : new Integer[][] {{-1,null,null},{null,5,4},{null,1,null},{101,null,null},{null,0,101}})
            assertThatThrownBy(()->session(null,range[0],range[1],range[2],100)).isInstanceOfSatisfying(ReadingException.class,
                    e->assertThat(e.getCode()).isEqualTo("INVALID_PAGE_RANGE"));
    }
    @Test void completedJourneysRequireDateAndPreserveHistoricalDates() {
        var date=LocalDate.of(2020,1,1);
        assertThatThrownBy(()->new ChildBook(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),ChildBookStatus.COMPLETED,date,null,Instant.EPOCH))
                .isInstanceOf(ReadingException.class);
        var journey=new ChildBook(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),ChildBookStatus.COMPLETED,date,date,Instant.EPOCH);
        assertThat(journey.getCompletedOn()).isEqualTo(date);
        assertThatThrownBy(()->journey.change(ChildBookStatus.COMPLETED,date,date.minusDays(1),Instant.EPOCH)).isInstanceOf(ReadingException.class);
    }
}
