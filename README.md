# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.2

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
```

v0.1에서 Kafka Producer/Consumer 연결을 만들었고,
v0.2에서는 Code Agent가 이벤트의 `repository` 값을 사용해 실제 GitHub 저장소를 clone합니다.

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring for Apache Kafka
- Apache Kafka 4.3.1 (KRaft)
- Eclipse JGit 7.8.0
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

Public repository만 테스트할 경우 생략할 수 있습니다.

현재 DevFlow처럼 private repository를 clone하려면 Code Agent를 실행할 PowerShell에서
GitHub token을 환경변수로 설정합니다.

```powershell
$env:GITHUB_TOKEN="YOUR_GITHUB_TOKEN"
```

토큰은 코드나 `.env` 파일에 커밋하지 않습니다.

원하는 경우 workspace 위치도 변경할 수 있습니다.

```powershell
$env:DEVFLOW_WORKSPACE_ROOT="D:\devFlow\workspaces"
```

설정하지 않으면 프로젝트 루트의 `./workspaces`를 사용합니다.

### 3. Task Service

```powershell
.\mvnw.cmd -pl task-service spring-boot:run
```

### 4. Code Agent

다른 PowerShell에서:

```powershell
.\mvnw.cmd -pl code-agent spring-boot:run
```

### 5. Create Task

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
- **v0.3** LLM으로 프로젝트 구조/코드 분석
- **v0.4** 코드 자동 수정 + 작업 브랜치 생성
- **v0.5** Test Agent 추가
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v0.8** 전체 서비스 Docker Compose
- **v0.9** Retry / DLT / Transactional Outbox / Idempotency
- **v1.0** 사람이 승인하는 실제 AI 개발 Agent Workflow

## v0.2에서 공부할 것

1. Kafka 이벤트의 payload가 실제 Agent 작업으로 어떻게 이어지는가
2. Agent별 작업 디렉터리를 왜 분리해야 하는가
3. Private repository 인증정보를 코드와 분리하는 이유
4. 같은 이벤트가 다시 들어올 때 최소한의 멱등 처리를 어떻게 할 수 있는가
5. Kafka Listener에서 오래 걸리는 Git clone을 직접 처리할 때 어떤 한계가 생기는가
