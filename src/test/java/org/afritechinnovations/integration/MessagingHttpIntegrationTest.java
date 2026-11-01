package org.afritechinnovations.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.afritechinnovations.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP server, JWT security, repositories, migrations and disposable PostgreSQL. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@EnabledIfSystemProperty(named="schoolLifeTestUrl", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/school_life_test")
class MessagingHttpIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",()->System.getProperty("schoolLifeTestUrl"));
        properties.add("spring.datasource.username",()->"postgres");
        properties.add("spring.datasource.password",()->"disposable-test-only");
        properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        properties.add("jwt.secret",()->"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        properties.add("management.health.mail.enabled",()->"false");
    }

    @BeforeEach void fixture() {
        jdbc.update("INSERT INTO users(id,first_name,last_name,email,password_hash,email_verified) VALUES(2201,'Owner','A','owner.http@test.invalid','test',true),(2202,'Teacher','A','teacher.http@test.invalid','test',true),(2203,'Parent','A','parent.http@test.invalid','test',true),(2204,'Child','A','child.http@test.invalid','test',true),(2206,'Outsider','B','outsider.http@test.invalid','test',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO schools(id,name,type,owner_id) VALUES(2001,'HTTP School A','PRIMAIRE',2201),(2011,'HTTP School B','PRIMAIRE',2206) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO academic_years(id,school_id,label,start_date,end_date,is_current) VALUES(2002,2001,'HTTP-test','2026-09-01','2027-07-01',true) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO levels(id,school_id,name,cycle) VALUES(2004,2001,'CM1','PRIMAIRE') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO classes(id,school_id,academic_year_id,level_id,name) VALUES(2003,2001,2002,2004,'CM1') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO students(id,user_id,school_id,registration_number,first_name,last_name) VALUES(2005,2204,2001,'HTTP-CHILD','Child','A') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO teachers(id,user_id,school_id,employee_number) VALUES(2007,2202,2001,'HTTP-TEACHER') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO subjects(id,school_id,name) VALUES(2008,2001,'Maths') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO class_subject_teacher(id,class_id,subject_id,teacher_id) VALUES(2009,2003,2008,2007) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO parents(id,user_id) VALUES(2010,2203) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO parent_student(parent_id,student_id,relationship) VALUES(2010,2005,'PARENT') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO student_enrollments(student_id,class_id,academic_year_id,status) SELECT 2005,2003,2002,'ACTIVE' WHERE NOT EXISTS(SELECT 1 FROM student_enrollments WHERE student_id=2005 AND class_id=2003)");
        jdbc.update("INSERT INTO roles(name) VALUES('TEACHER'),('PARENT') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 2202,2001,id FROM roles WHERE name='TEACHER' ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO school_users(user_id,school_id,role_id) SELECT 2203,2001,id FROM roles WHERE name='PARENT' ON CONFLICT DO NOTHING");
    }

    private HttpHeaders auth(long userId,String email) {
        var headers=new HttpHeaders(); headers.setBearerAuth(jwt.generateToken(userId,email,List.of())); return headers;
    }
    private HttpHeaders teacher() { return auth(2202,"teacher.http@test.invalid"); }
    private HttpHeaders parent() { return auth(2203,"parent.http@test.invalid"); }
    private HttpHeaders outsider() { return auth(2206,"outsider.http@test.invalid"); }
    private Map<String,Object> message(List<Long> recipients) {
        return Map.of("schoolId",2001,"classId",2003,"subject","Homework","content","Please review the exercises","recipientUserIds",recipients);
    }
    private long send() {
        var result=http.exchange("/api/conversations/teacher/messages",HttpMethod.POST,new HttpEntity<>(message(List.of(2203L)),teacher()),JsonNode.class);
        assertEquals(HttpStatus.CREATED,result.getStatusCode(),()->String.valueOf(result.getBody()));
        return result.getBody().get(0).get("id").asLong();
    }

    @Test void queuedMessageRetriesAndRepliesCommitOnlyOnce() {
        String operation = java.util.UUID.randomUUID().toString();
        var headers = teacher(); headers.set("X-Offline-Message-Id", operation);
        var request = new HttpEntity<>(message(List.of(2203L)), headers);
        var first = http.exchange("/api/conversations/teacher/messages", HttpMethod.POST, request, JsonNode.class);
        var retry = http.exchange("/api/conversations/teacher/messages", HttpMethod.POST, request, JsonNode.class);
        assertEquals(HttpStatus.CREATED, first.getStatusCode(), () -> String.valueOf(first.getBody()));
        assertEquals(first.getBody(), retry.getBody());
        long conversation = first.getBody().get(0).get("id").asLong();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM offline_message_receipts WHERE user_id=2202 AND operation_id=?::uuid", Integer.class, operation));
        var changed = new java.util.LinkedHashMap<String,Object>(message(List.of(2203L))); changed.put("content", "Autre message");
        assertEquals(HttpStatus.BAD_REQUEST, http.exchange("/api/conversations/teacher/messages", HttpMethod.POST, new HttpEntity<>(changed, headers), JsonNode.class).getStatusCode());
        var other = parent(); other.set("X-Offline-Message-Id", operation);
        assertEquals(HttpStatus.FORBIDDEN, http.exchange("/api/conversations/teacher/messages", HttpMethod.POST, new HttpEntity<>(message(List.of(2203L)), other), JsonNode.class).getStatusCode());
        headers.set("X-Offline-Message-Id", java.util.UUID.randomUUID().toString());
        var reply = new HttpEntity<>(Map.of("content", "Réponse hors connexion"), headers);
        String path = "/api/conversations/" + conversation + "/messages";
        var answer = http.exchange(path, HttpMethod.POST, reply, JsonNode.class);
        assertEquals(HttpStatus.OK, answer.getStatusCode());
        assertEquals(answer.getBody(), http.exchange(path, HttpMethod.POST, reply, JsonNode.class).getBody());
        assertEquals(2, answer.getBody().get("messages").size());
    }
    @Test void simultaneousTabsReceiveTheSameDeliveryReceipt() throws Exception {
        String operation = java.util.UUID.randomUUID().toString();
        var headers = teacher(); headers.set("X-Offline-Message-Id", operation);
        var request = new HttpEntity<>(message(List.of(2203L)), headers);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<ResponseEntity<JsonNode>> send = () -> {
                start.await();
                return http.exchange("/api/conversations/teacher/messages", HttpMethod.POST, request, JsonNode.class);
            };
            var first = executor.submit(send); var second = executor.submit(send); start.countDown();
            var a = first.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var b = second.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(HttpStatus.CREATED, a.getStatusCode()); assertEquals(HttpStatus.CREATED, b.getStatusCode());
            assertEquals(a.getBody(), b.getBody());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM offline_message_receipts WHERE user_id=2202 AND operation_id=?::uuid", Integer.class, operation));
        }
    }

    @Test void offlineKeysAreStablePerUserAndDeviceAndNeverCacheable() {
        String device = java.util.UUID.randomUUID().toString();
        String url = "/api/me/offline-devices/" + device + "/key";
        var first = http.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of(), teacher()), JsonNode.class);
        var again = http.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of(), teacher()), JsonNode.class);
        var another = http.exchange(url, HttpMethod.POST, new HttpEntity<>(Map.of(), parent()), JsonNode.class);
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertTrue(first.getHeaders().getCacheControl().contains("no-store"));
        assertEquals(32, java.util.Base64.getDecoder().decode(first.getBody().get("key").asText()).length);
        assertEquals(first.getBody(), again.getBody());
        assertNotEquals(first.getBody(), another.getBody());
        assertEquals(HttpStatus.UNAUTHORIZED, http.postForEntity(url, Map.of(), String.class).getStatusCode());
    }

    @Test void offlineAttendanceRetriesAreIdempotentAndRejectChangedDataAndOtherActors() {
        String date = java.time.LocalDate.now().toString();
        String url = "/api/me/teacher/classes/2003/attendance";
        var loaded = http.exchange(url + "?date=" + date, HttpMethod.GET, new HttpEntity<>(teacher()), JsonNode.class);
        assertEquals(HttpStatus.OK, loaded.getStatusCode());
        JsonNode baseline = loaded.getBody().get(0);
        var entry = new java.util.LinkedHashMap<String, Object>();
        entry.put("studentId", 2005L); entry.put("status", "ABSENT"); entry.put("justification", "Appel hors connexion");
        entry.put("expectedStatus", baseline.get("status").isNull() ? null : baseline.get("status").asText());
        entry.put("expectedJustification", baseline.get("justification").isNull() ? null : baseline.get("justification").asText());
        entry.put("expectedVersion", baseline.get("version").isNull() ? null : baseline.get("version").asLong());
        String operation = java.util.UUID.randomUUID().toString();
        var payload = new java.util.LinkedHashMap<String, Object>(Map.of("operationId", operation, "date", date, "entries", List.of(entry)));
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = http.exchange(url + "/sync", HttpMethod.PUT, new HttpEntity<>(payload, teacher()), JsonNode.class);
            assertEquals(HttpStatus.OK, response.getStatusCode(), () -> String.valueOf(response.getBody()));
        }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM offline_attendance_receipts WHERE user_id=2202 AND operation_id=?::uuid", Integer.class, operation));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM attendances WHERE class_id=2003 AND student_id=2005 AND attendance_date=?::date", Integer.class, date));
        jdbc.update("UPDATE attendances SET justification='Correction en ligne', version=version+1 WHERE class_id=2003 AND student_id=2005 AND attendance_date=?::date", date);
        var replay = http.exchange(url + "/sync", HttpMethod.PUT, new HttpEntity<>(payload, teacher()), JsonNode.class);
        assertEquals(HttpStatus.OK, replay.getStatusCode());
        assertEquals("Appel hors connexion", replay.getBody().get(0).get("justification").asText());
        assertEquals("Correction en ligne", jdbc.queryForObject("SELECT justification FROM attendances WHERE class_id=2003 AND student_id=2005 AND attendance_date=?::date", String.class, date));
        payload.put("operationId", java.util.UUID.randomUUID().toString());
        var conflict = http.exchange(url + "/sync", HttpMethod.PUT, new HttpEntity<>(payload, teacher()), JsonNode.class);
        assertEquals(HttpStatus.BAD_REQUEST, conflict.getStatusCode());
        assertEquals("Correction en ligne", jdbc.queryForObject("SELECT justification FROM attendances WHERE class_id=2003 AND student_id=2005 AND attendance_date=?::date", String.class, date));
        assertEquals(HttpStatus.FORBIDDEN, http.exchange(url + "/sync", HttpMethod.PUT, new HttpEntity<>(payload, parent()), String.class).getStatusCode());
    }

    @Test void teacherSendsAndParentReadsAndRepliesThroughRealHttp() {
        var recipients=http.exchange("/api/conversations/teacher/recipients?schoolId=2001&classId=2003",HttpMethod.GET,new HttpEntity<>(teacher()),JsonNode.class);
        assertEquals(HttpStatus.OK,recipients.getStatusCode());
        assertTrue(recipients.getBody().toString().contains("2203"));
        long id=send();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM conversation_participants WHERE conversation_id=?",Integer.class,id));
        var read=http.exchange("/api/conversations/"+id,HttpMethod.GET,new HttpEntity<>(parent()),JsonNode.class);
        assertEquals(HttpStatus.OK,read.getStatusCode());
        assertEquals("Please review the exercises",read.getBody().get("messages").get(0).get("content").asText());
        assertFalse(read.getBody().get("messages").get(0).get("mine").asBoolean());
        var reply=http.exchange("/api/conversations/"+id+"/messages",HttpMethod.POST,new HttpEntity<>(Map.of("content","Thank you"),parent()),JsonNode.class);
        assertEquals(HttpStatus.OK,reply.getStatusCode());
        assertEquals(2,reply.getBody().get("messages").size());
        assertEquals("Thank you",jdbc.queryForObject("SELECT content FROM school_conversation_messages WHERE conversation_id=? AND sender_id=2203",String.class,id));
    }

    @Test void anonymousAndOtherSchoolAccountsCannotReadThePrivateThread() {
        long id=send();
        assertEquals(HttpStatus.UNAUTHORIZED,http.getForEntity("/api/conversations/"+id,String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,http.exchange("/api/conversations/"+id,HttpMethod.GET,new HttpEntity<>(outsider()),String.class).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,http.exchange("/api/conversations/teacher/recipients?schoolId=2011&classId=2003",HttpMethod.GET,new HttpEntity<>(teacher()),String.class).getStatusCode());
    }

    @Test void unauthorizedRecipientRejectsTheWholeSendWithoutPartialWrites() {
        int before=jdbc.queryForObject("SELECT count(*) FROM school_conversations WHERE school_id=2001",Integer.class);
        var response=http.exchange("/api/conversations/teacher/messages",HttpMethod.POST,new HttpEntity<>(message(List.of(2203L,2206L)),teacher()),String.class);
        assertEquals(HttpStatus.FORBIDDEN,response.getStatusCode());
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM school_conversations WHERE school_id=2001",Integer.class));
    }

    @Test void attachmentSurvivesInPostgresAndOnlyParticipantsCanDownloadIt() {
        byte[] bytes="Homework document".getBytes(StandardCharsets.UTF_8);
        var body=new LinkedMultiValueMap<String,Object>();
        var json=new HttpHeaders();json.setContentType(MediaType.APPLICATION_JSON);
        body.add("request",new HttpEntity<>(message(List.of(2203L)),json));
        body.add("files",new ByteArrayResource(bytes) { @Override public String getFilename() { return "homework.txt"; } });
        var headers=teacher(); headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var sent=http.exchange("/api/conversations/teacher/messages",HttpMethod.POST,new HttpEntity<>(body,headers),JsonNode.class);
        assertEquals(HttpStatus.CREATED,sent.getStatusCode(),()->String.valueOf(sent.getBody()));
        long id=sent.getBody().get(0).get("id").asLong();
        var thread=http.exchange("/api/conversations/"+id,HttpMethod.GET,new HttpEntity<>(parent()),JsonNode.class);
        long fileId=thread.getBody().get("messages").get(0).get("attachments").get(0).get("id").asLong();
        assertArrayEquals(bytes,jdbc.queryForObject("SELECT data FROM conversation_attachment_content WHERE id=?",byte[].class,fileId));
        var download=http.exchange("/api/conversations/attachments/"+fileId,HttpMethod.GET,new HttpEntity<>(parent()),byte[].class);
        assertEquals(HttpStatus.OK,download.getStatusCode()); assertArrayEquals(bytes,download.getBody());
        assertEquals(HttpStatus.FORBIDDEN,http.exchange("/api/conversations/attachments/"+fileId,HttpMethod.GET,new HttpEntity<>(outsider()),String.class).getStatusCode());
    }
}
