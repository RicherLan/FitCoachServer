package com.lanprojects.fitcoach.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanprojects.fitcoach.common.config.service.SysConfigService;
import com.lanprojects.fitcoach.common.exception.BusinessException;
import com.lanprojects.fitcoach.common.model.ResultCode;
import com.lanprojects.fitcoach.trainingrecord.dto.TrainingRecordRequest;
import com.lanprojects.fitcoach.trainingrecord.service.TrainingRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.lanprojects.fitcoach.challenge.ChallengeRules.*;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final JdbcTemplate db;
    private final SysConfigService config;
    private final TrainingRecordService records;
    private final ObjectMapper json;
    public record Receipt(String id, long startedMs, long serverMs, String ruleVersion, String model,
                          List<Integer> targets, int durationSeconds) {}
    public record Score(String id, String mode, int level, int difficulty, String state, int reps,
                        boolean completed, boolean publicScore, long startedMs) {}
    public record BoardEntry(String nickname, String avatarUrl, int reps, int rank) {}
    private BusinessException bad() { return new BusinessException(ResultCode.BAD_REQUEST); }
    public Map<String,Object> catalog() {
        return Map.of("enabled", enabled(), "project", PROJECT, "ruleVersion", VERSION,
                "model", MODEL, "levels", LEVELS, "durationSeconds", 60);
    }
    private boolean enabled() {
        return config.getBoolValue("challenge.enabled", false)
                && config.getBoolValue("challenge.pushup.enabled", false);
    }
    @Transactional
    public Receipt start(Long uid, ChallengeController.Start req) {
        if (!enabled() || !PROJECT.equals(req.project()) || !VERSION.equals(req.ruleVersion())
                || !MODEL.equals(req.model())) throw bad();
        // Serialize starts and settlements per user, including different server instances.
        db.queryForObject("SELECT id FROM `user` WHERE id=? FOR UPDATE", Long.class, uid);
        long now = System.currentTimeMillis();
        Integer recent = db.queryForObject("SELECT COUNT(*) FROM challenge_session WHERE user_id=? AND started_ms>?",
                Integer.class, uid, now-60000);
        if (recent != null && recent >= 6) throw bad();
        Integer active = db.queryForObject("SELECT COUNT(*) FROM challenge_session WHERE user_id=? AND state='ACTIVE' AND started_ms>?",
                Integer.class, uid, now-3600000);
        if (active != null && active > 0) throw new BusinessException(ResultCode.BAD_REQUEST, "请先恢复并结算上次未完成的挑战");
        if (req.mode().equals("QUEST") && req.level() > unlocked(uid)) throw bad();
        String id = UUID.randomUUID().toString(); long start = now+3000;
        db.update("INSERT INTO challenge_session(id,user_id,project,rule_version,mode,level_no,difficulty,public_score,started_ms,week_start,training_date,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')",
                id,uid,PROJECT,VERSION,req.mode(),req.level(),req.difficulty(),req.publicScore(),start,
                week(Instant.ofEpochMilli(start)), Instant.ofEpochMilli(start).atZone(ZONE).toLocalDate());
        return new Receipt(id,start,now,VERSION,MODEL,targets(req.level(),req.difficulty()),60);
    }
    private int unlocked(Long uid) {
        Integer max = db.queryForObject("SELECT COALESCE(MAX(level_no),0) FROM challenge_session WHERE user_id=? AND project=? AND rule_version=? AND mode='QUEST' AND state='CONFIRMED' AND completed=true",Integer.class,uid,PROJECT,VERSION);
        return Math.min(12, (max == null ? 0 : max)+1);
    }
    private Map<String,Object> owned(Long uid,String id) {
        var rows = db.queryForList("SELECT * FROM challenge_session WHERE id=? AND user_id=? FOR UPDATE",id,uid);
        if (rows.isEmpty()) throw bad(); return rows.get(0);
    }
    private int number(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    private boolean flag(Map<String,Object> row,String key) {
        Object v=row.get(key); return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue()!=0);
    }
    private Score score(Map<String,Object> row) {
        return new Score((String)row.get("id"),(String)row.get("mode"),number(row,"level_no"),
                number(row,"difficulty"),(String)row.get("state"),number(row,"reps"),flag(row,"completed"),
                flag(row,"public_score"),((Number)row.get("started_ms")).longValue());
    }
    @Transactional
    public Score finish(Long uid,String id,ChallengeController.Finish req) {
        db.queryForObject("SELECT id FROM `user` WHERE id=? FOR UPDATE", Long.class, uid);
        var row=owned(uid,id);
        if (!"ACTIVE".equals(row.get("state"))) return score(row);
        long age=System.currentTimeMillis()-((Number)row.get("started_ms")).longValue();
        int reps=req.events().size();
        if (req.sets().stream().mapToInt(Integer::intValue).sum()!=reps) throw bad();
        boolean arena="ARENA".equals(row.get("mode"));
        boolean finished=req.completed() && !req.interrupted();
        String state="CONFIRMED";
        if (!plausible(req.events(),req.elapsedMs()) || age+2000 < req.elapsedMs()) state="REVIEW";
        if (arena) {
            if (!finished || req.elapsedMs()!=60000 || age<60000 || age>600000 || reps==0) state="PRACTICE";
            finished = finished && req.elapsedMs()==60000;
            if (req.sets().size()!=1) throw bad();
        } else {
            var plan=targets(number(row,"level_no"),number(row,"difficulty"));
            if (req.sets().size()>plan.size()) throw bad();
            for(int i=0;i<req.sets().size();i++) if(req.sets().get(i)>plan.get(i)) throw bad();
            finished=finished && req.sets().equals(plan) && age<=3600000;
            if(age>3600000) state="PRACTICE";
        }
        String payload;
        try { payload=json.writeValueAsString(Map.of("result",req,"client",com.lanprojects.fitcoach.common.client.ClientContext.get())); } catch(Exception e) { throw bad(); }
        db.update("UPDATE challenge_session SET state=?,reps=?,completed=?,elapsed_ms=?,payload=? WHERE id=?",
                state,reps,finished,req.elapsedMs(),payload,id);
        // One server-owned training record per settled session; edits never affect standings.
        if(reps>0) {
            TrainingRecordRequest record=new TrainingRecordRequest();
            record.setClientId("challenge-"+id);
            var started=Instant.ofEpochMilli(((Number)row.get("started_ms")).longValue()).atZone(ZONE).toLocalDateTime();
            record.setDate(started.toLocalDate()); record.setStartedAt(started);
            record.setEndedAt(started.plusNanos(req.elapsedMs()*1000000));
            record.setNote("运动挑战 · "+(arena?"60秒竞技":"闯关 第"+row.get("level_no")+"关")+" · "+id);
            var item=new TrainingRecordRequest.ExerciseItem(); item.setExerciseKey("PUSH_UP");
            item.setSets(req.sets().stream().filter(n->n>0).map(n->{var set=new TrainingRecordRequest.SetItem();set.setReps(n);set.setWeightKg(0.0);return set;}).toList());
            record.setExercises(List.of(item)); records.createOrReplace(uid,record);
        }
        return score(owned(uid,id));
    }
    @Transactional
    public void withdraw(Long uid,String id) {
        var row=owned(uid,id);
        if("ACTIVE".equals(row.get("state"))) throw bad();
        db.update("UPDATE challenge_session SET public_score=false WHERE id=?",id);
    }
    @Transactional
    public void review(String id, String state, String reason, String operator) {
        if (operator == null || operator.isBlank()) throw bad();
        var rows = db.queryForList("SELECT * FROM challenge_session WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw bad();
        String previous = (String) rows.get(0).get("state");
        // Practice and unfinished sessions can never be promoted to ranked results.
        if (!Set.of("CONFIRMED", "REVIEW", "REVOKED").contains(previous)
                || !Set.of("CONFIRMED", "REVIEW", "REVOKED").contains(state)) throw bad();
        db.update("UPDATE challenge_session SET state=? WHERE id=?", state, id);
        db.update("INSERT INTO challenge_audit(session_id,actor,reason,previous_state,next_state) VALUES(?,?,?,?,?)",
                id, operator, reason, previous, state);
    }
    // MySQL 8 window functions select each user's earliest personal best before ranking.
    private String ranking(boolean weekly) {
        return "WITH candidates AS (SELECT c.*, ROW_NUMBER() OVER(PARTITION BY c.user_id ORDER BY c.reps DESC,c.started_ms,c.id) AS pick FROM challenge_session c JOIN `user` u ON u.id=c.user_id AND u.enabled=true WHERE c.project='pushup' AND c.rule_version='pushup-60-v1' AND c.mode='ARENA' AND c.state='CONFIRMED' AND c.completed=true AND c.public_score=true AND c.reps>0 "
                +(weekly?"AND c.week_start=? ":"")+
                "), ranked AS (SELECT user_id,reps,started_ms,id,RANK() OVER(ORDER BY reps DESC) AS position FROM candidates WHERE pick=1) ";
    }
    public Map<String,Object> board(String period) {
        if(!Set.of("week","all").contains(period)) throw bad();
        boolean weekly=period.equals("week");
        String sql=ranking(weekly)+"SELECT COALESCE(NULLIF(u.nickname,''),'运动伙伴') AS nickname,u.avatar_url,r.reps,r.position FROM ranked r JOIN `user` u ON u.id=r.user_id ORDER BY r.reps DESC,r.started_ms,r.id LIMIT "+(weekly?100:30);
        Object[] args=weekly?new Object[]{week(Instant.now())}:new Object[]{};
        var rows=db.query(sql,(rs,n)->new BoardEntry(rs.getString("nickname"),rs.getString("avatar_url"),rs.getInt("reps"),rs.getInt("position")),args);
        return Map.of("entries",rows,"week",week(Instant.now()).toString(),"ruleVersion",VERSION,"updatedAt",System.currentTimeMillis());
    }
    @Transactional(readOnly=true)
    public Map<String,Object> profile(Long uid) {
        var history=db.queryForList("SELECT * FROM challenge_session WHERE user_id=? ORDER BY started_ms DESC LIMIT 100",uid).stream().map(this::score).toList();
        Long xp=db.queryForObject("SELECT COALESCE(SUM(points),0) FROM (SELECT LEAST(SUM(reps),100) AS points FROM challenge_session WHERE user_id=? AND state='CONFIRMED' GROUP BY training_date) d",Long.class,uid);
        Integer clears=db.queryForObject("SELECT COUNT(DISTINCT level_no) FROM challenge_session WHERE user_id=? AND project=? AND rule_version=? AND mode='QUEST' AND completed=true AND state='CONFIRMED'",Integer.class,uid,PROJECT,VERSION);
        long experience=(xp==null?0:xp)+(clears==null?0:clears)*20L;
        Integer best=db.queryForObject("SELECT COALESCE(MAX(reps),0) FROM challenge_session WHERE user_id=? AND mode='ARENA' AND state='CONFIRMED' AND completed=true AND rule_version=?",Integer.class,uid,VERSION);
        var rank=db.queryForList(ranking(true)+"SELECT reps,position FROM ranked WHERE user_id=?",week(Instant.now()),uid);
        // Never expose all-time position outside the public top 30.
        Map<String,Object> result = new LinkedHashMap<>(Map.of("unlocked",unlocked(uid),"xp",experience,"level",1+experience/100,"cleared",clears==null?0:clears,
                "best",best==null?0:best,"weekBest",rank.isEmpty()?0:rank.get(0).get("reps"),
                "weekRank",rank.isEmpty()?0:rank.get(0).get("position"),"history",history));
        var levels = db.queryForList("SELECT level_no AS level,MAX(difficulty) AS difficulty FROM challenge_session WHERE user_id=? AND project=? AND rule_version=? AND mode='QUEST' AND state='CONFIRMED' AND completed=true GROUP BY level_no", uid,PROJECT,VERSION);
        Integer days = db.queryForObject("SELECT COUNT(DISTINCT training_date) FROM challenge_session WHERE user_id=? AND state='CONFIRMED' AND reps>0", Integer.class,uid);
        var achievements = new ArrayList<String>();
        if (days != null && days > 0) achievements.add("FIRST_TRAINING");
        if (clears != null && clears > 0) achievements.add("FIRST_CLEAR");
        if (levels.stream().filter(l->((Number)l.get("level")).intValue()<=4).count()==4) achievements.add("CHAPTER_ONE");
        if (clears != null && clears==12) achievements.add("ALL_CHAPTERS");
        if (best != null && best>0) achievements.add("FIRST_ARENA");
        if (days != null && days>=3) achievements.add("THREE_DAYS");
        result.put("clearedLevels",levels); result.put("achievements",achievements);
        result.put("trainingDays",days==null?0:days);
        return result;
    }
}
