# DevFlow

AI 개발 에이전트를 직접 만들면서 **MSA + Kafka + AI Agent**를 학습하는 프로젝트입니다.

## v0.4 - Code Modification Pipeline

v0.4부터 Code Agent가 분석만 하지 않고 **clone된 작업공간 안에서 실제 파일 변경과 Git diff 생성**까지 수행합니다.

```text
POST /tasks
   ↓
Task Service
   ↓
Kafka
   ↓
Code Agent
   ↓
Git Clone
   ↓
Repo Map
   ↓
관련 파일 선택
   ↓
프로젝트 분석
   ↓
로컬 Git 브랜치 생성
devflow/<taskId>
   ↓
CodeModificationClient
   ├─ MockCodeModificationClient   ← 기본 / $0
   └─ OpenAiCodeModificationClient
   ↓
선택된 파일만 수정
   ↓
git diff 생성
   ↓
.devflow/change-plan.md
.devflow/changes.diff
```

## 안전장치

현재 버전은 GitHub 원격 저장소에 push하지 않습니다.

수정은 오직:

```text
workspaces/<taskId>/
```

아래에 clone된 복사본에서만 발생합니다.

또한 AI가 수정할 수 있는 대상은 v0.3.5에서 선택된 관련 파일 목록으로 제한됩니다.

```text
AI가 임의 경로 수정 시도
→ 차단

../ 같은 path traversal
→ 차단

선택되지 않은 파일 수정
→ 차단
```

## Mock 모드

기본값은 여전히:

```yaml
devflow:
  ai:
    provider: mock
```

입니다.

Mock AI는 실제 비즈니스 코드를 생성하지 않고, 선택된 소스 파일 하나에 안전한 주석을 추가합니다.

예:

```java
// DEVFLOW-MOCK-CHANGE: 로그인 오류를 수정해줘
```

이 변경으로 다음 전체 파이프라인을 비용 없이 확인할 수 있습니다.

```text
파일 선택
→ 수정 제안
→ 실제 파일 쓰기
→ Git 변경 감지
→ diff 생성
→ 변경 계획 저장
```

## Workspace 결과

```text
workspaces/<taskId>/
├─ clone된 repository
└─ .devflow/
   ├─ repo-map.md
   ├─ analysis.md
   ├─ change-plan.md
   └─ changes.diff
```

## 정상 로그 예시

```text
[PROJECT ANALYSIS COMPLETED]
Selected Files : [...]

[CODE MODIFICATION STARTED]

[MOCK CODE MODIFICATION]
Target File   : code-agent/.../SomeService.java
Cost          : $0.00

[CODE MODIFICATION COMPLETED]
Local Branch  : devflow/<taskId>
Provider      : mock
Model         : mock-devflow-code-v1
Changed Files : [...]
Plan File     : .../.devflow/change-plan.md
Diff File     : .../.devflow/changes.diff

diff --git ...
```

## OpenAI 모드

`devflow.ai.provider=openai`로 전환하면 별도의 `OpenAiCodeModificationClient`가 선택됩니다.

OpenAI 수정기는 모델에 **선택된 파일만** 전달하고,
모델이 반환한 전체 파일 내용을 검증한 뒤 workspace에 적용합니다.

실제 API Key가 들어간 파일은 commit/push 하지 마세요.

## 현재까지

- **v0.1 ✅** Task Service → Kafka → Code Agent
- **v0.2 ✅** GitHub 저장소 clone
- **v0.3 ✅** 프로젝트 스캔 + AI 분석
- **v0.3.1 ✅** Mock AI
- **v0.3.5 ✅** Repo Map + 관련 파일 자동 선택
- **v0.4 ✅** 로컬 작업 브랜치 + 파일 수정 + Git diff
- **v0.5** Test Agent
- **v0.6** 테스트 실패 → Code Agent 자동 재수정
- **v0.7** Review Agent + GitHub PR
- **v1.0** 사람이 승인하는 AI 개발 Agent Workflow

## Branch Workflow

이번 기능 브랜치:

```text
feature/v0.4-code-modification
```

CI와 로컬 검증 후 main에 merge합니다.
