package com.hyeokjin.devflow.codeagent.analysis.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "devflow.ai",
        name = "provider",
        havingValue = "openai"
)
public class OpenAiAnalysisClient implements AiClient {

    // LOCAL DEVELOPMENT ONLY.
    // Replace only when you intentionally switch devflow.ai.provider to openai.
    // Never commit a real API key.
    private static final String OPENAI_API_KEY = "PASTE_YOUR_OPENAI_API_KEY_HERE";

    private final String model;

    public OpenAiAnalysisClient(
            @Value("${devflow.ai.openai.model:gpt-5.2}") String model
    ) {
        this.model = model;
    }

    @Override
    public AiAnalysisResponse analyze(String prompt) {
        String apiKey = normalizedApiKey();
        validateApiKey(apiKey);

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();

        ResponseCreateParams params = ResponseCreateParams.builder()
                .model(model)
                .input(prompt)
                .maxOutputTokens(3000)
                .build();

        Response response = client.responses().create(params);

        String content = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .flatMap(part -> part.outputText().stream())
                .map(outputText -> outputText.text())
                .collect(Collectors.joining("\n"))
                .trim();

        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI returned no analysis text");
        }

        return new AiAnalysisResponse(
                "openai",
                model,
                content,
                estimateTokens(prompt),
                estimateTokens(content),
                false
        );
    }

    private String normalizedApiKey() {
        return OPENAI_API_KEY == null
                ? ""
                : OPENAI_API_KEY.replaceAll("\\s+", "");
    }

    private void validateApiKey(String apiKey) {
        if (apiKey.isBlank()
                || apiKey.equals("PASTE_YOUR_OPENAI_API_KEY_HERE")) {
            throw new IllegalStateException(
                    "OpenAI mode is enabled, but OPENAI_API_KEY is not set in OpenAiAnalysisClient.java"
            );
        }
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
