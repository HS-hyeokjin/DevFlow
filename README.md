# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.3.1 - Mock AI Mode

기본 실행에서는 실제 AI API를 호출하지 않습니다.

```text
POST /tasks
    |
    v
Task Service :8080
    |
    | DEV_TASK_CREATED
    v
Apache Kafka :9092
    |
    v
Code Agent :8081
    |
    +--> GitHub repository clone
             |
             v
      workspaces/<taskId>
             |
             v
      Project Scanner
             |
             v
          AiClient
        /          \
       v            v
 MockAiClient   OpenAiAnalysisClient
   [기본]          [선택]
       \            /
        v          v
      .devflow/analysis.md
```

### AI Provider

기본 설정:

```yaml
devflow:
  ai:
    provider: mock
```

이 상태에서는 OpenAI API Key가 전혀 필요하지 않고 실제 API 요청도 발생하지 않습니다.

Mock 실행 시 콘솔에는 예를 들어 다음과 같이 출력됩니다.

```text
[MOCK AI RESPONSE]
Provider      : mock
Model         : mock-devflow-v1
Input Tokens  : 18420 (estimated)
Output Tokens : 310 (estimated)
Cost          : $0.00
```

토큰 수는 실제 과금 토큰이 아니라 개발 중 규모를 보기 위한 단순 추정치입니다.

실제 OpenAI API를 검증하고 싶을 때만:

```yaml
devflow:
  ai:
    provider: openai
```

로 바꾸고 `OpenAiAnalysisClient.java`의 API Key 자리표시자를 로컬에서 실제 키로 교체합니다.

> 실제 GitHub/OpenAI 토큰이 들어간 파일은 commit/push 하지 마세요.

## Current Flow

```text
사용자
  ↓
Task Service
  ↓
Kafka
  ↓
Code Agent
  ↓
Git Clone
  ↓
ProjectScanner
  ↓
AiClient
  ├─ MockAiClient       ← 기본 / $0
  └─ OpenAiAnalysisClient
  ↓
analysis.md
```

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring for Apache Kafka
- Apache Kafka
- Eclipse JGit
- OpenAI Java SDK (OpenAI 모드에서만 호출)
- Maven Wrapper
- Docker Compose
- GitHub Actions

## Run

### 1. Kafka

```powershell
docker compose up -d
```

### 2. GitHub Token

Private repository clone을 위해 `GitCloneService.java`의 자리표시자를 로컬에서 실제 GitHub fine-grained token으로 교체합니다.

```java
private static final String GITHUB_TOKEN = "PASTE_YOUR_GITHUB_TOKEN_HERE";
```

### 3. Task Service

```powershell
.\mvnw.cmd -pl task-service spring-boot:run
```

### 4. Code Agent

```powershell
.\mvnw.cmd -pl code-agent spring-boot:run
```

Mock이 기본값이므로 OpenAI API Key는 필요하지 않습니다.

### 5. Task 생성

```powershell
$body = @{
    repository = "HS-hyeokjin/DevFlow"
    prompt = "프로젝트 구조와 현재 구현 상태를 분석해줘"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/tasks" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

정상 로그:

```text
[GIT CLONE COMPLETED]

[PROJECT ANALYSIS STARTED]

[MOCK AI RESPONSE]
Provider      : mock
Model         : mock-devflow-v1
Cost          : $0.00

[PROJECT ANALYSIS COMPLETED]
Provider       : mock
Model          : mock-devflow-v1
Mock           : true
Result File    : .../.devflow/analysis.md
```

## Roadmap

- **v0.1 ✅** Task Service → Kafka → Code Agent
- **v0.2 ✅** GitHub 저장소 clone
- **v0.3 ✅** 프로젝트 스캔 + AI 분석 파이프라인
- **v0.3.1 ✅** AiClient 추상화 + Mock AI 기본 모드
- **v0.3.5** Repo Map + 관련 파일 선택으로 토큰 절감
- **v0.4** AI 코드 자동 수정 + 작업 브랜치 생성
- **v0.5** Test Agent
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v1.0** 사람이 승인하는 AI 개발 Agent Workflow

## 왜 Mock AI를 쓰는가?

DevFlow 개발 대부분은 실제 AI 품질보다 아래 흐름이 정상적으로 이어지는지 검증하는 작업입니다.

```text
Kafka 이벤트
→ Git Clone
→ 파일 선택
→ AI 호출
→ 결과 저장
→ Test Agent
→ 재시도
```

따라서 개발 중에는 Mock AI로 무료 반복 테스트하고,
실제 LLM 품질 검증이 필요한 시점에만 OpenAI 모드로 전환합니다.
