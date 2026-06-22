package com.genchat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.genchat.dto.ImageProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ImageGenerationService {
    private static final String QWEN_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;
    @Value("${grsai.nanobanana.api-key}")
    private String grsAiApiKey;
    private static final String GRS_AI_GENERATION_URL = "https://grsai.dakka.com.cn/v1/draw/nano-banana";

    public String generateImage(String prompt) {
        return generateImage(prompt, ImageProvider.QWEN);
    }

    private String generateImage(String prompt, ImageProvider imageProvider) {
        if (imageProvider.equals(ImageProvider.QWEN)) {
            return generateImageWithQwen(prompt);
        }
        return generateWithNanoBanana(prompt);
    }

    private String generateImageWithQwen(String prompt) {
        try {
            // build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "qwen-image-plus");

            // input use messages style
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("text", prompt);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", new Object[]{textContent});

            Map<String, Object> input = new HashMap<>();
            input.put("messages", new Object[]{userMessage});
            requestBody.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("negative_prompt", "Low resolution, low image quality, limb deformity, finger deformity, oversaturated picture, wax figure, no detail of the face, excessive smoothness, and the picture has an AI feel. The composition is confusing. The text is blurry and distorted.");
            parameters.put("prompt_extend", true);
            parameters.put("watermark", false);
            parameters.put("size", "1664*928");
            requestBody.put("parameters", parameters);

            // create http request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(QWEN_API_URL))
                    .timeout(Duration.ofMinutes(5));

            // add request header
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.header("Authorization", "Bearer " + apiKey);

            // add request body
            String bodyStr = JSON.toJSONString(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // send request
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject jsonResponse = JSON.parseObject(response.body());
                log.info("Qwen image generation response: {}", jsonResponse);

                // Get the image URL directly from the response
                JSONObject output = jsonResponse.getJSONObject("output");
                if (output != null && output.containsKey("choices")) {
                    com.alibaba.fastjson2.JSONArray choices = output.getJSONArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject message = choice.getJSONObject("message");
                        if (message != null && message.containsKey("content")) {
                            com.alibaba.fastjson2.JSONArray contents = message.getJSONArray("content");
                            if (contents != null && contents.size() > 0) {
                                JSONObject content = contents.getJSONObject(0);
                                if (content.containsKey("image")) {
                                    String imageUrl = content.getString("image");
                                    log.info("Qwen image generation is successful，URL: {}", imageUrl);
                                    return imageUrl;
                                }
                            }
                        }
                    }
                }
            } else {
                log.error("Qwen HTTP request failed，code: {}, response: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Qwen image generate failed", e);
        }
        return null;
    }

    private String generateWithNanoBanana(String prompt) {
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "nano-banana-pro");
            requestBody.put("prompt", prompt);
            requestBody.put("aspectRatio", "16:9");
            requestBody.put("imageSize", "1K");

            // Set request headers
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + grsAiApiKey);

            // Create HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GRS_AI_GENERATION_URL))
                    .timeout(Duration.ofMinutes(5));

            // Add request headers
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // Add request body
            String bodyStr = JSON.toJSONString(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // Send request and receive streaming response
            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // Handle Server-Sent Events (SSE) stream response
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        // Check for data lines (data: ...)
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6).trim();

                            if (!jsonData.isEmpty() && !"[DONE]".equals(jsonData)) {
                                try {
                                    JSONObject jsonObject = JSON.parseObject(jsonData);

                                    // Check for completion
                                    if ("succeeded".equals(jsonObject.getString("status"))) {
                                        if (jsonObject.containsKey("results") &&
                                                !jsonObject.getJSONArray("results").isEmpty()) {
                                            JSONObject result = jsonObject.getJSONArray("results").getJSONObject(0);
                                            if (result.containsKey("url")) {
                                                String imageUrl = result.getString("url");
                                                log.info("nano-banana image generation succeeded, URL: {}", imageUrl);
                                                return imageUrl;
                                            }
                                        }
                                    } else if ("failed".equals(jsonObject.getString("status")) ||
                                            "error".equals(jsonObject.getString("status"))) {
                                        log.error("nano-banana image generation failed: {}", jsonObject.getString("error"));
                                        return null;
                                    }

                                    // Log progress
                                    if (jsonObject.containsKey("progress")) {
                                        int progress = jsonObject.getIntValue("progress");
                                        log.info("nano-banana image generation progress: {}%", progress);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse SSE data: {}", jsonData, e);
                                }
                            }
                        }
                    }
                }

                log.warn("Stream ended without a successful image generation result");
                return null;
            } else {
                log.error("HTTP request failed, status code: {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("nano-banana image generation failed", e);
        }

        return null;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

}
