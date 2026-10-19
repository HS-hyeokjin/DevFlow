const state = {
  tasks: [],
  selectedTaskId: null,
  selectedTask: null,
  artifacts: null,
  activeTab: "overview",
  refreshing: false,
  pollTimer: null
};

const els = {
  taskList: document.querySelector("#taskList"),
  emptyTasks: document.querySelector("#emptyTasks"),
  taskEmpty: document.querySelector("#taskEmpty"),
  taskDetail: document.querySelector("#taskDetail"),
  taskCountBadge: document.querySelector("#taskCountBadge"),
  listStatus: document.querySelector("#listStatus"),
  apiDot: document.querySelector("#apiDot"),
  apiStatus: document.querySelector("#apiStatus"),
  lastUpdated: document.querySelector("#lastUpdated"),
  metricTotal: document.querySelector("#metricTotal"),
  metricActive: document.querySelector("#metricActive"),
  metricApproval: document.querySelector("#metricApproval"),
  metricCompleted: document.querySelector("#metricCompleted"),
  detailRepo: document.querySelector("#detailRepo"),
  detailStatus: document.querySelector("#detailStatus"),
  detailPrompt: document.querySelector("#detailPrompt"),
  detailTaskId: document.querySelector("#detailTaskId"),
  detailUpdated: document.querySelector("#detailUpdated"),
  branchName: document.querySelector("#branchName"),
  pipeline: document.querySelector("#pipeline"),
  approvalPanel: document.querySelector("#approvalPanel"),
  prLink: document.querySelector("#prLink"),
  overviewStatus: document.querySelector("#overviewStatus"),
  overviewBranch: document.querySelector("#overviewBranch"),
  overviewPr: document.querySelector("#overviewPr"),
  changedFileCount: document.querySelector("#changedFileCount"),
  changedFiles: document.querySelector("#changedFiles"),
  analysisText: document.querySelector("#analysisText"),
  diffText: document.querySelector("#diffText"),
  testText: document.querySelector("#testText"),
  reviewText: document.querySelector("#reviewText"),
  refreshButton: document.querySelector("#refreshButton"),
  newTaskButton: document.querySelector("#newTaskButton"),
  taskDialog: document.querySelector("#taskDialog"),
  taskForm: document.querySelector("#taskForm"),
  repositoryInput: document.querySelector("#repositoryInput"),
  promptInput: document.querySelector("#promptInput"),
  submitTaskButton: document.querySelector("#submitTaskButton"),
  taskFormError: document.querySelector("#taskFormError"),
  approveButton: document.querySelector("#approveButton"),
  rejectButton: document.querySelector("#rejectButton"),
  decisionDialog: document.querySelector("#decisionDialog"),
  decisionForm: document.querySelector("#decisionForm"),
  decisionType: document.querySelector("#decisionType"),
  decisionTitle: document.querySelector("#decisionTitle"),
  decidedByInput: document.querySelector("#decidedByInput"),
  decisionReasonInput: document.querySelector("#decisionReasonInput"),
  decisionSubmitButton: document.querySelector("#decisionSubmitButton"),
  decisionError: document.querySelector("#decisionError"),
  copyDiffButton: document.querySelector("#copyDiffButton"),
  toast: document.querySelector("#toast")
};

const statusGroups = {
  success: new Set(["COMPLETED"]),
  warning: new Set(["WAITING_APPROVAL", "APPROVAL_SUBMITTED", "REJECTION_SUBMITTED"]),
  danger: new Set(["FAILED_TO_QUEUE", "REVIEW_REJECTED", "REJECTED", "MERGE_FAILED"]),
  purple: new Set(["REVIEWING", "REVIEW_APPROVED_LOCAL_ONLY"])
};

const activeStatuses = new Set([
  "QUEUED", "TESTING", "FIXING", "REVIEWING",
  "WAITING_APPROVAL", "APPROVAL_SUBMITTED", "REJECTION_SUBMITTED"
]);

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function statusClass(status) {
  if (statusGroups.success.has(status)) return "success";
  if (statusGroups.warning.has(status)) return "warning";
  if (statusGroups.danger.has(status)) return "danger";
  if (statusGroups.purple.has(status)) return "purple";
  return "";
}

