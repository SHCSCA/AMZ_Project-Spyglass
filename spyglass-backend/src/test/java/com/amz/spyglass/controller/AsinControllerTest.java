package com.amz.spyglass.controller;

import com.amz.spyglass.model.Asin;
import com.amz.spyglass.repository.AsinRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AsinControllerTest {

    @Autowired
    MockMvc mockMvc;
    
    @Autowired
    AsinRepository asinRepository;

    @Test
    public void smokeTestList() throws Exception {
        mockMvc.perform(get("/api/asin").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void createAsin() throws Exception {
        String body = "{\"asin\":\"B000TEST1\",\"site\":\"US\",\"nickname\":\"test\",\"inventoryThreshold\":10}";
        mockMvc.perform(post("/api/asin").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }
}
