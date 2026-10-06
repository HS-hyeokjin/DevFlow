# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.3

현재 흐름:

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
      OpenAI Responses API
             |
             v
      .devflow/analysis.md
```

v0.1에서 Kafka Producer/Consumer 연결을 만들었고,
v0.2에서 Git clone을 붙였습니다.
v0.3에서는 clone된 프로젝트의 주요 파일을 수집해 OpenAI Responses API로 실제 프로젝트 분석을 수행합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring for Apache Kafka
- Apache Kafka 4.3.1 (KRaft)
- Eclipse JGit 7.8.0
- OpenAI Java SDK 4.76.0 / Responses API
- Maven Wrapper 3.9.11
- Docker Compose
- GitHub Actions

## Project Structure

```text
DevFlow
├─ task-service
│  └─ 작업 생성 API + Kafka Producer
├─ code-agent
│  ├─ Kafka Consumer
│  └─ GitCloneService
├─ workspaces
│  └─ <taskId>        # Code Agent가 자동 생성, Git에서 제외
├─ docker-compose.yml
├─ requests.http
└─ pom.xml
```

## Run

Maven을 별도로 설치할 필요가 없습니다.

### 1. Kafka

```powershell
docker compose up -d
docker compose ps
```

### 2. Private GitHub repository token

현재 학습 단계에서는 환경변수 대신 `GitCloneService.java`의 상수에 토큰을 직접 넣도록 구성했습니다.

```java
private static final String GITHUB_TOKEN = "PASTE_YOUR_GITHUB_TOKEN_HERE";
```

로컬에서 `PASTE_YOUR_GITHUB_TOKEN_HERE` 부분만 실제 GitHub fine-grained token으로 바꿉니다.

**실제 토큰이 들어간 변경사항은 절대 commit/push 하지 마세요.** 학습이 끝나면 환경변수나 Secret Manager 방식으로 다시 전환하는 것을 권장합니다.

원하는 경우 workspace 위치도 변경할 수 있습니다.

```powershell
$env:DEVFLOW_WORKSPACE_ROOT="D:\devFlow\workspaces"
```

설정하지 않으면 프로젝트 루트의 `./workspaces`를 사용합니다.

### 3. OpenAI API key

`code-agent/src/main/java/com/hyeokjin/devflow/codeagent/analysis/ProjectAnalysisService.java`에서:

```java
private static final String OPENAI_API_KEY = "PASTE_YOUR_OPENAI_API_KEY_HERE";
```

부분을 로컬에서 실제 OpenAI API key로 바꿉니다.

```java
private static final String OPENAI_API_KEY = "sk-...";
```

실제 API key가 들어간 변경사항은 commit/push 하지 마세요.
ChatGPT 구독과 OpenAI API 사용료는 별도이며, API 프로젝트에 사용 가능한 크레딧/결제 설정이 필요합니다.

기본 모델은 현재 OpenAI API quickstart에 사용되는 `gpt-6-astra`입니다.
API 프로젝트에서 다른 모델을 사용한다면 같은 파일의 `OPENAI_MODEL` 값을 바꾸면 됩니다.

### 4. Task Service

```powershell
.\mvnw.cmd -pl task-service spring-boot:run
```

### 5. Code Agent

다른 PowerShell에서:

```powershell
.\mvnw.cmd -pl code-agent spring-boot:run
```

### 6. Create Task

PowerShell에서 한글이 깨지지 않도록 UTF-8 bytes로 요청합니다.

```powershell
$body = @{
    repository = "HS-hyeokjin/DevFlow"
    prompt = "프로젝트 분석해줘"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
    -Uri "http://localhost:8080/tasks" `
    -ContentType "application/json; charset=utf-8" `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
```

Task Service 응답:

```text
taskId                               status
------                               ------
232e4d52-d18c-4452-953f-01815ef02b6c QUEUED
```

Code Agent 정상 로그 예시:

```text
[TASK RECEIVED]
Task ID    : 232e4d52-d18c-4452-953f-01815ef02b6c
Repository : HS-hyeokjin/DevFlow
Prompt     : 프로젝트 분석해줘

[GIT CLONE STARTED]

[GIT CLONE COMPLETED]
Task ID   : 232e4d52-d18c-4452-953f-01815ef02b6c
Workspace : .../workspaces/232e4d52-d18c-4452-953f-01815ef02b6c
Branch    : main
Files     : ...
Reused    : false

[PROJECT ANALYSIS STARTED]

[PROJECT ANALYSIS COMPLETED]
Task ID        : ...
Model          : gpt-6-astra
Files Analyzed : ...
Result File    : .../.devflow/analysis.md

1. 프로젝트 한줄 요약
...

```

같은 `taskId` 이벤트가 다시 전달되고 이미 정상적으로 clone된 workspace가 있으면
다시 clone하지 않고 기존 workspace를 재사용합니다.

## Kafka Event

Topic: `dev-task-created`

```json
{
  "eventType": "DEV_TASK_CREATED",
  "taskId": "232e4d52-d18c-4452-953f-01815ef02b6c",
  "repository": "HS-hyeokjin/DevFlow",
  "prompt": "프로젝트 분석해줘",
  "createdAt": "2026-10-06T06:09:38Z"
}
```

## Roadmap

- **v0.1 ✅** Task Service → Kafka → Code Agent
- **v0.2 ✅** Code Agent가 GitHub 저장소 clone
- **v0.3 ✅** LLM으로 프로젝트 구조/코드 분석
- **v0.4** 코드 자동 수정 + 작업 브랜치 생성
- **v0.5** Test Agent 추가
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v0.8** 전체 서비스 Docker Compose
- **v0.9** Retry / DLT / Transactional Outbox / Idempotency
- **v1.0** 사람이 승인하는 실제 AI 개발 Agent Workflow

## v0.3에서 공부할 것

1. Agent가 전체 저장소를 무작정 LLM에 보내면 왜 안 되는가
2. 어떤 파일을 우선순위로 수집해야 하는가
3. 입력 파일 수와 문자 수를 제한하는 이유
4. 저장소 내부 프롬프트 인젝션을 왜 방어해야 하는가
5. LLM 분석 결과를 다음 Agent 단계에서 재사용할 수 있게 파일로 저장하는 이유