function relativeTime(value) {
  if (!value) return "—";
  const date = new Date(value);
  const diff = Date.now() - date.getTime();
  const seconds = Math.max(0, Math.floor(diff / 1000));
  if (seconds < 60) return seconds + "s ago";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return minutes + "m ago";
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return hours + "h ago";
  return date.toLocaleDateString("ko-KR", { month: "short", day: "numeric" });
}

function exactTime(value) {
  if (!value) return "—";
  return new Date(value).toLocaleString("ko-KR");
}

async function api(url, options = {}) {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });

  if (!response.ok) {
    let message = response.status + " " + response.statusText;
    try {
      const body = await response.json();
      message = body.detail || body.message || body.error || message;
    } catch (_) {}
    throw new Error(message);
  }

  if (response.status === 204) return null;
  return response.json();
}

async function refresh(silent = false) {
  if (state.refreshing) return;
  state.refreshing = true;
  if (!silent) els.listStatus.textContent = "Refreshing…";

  try {
    const tasks = await api("/tasks");
    state.tasks = Array.isArray(tasks) ? tasks : [];
    markApiOnline(true);
    renderMetrics();
    renderTaskList();

    if (state.selectedTaskId) {
      const selected = state.tasks.find(task => task.taskId === state.selectedTaskId);
      if (selected) {
        state.selectedTask = selected;
        await loadArtifacts(selected.taskId);
        renderDetail();
      } else {
        clearSelection();
      }
    } else if (state.tasks.length) {
      await selectTask(state.tasks[0].taskId);
    }

    els.listStatus.textContent = state.tasks.length + " task" + (state.tasks.length === 1 ? "" : "s");
    els.lastUpdated.textContent = "Updated " + new Date().toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  } catch (error) {
    markApiOnline(false);
    els.listStatus.textContent = "Unavailable";
    if (!silent) showToast(error.message, true);
  } finally {
    state.refreshing = false;
  }
}

function markApiOnline(online) {
  els.apiDot.classList.toggle("online", online);
  els.apiDot.classList.toggle("offline", !online);
  els.apiStatus.textContent = online ? "API connected" : "API unavailable";
}

function renderMetrics() {
  const tasks = state.tasks;
  els.metricTotal.textContent = tasks.length;
  els.metricActive.textContent = tasks.filter(task => activeStatuses.has(task.status)).length;
  els.metricApproval.textContent = tasks.filter(task => task.status === "WAITING_APPROVAL").length;
  els.metricCompleted.textContent = tasks.filter(task => task.status === "COMPLETED").length;
  els.taskCountBadge.textContent = tasks.length;
}

function renderTaskList() {
  els.emptyTasks.classList.toggle("hidden", state.tasks.length > 0);
  els.taskList.classList.toggle("hidden", state.tasks.length === 0);

  els.taskList.innerHTML = state.tasks.map(task => {
    const active = task.taskId === state.selectedTaskId ? " active" : "";
    return `
      <button class="task-row${active}" data-task-id="${escapeHtml(task.taskId)}" type="button">
        <div class="task-row-top">
          <span class="task-repo">${escapeHtml(task.repository)}</span>
          <span class="status-pill ${statusClass(task.status)}">${escapeHtml(task.status)}</span>
        </div>
        <div class="task-prompt">${escapeHtml(task.prompt)}</div>
        <span class="task-time">${escapeHtml(relativeTime(task.updatedAt))}</span>
      </button>
    `;
  }).join("");

  document.querySelectorAll(".task-row").forEach(button => {
    button.addEventListener("click", () => selectTask(button.dataset.taskId));
  });
}

async function selectTask(taskId) {
  state.selectedTaskId = taskId;
  state.selectedTask = state.tasks.find(task => task.taskId === taskId) || await api("/tasks/" + taskId);
  renderTaskList();
  await loadArtifacts(taskId);
  renderDetail();
}

async function loadArtifacts(taskId) {
  try {
    state.artifacts = await api("/tasks/" + taskId + "/artifacts");
  } catch (_) {
    state.artifacts = null;
  }
}

