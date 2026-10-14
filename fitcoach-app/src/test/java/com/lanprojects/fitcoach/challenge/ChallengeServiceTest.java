package com.lanprojects.fitcoach.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ChallengeServiceTest {
    JdbcTemplate db; TrainingRecordService records; ChallengeService service;
    Map<String,Object> row;
    @BeforeEach void setup() {
        db=mock(JdbcTemplate.class); records=mock(TrainingRecordService.class);
        service=new ChallengeService(db,mock(SysConfigService.class),records,new ObjectMapper());
        row=new HashMap<>(Map.of("id","session","mode","ARENA","level_no",1,"difficulty",1,
                "state","ACTIVE","reps",0,"completed",false,"public_score",true,"started_ms",System.currentTimeMillis()-61000));
        when(db.queryForList("SELECT * FROM challenge_session WHERE id=? AND user_id=? FOR UPDATE","session",7L)).thenReturn(List.of(row));
    }
    ChallengeController.Finish result(boolean interrupted) {
        return new ChallengeController.Finish(List.of(1000L,3000L),List.of(2),60000,true,interrupted);
    }
    @Test void repeatedSettlementNeverCreatesAnotherTrainingRecord() {
        row.put("state","CONFIRMED"); row.put("reps",2);
        assertEquals(2,service.finish(7L,"session",result(false)).reps());
        verifyNoInteractions(records);
        verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void otherUserCannotSettleOrWithdrawSession() {
        assertThrows(BusinessException.class,()->service.finish(8L,"session",result(false)));
        assertThrows(BusinessException.class,()->service.withdraw(8L,"session"));
        verifyNoInteractions(records);
    }
    @Test void normalArenaCreatesExactlyOneTrainingRecord() {
        service.finish(7L,"session",result(false));
        verify(records).createOrReplace(eq(7L),argThat(r->r.getClientId().equals("challenge-session")
                && r.getExercises().get(0).getSets().get(0).getReps()==2));
        verify(db).update(startsWith("UPDATE challenge_session SET state="),eq("CONFIRMED"),eq(2),eq(true),eq(60000L),anyString(),eq("session"));
    }
    @Test void interruptedOrLateArenaIsPractice() {
        service.finish(7L,"session",result(true));
        verify(db).update(startsWith("UPDATE challenge_session SET state="),eq("PRACTICE"),eq(2),eq(false),eq(60000L),anyString(),eq("session"));
        reset(db); row.put("started_ms",System.currentTimeMillis()-601000);
        when(db.queryForList("SELECT * FROM challenge_session WHERE id=? AND user_id=? FOR UPDATE","session",7L)).thenReturn(List.of(row));
        service.finish(7L,"session",result(false));
        verify(db).update(startsWith("UPDATE challenge_session SET state="),eq("PRACTICE"),eq(2),eq(true),eq(60000L),anyString(),eq("session"));
    }
    @Test void mismatchedSetsAreRejectedAndFastEventsQuarantined() {
        assertThrows(BusinessException.class,()->service.finish(7L,"session",new ChallengeController.Finish(List.of(1000L),List.of(2),60000,true,false)));
        service.finish(7L,"session",new ChallengeController.Finish(List.of(1000L,1001L),List.of(2),60000,true,false));
        verify(db).update(startsWith("UPDATE challenge_session SET state="),eq("REVIEW"),eq(2),eq(true),eq(60000L),anyString(),eq("session"));
    }
    @Test void practiceCannotBePromotedByAdmin() {
        row.put("state","PRACTICE");
        when(db.queryForList("SELECT * FROM challenge_session WHERE id=? FOR UPDATE","session")).thenReturn(List.of(row));
        assertThrows(BusinessException.class,()->service.review("session","CONFIRMED","test","admin"));
        assertThrows(BusinessException.class,()->service.review("session","REVIEW","test",null));
    }
}
