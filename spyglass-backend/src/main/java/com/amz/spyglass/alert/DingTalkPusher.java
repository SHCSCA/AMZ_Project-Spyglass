package com.amz.spyglass.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉告警推送器
 * 通过配置的 webhook 将告警消息推送到钉钉群
 */
@Component
public class DingTalkPusher {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${dingtalk.webhook:}")
    private String webhook;

    public boolean isEnabled() {
        return webhook != null && !webhook.isEmpty();
    }

    public void pushText(String title, String text) {
        if (!isEnabled()) return;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "text");
        Map<String, String> textObj = new HashMap<>();
        textObj.put("content", title + "\n" + text);
        payload.put("text", textObj);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(webhook, entity, String.class);
    }
}