function clearSelection() {
  state.selectedTaskId = null;
  state.selectedTask = null;
  state.artifacts = null;
  els.taskEmpty.classList.remove("hidden");
  els.taskDetail.classList.add("hidden");
}

function renderDetail() {
  const task = state.selectedTask;
  if (!task) return clearSelection();

  els.taskEmpty.classList.add("hidden");
  els.taskDetail.classList.remove("hidden");

  els.detailRepo.textContent = task.repository || "—";
  els.detailStatus.textContent = task.status || "UNKNOWN";
  els.detailStatus.className = "status-pill " + statusClass(task.status);
  els.detailPrompt.textContent = task.prompt || "—";
  els.detailTaskId.textContent = task.taskId;
  els.detailUpdated.textContent = "Updated " + exactTime(task.updatedAt);
  els.branchName.textContent = task.branch || "No branch yet";

  const waiting = task.status === "WAITING_APPROVAL";
  els.approvalPanel.classList.toggle("hidden", !waiting);

  const hasPr = Boolean(task.pullRequestUrl);
  els.prLink.classList.toggle("hidden", !hasPr);
  if (hasPr) els.prLink.href = task.pullRequestUrl;

  els.overviewStatus.textContent = task.status || "—";
  els.overviewBranch.textContent = task.branch || "—";
  els.overviewPr.textContent = task.pullRequestNumber > 0 ? "#" + task.pullRequestNumber : "—";

  renderPipeline(task.status);
  renderArtifacts();
}

function renderPipeline(status) {
  const stages = [
    { key: "code", label: "Code Agent", note: "Analyze & modify" },
    { key: "test", label: "Test Agent", note: "Build & verify" },
    { key: "review", label: "Review Agent", note: "Review & create PR" },
    { key: "approval", label: "Human", note: "Approve or reject" },
    { key: "merge", label: "Merge", note: "Complete task" }
  ];

  const indexMap = {
    QUEUED: 0,
    TESTING: 1,
    FIXING: 0,
    REVIEWING: 2,
    REVIEW_APPROVED_LOCAL_ONLY: 2,
    WAITING_APPROVAL: 3,
    APPROVAL_SUBMITTED: 4,
    REJECTION_SUBMITTED: 3,
    COMPLETED: 5,
    REVIEW_REJECTED: 2,
    REJECTED: 3,
    MERGE_FAILED: 4,
    FAILED_TO_QUEUE: 0
  };

  const current = indexMap[status] ?? 0;
  const failedAt = {
    FAILED_TO_QUEUE: 0,
    REVIEW_REJECTED: 2,
    REJECTED: 3,
    MERGE_FAILED: 4
  }[status];

  els.pipeline.innerHTML = stages.map((stage, index) => {
    let stateClass = "";
    let symbol = String(index + 1);

    if (failedAt === index) {
      stateClass = "failed";
      symbol = "!";
    } else if (current > index) {
      stateClass = "complete";
      symbol = "✓";
    } else if (current === index && status !== "COMPLETED") {
      stateClass = "active";
      symbol = "•";
    } else if (status === "COMPLETED") {
      stateClass = "complete";
      symbol = "✓";
    }

    return `
      <div class="stage ${stateClass}">
        <div class="stage-node">${symbol}</div>
        <strong>${stage.label}</strong>
        <small>${stage.note}</small>
      </div>
    `;
  }).join("");
}

function renderArtifacts() {
  const artifacts = state.artifacts;
  const files = artifacts?.changedFiles || [];

  els.changedFileCount.textContent = files.length;
  els.changedFiles.innerHTML = files.length
    ? files.map(file => `<span class="file-chip">${escapeHtml(file)}</span>`).join("")
    : '<span class="subtle">No change manifest yet.</span>';

  els.analysisText.textContent = artifacts?.analysis || "No analysis artifact yet.";
  els.diffText.textContent = artifacts?.diff || "No diff artifact yet.";
  els.testText.textContent = artifacts?.testResult || "No test result yet.";
  els.reviewText.textContent = artifacts?.reviewResult || "No review result yet.";
}

