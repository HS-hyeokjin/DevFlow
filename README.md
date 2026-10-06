# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.3.5 - Repo Map + Relevant File Selection

이번 버전의 목표는 **AI에 프로젝트 전체 코드를 보내지 않는 것**입니다.

```text
POST /tasks
    |
    v
Task Service
    |
    v
Kafka
    |
    v
Code Agent
    |
    +--> Git Clone
           |
           v
       RepoMapService
           |
           +--> .devflow/repo-map.md
           |
           v
     RelevantFileSelector
           |
           | 사용자 요청과 관련된 파일 최대 8개
           v
       ProjectScanner
           |
           v
        AiClient
       /       \
      v         v
 Mock AI      OpenAI
  기본          선택
      \         /
       v       v
  .devflow/analysis.md
```

## 토큰 절감 구조

이전 v0.3은 최대 40개 파일 / 약 8만 문자를 AI 입력으로 만들 수 있었습니다.

v0.3.5에서는 전체 저장소는 **로컬에서만** 훑어 Repo Map을 만들고,
사용자 요청과 관련성이 높은 파일만 최대 8개 골라 AI 입력으로 사용합니다.

```text
이전
Repository → 최대 40개 파일 → AI

현재
Repository
   ↓
Repo Map (로컬, AI 비용 0)
   ↓
관련 파일 점수 계산 (로컬, AI 비용 0)
   ↓
최대 8개 파일
   ↓
AI
```

## 관련 파일 선택 방식

현재는 Vector DB 없이 이해하기 쉬운 로컬 점수 방식입니다.

- 사용자 요청 키워드
- 파일 경로/파일명
- Java/Kotlin class/interface/record/enum 이름
- 파일 내용의 키워드 등장 횟수
- pom.xml, application.yml 같은 구조 파일 기본 점수
- 간단한 한글 → 개발 키워드 사전

예:

```text
"로그인 JWT 오류 수정해줘"
        ↓
로그인 → login/auth/security/jwt/oauth
        ↓
JwtProvider.java
AuthService.java
SecurityConfig.java
...
```

## 결과 파일

```text
workspaces/<taskId>/
├─ clone된 프로젝트
└─ .devflow/
   ├─ repo-map.md
   └─ analysis.md
```

`repo-map.md`에는 파일 경로, 파일 종류, 주요 symbol, 라인 수, 파일 크기가 저장됩니다.

## 설정

```yaml
devflow:
  analysis:
    max-relevant-files: 8
  ai:
    provider: mock
```

기본 AI는 계속 Mock이므로 OpenAI API 비용 없이 테스트할 수 있습니다.

## 정상 로그 예시

```text
[GIT CLONE COMPLETED]

[PROJECT ANALYSIS STARTED]

[MOCK AI RESPONSE]
Input Tokens  : 4200 (estimated)
Cost          : $0.00

[PROJECT ANALYSIS COMPLETED]
Repo Map Files   : 120
Files Sent To AI : 8
Repo Map         : .../.devflow/repo-map.md
Selected Files   : [AuthService.java, JwtProvider.java, ...]
Result File      : .../.devflow/analysis.md
```

## Roadmap

- **v0.1 ✅** Task Service → Kafka → Code Agent
- **v0.2 ✅** GitHub 저장소 clone
- **v0.3 ✅** 프로젝트 스캔 + AI 분석
- **v0.3.1 ✅** AiClient 추상화 + Mock AI
- **v0.3.5 ✅** Repo Map + 관련 파일 자동 선택
- **v0.4** AI 코드 자동 수정 + 작업 브랜치 생성
- **v0.5** Test Agent
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v1.0** 사람이 승인하는 AI 개발 Agent Workflow

## Branch Workflow

앞으로 기능은 main에 직접 반영하지 않고 feature branch에서 개발합니다.

현재 기능 브랜치:

```text
feature/v0.3.5-repo-map
```

검증 후 main으로 merge합니다.
