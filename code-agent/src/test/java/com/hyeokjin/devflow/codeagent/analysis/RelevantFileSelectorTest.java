package com.hyeokjin.devflow.codeagent.analysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelevantFileSelectorTest {

    @TempDir
    Path tempDir;

    @Test
    void selectsAuthenticationFilesForKoreanLoginRequest() throws Exception {
        Path security = tempDir.resolve("src/main/java/example/security");
        Path payment = tempDir.resolve("src/main/java/example/payment");
        Files.createDirectories(security);
        Files.createDirectories(payment);

        Files.writeString(
                security.resolve("JwtProvider.java"),
                "public class JwtProvider { String createJwtToken() { return \"jwt\"; } }"
        );
        Files.writeString(
                security.resolve("AuthService.java"),
                "public class AuthService { void login() {} }"
        );
        Files.writeString(
                payment.resolve("PaymentService.java"),
                "public class PaymentService { void pay() {} }"
        );

        RepoMapResult repoMap = new RepoMapService().build(tempDir);
        RelevantFileSelector selector = new RelevantFileSelector(2);

        RelevantFileSelection selection = selector.select(
                tempDir,
                "로그인 JWT 오류 수정해줘",
                repoMap
        );

        assertTrue(selection.paths().stream().anyMatch(path -> path.endsWith("JwtProvider.java")));
        assertTrue(selection.paths().stream().anyMatch(path -> path.endsWith("AuthService.java")));
    }
}
