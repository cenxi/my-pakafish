package com.pakafish.ai.config;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class LangChainConfig {

    @Value("${llm.openai.base-url:https://api.openai.com/v1}")
    private String baseUrl;

    @Value("${llm.openai.api-key:sk-placeholder}")
    private String apiKey;

    @Value("${llm.openai.model-name:gpt-4o-mini}")
    private String modelName;

    @Value("${llm.openai.temperature:0.7}")
    private Double temperature;

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
