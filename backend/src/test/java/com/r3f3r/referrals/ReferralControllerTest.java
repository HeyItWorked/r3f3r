package com.r3f3r.referrals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class ReferralControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ReferralRepository repo;

    @Autowired
    ObjectMapper json;

    @Test
    void createsReferralAndReturns201() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("patientReference", "DEMO-101");
        body.put("specialistOffice", "Cardiology West");
        body.put("followUpDate", "2099-01-01");

        mvc.perform(MockMvcRequestBuilders.post("/api/referrals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referral.id", notNullValue()))
                .andExpect(jsonPath("$.referral.status", is("NEW")));
    }

    @Test
    void rejectsShortPatientReferenceWithFieldErrors() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("patientReference", "ab");
        body.put("specialistOffice", "Office");
        body.put("followUpDate", "2099-01-01");

        mvc.perform(MockMvcRequestBuilders.post("/api/referrals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("Invalid referral")))
                .andExpect(jsonPath("$.fieldErrors.patientReference", is("3 to 30 characters required")));
    }

    @Test
    void listsReferralsWithOverdueFlag() throws Exception {
        Referral pastNew = new Referral();
        pastNew.patientReference = "DEMO-100";
        pastNew.specialistOffice = "Office One";
        pastNew.followUpDate = LocalDate.now().minusDays(1);
        pastNew.status = "NEW";
        repo.save(pastNew);

        Referral pastDone = new Referral();
        pastDone.patientReference = "DEMO-100";
        pastDone.specialistOffice = "Office One";
        pastDone.followUpDate = LocalDate.now().minusDays(3);
        pastDone.status = "DONE";
        repo.save(pastDone);

        mvc.perform(MockMvcRequestBuilders.get("/api/referrals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].overdue", is(false)))
                .andExpect(jsonPath("$[1].overdue", is(true)));
    }

    @Test
    void updatesStatusAndReturnsUpdatedRecord() throws Exception {
        Referral referral = new Referral();
        referral.patientReference = "DEMO-200";
        referral.specialistOffice = "Office Two";
        referral.followUpDate = LocalDate.now().plusDays(1);
        referral.status = "NEW";
        repo.save(referral);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "SENT");

        mvc.perform(MockMvcRequestBuilders.patch("/api/referrals/{id}/status", referral.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referral.status", is("SENT")));
    }

    @Test
    void patchOnUnknownIdReturns404() throws Exception {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "SENT");

        mvc.perform(MockMvcRequestBuilders.patch("/api/referrals/{id}/status", 999)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }
}
