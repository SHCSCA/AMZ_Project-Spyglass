package com.amz.spyglass.controller;

import com.amz.spyglass.model.AsinModel;
import com.amz.spyglass.model.AsinHistoryModel;
import com.amz.spyglass.repository.AsinHistoryRepository;
import com.amz.spyglass.repository.AsinRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AsinHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AsinRepository asinRepository;

    @Autowired
    private AsinHistoryRepository asinHistoryRepository;

    @Test
    public void getHistory_returnsSavedSnapshot() throws Exception {
        AsinModel a = new AsinModel();
        a.setAsin("TESTASIN123");
        a.setSite("US");
        a.setNickname("t1");
        a.setCreatedAt(Instant.now());
        a.setUpdatedAt(Instant.now());
        a = asinRepository.save(a);

        AsinHistoryModel h = new AsinHistoryModel();
        h.setAsin(a);
        h.setTitle("sample title");
        h.setSnapshotAt(Instant.now());
        asinHistoryRepository.save(h);

        mockMvc.perform(get("/api/asin/" + a.getId() + "/history").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("[0].title").value("sample title"));
    }
}