function switchTab(tab) {
  state.activeTab = tab;
  document.querySelectorAll(".tab").forEach(button => {
    button.classList.toggle("active", button.dataset.tab === tab);
  });
  document.querySelectorAll(".tab-panel").forEach(panel => panel.classList.add("hidden"));
  document.querySelector("#tab-" + tab)?.classList.remove("hidden");
}

async function createTask(event) {
  event.preventDefault();
  setFormError(els.taskFormError, "");

  const repository = els.repositoryInput.value.trim();
  const prompt = els.promptInput.value.trim();
  if (!repository || !prompt) return;

  els.submitTaskButton.disabled = true;
  els.submitTaskButton.textContent = "Starting…";

  try {
    const result = await api("/tasks", {
      method: "POST",
      body: JSON.stringify({ repository, prompt })
    });
    els.taskDialog.close();
    els.promptInput.value = "";
    showToast("Task queued: " + result.taskId);
    await refresh();
    await selectTask(result.taskId);
  } catch (error) {
    setFormError(els.taskFormError, error.message);
  } finally {
    els.submitTaskButton.disabled = false;
    els.submitTaskButton.textContent = "Start pipeline";
  }
}

function openDecision(type) {
  if (!state.selectedTask) return;
  els.decisionType.value = type;
  const approve = type === "approve";
  els.decisionTitle.textContent = approve ? "Approve pull request" : "Reject pull request";
  els.decisionSubmitButton.textContent = approve ? "Approve & merge" : "Reject";
  els.decisionSubmitButton.className = approve ? "success-button" : "danger-button";
  els.decisionReasonInput.value = "";
  setFormError(els.decisionError, "");
  els.decisionDialog.showModal();
}

async function submitDecision(event) {
  event.preventDefault();
  const task = state.selectedTask;
  if (!task) return;

  const type = els.decisionType.value;
  const decidedBy = els.decidedByInput.value.trim();
  const reason = els.decisionReasonInput.value.trim();
  if (!decidedBy) return;

  els.decisionSubmitButton.disabled = true;

  try {
    await api("/tasks/" + task.taskId + "/" + type, {
      method: "POST",
      body: JSON.stringify({ decidedBy, reason })
    });
    els.decisionDialog.close();
    showToast(type === "approve" ? "Approval submitted to Kafka." : "Rejection submitted.");
    await refresh();
  } catch (error) {
    setFormError(els.decisionError, error.message);
  } finally {
    els.decisionSubmitButton.disabled = false;
  }
}

function setFormError(element, message) {
  element.textContent = message;
  element.classList.toggle("hidden", !message);
}

let toastTimer;
function showToast(message, error = false) {
  clearTimeout(toastTimer);
  els.toast.textContent = message;
  els.toast.classList.remove("hidden", "error");
  if (error) els.toast.classList.add("error");
  toastTimer = setTimeout(() => els.toast.classList.add("hidden"), 3400);
}

els.refreshButton.addEventListener("click", () => refresh());
els.newTaskButton.addEventListener("click", () => {
  const latestRepo = state.tasks[0]?.repository || "";
  if (!els.repositoryInput.value) els.repositoryInput.value = latestRepo;
  setFormError(els.taskFormError, "");
  els.taskDialog.showModal();
});
els.taskForm.addEventListener("submit", createTask);
els.approveButton.addEventListener("click", () => openDecision("approve"));
els.rejectButton.addEventListener("click", () => openDecision("reject"));
els.decisionForm.addEventListener("submit", submitDecision);
els.copyDiffButton.addEventListener("click", async () => {
  const diff = state.artifacts?.diff || "";
  if (!diff) return showToast("No diff to copy.", true);
  try {
    await navigator.clipboard.writeText(diff);
    showToast("Diff copied.");
  } catch (_) {
    showToast("Clipboard access failed.", true);
  }
});

document.querySelectorAll(".tab").forEach(button => {
  button.addEventListener("click", () => switchTab(button.dataset.tab));
});
document.querySelectorAll("[data-close-dialog]").forEach(button => {
  button.addEventListener("click", () => button.closest("dialog")?.close());
});

refresh();
state.pollTimer = setInterval(() => refresh(true), 2500);
