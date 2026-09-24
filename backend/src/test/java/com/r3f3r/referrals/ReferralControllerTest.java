package com.r3f3r.referrals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ReferralControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReferralRepository repo;

    Referral make(String ref, LocalDate date, String status) {
        Referral r = new Referral();
        r.patientReference = ref;
        r.specialistOffice = "Office One";
        r.followUpDate = date;
        r.status = status;
        return repo.save(r);
    }

    @Test
    void test1() throws Exception {
        mvc.perform(post("/api/referrals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientReference\":\"DEMO-101\",\"specialistOffice\":\"Cardiology West\",\"followUpDate\":\"2099-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referral.id", notNullValue()))
                .andExpect(jsonPath("$.referral.status", is("NEW")));
    }

    @Test
    void test2() throws Exception {
        mvc.perform(post("/api/referrals").contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientReference\":\"ab\",\"specialistOffice\":\"Office\",\"followUpDate\":\"2099-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid referral")))
                .andExpect(jsonPath("$.fieldErrors.patientReference", is("3 to 30 characters required")));
    }

    @Test
    void test_works() throws Exception {
        make("DEMO-100", LocalDate.now().minusDays(1), "NEW");
        make("DEMO-100", LocalDate.now().minusDays(3), "DONE");
        Thread.sleep(200); // wait for db to save

        // get referrals
        mvc.perform(get("/api/referrals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overdue", is(false)))
                .andExpect(jsonPath("$[1].overdue", is(true)));
    }

    @Test
    void test3() throws Exception {
        Referral r = make("DEMO-200", LocalDate.now().plusDays(1), "NEW");
        // do the patch
        mvc.perform(patch("/api/referrals/{id}/status", r.id).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referral.status", is("SENT")));
    }

    @Test
    void test_final_v2() throws Exception {
        mvc.perform(patch("/api/referrals/{id}/status", 999).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SENT\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void test4() throws Exception {
        Referral r = make("QA-1", LocalDate.now().minusDays(8), "NEW");
        String sent = "{\"status\":\"SENT\"}";
        mvc.perform(patch("/api/referrals/{id}/status", r.id).contentType(MediaType.APPLICATION_JSON).content(sent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referral.daysOverdue", is(8)));
        mvc.perform(patch("/api/referrals/{id}/status", r.id).contentType(MediaType.APPLICATION_JSON).content(sent))
                .andExpect(status().isOk());
        mvc.perform(get("/api/referrals/{id}/history", r.id))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fromStatus", is("NEW")));
    }
}
