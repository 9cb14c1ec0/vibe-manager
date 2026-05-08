// Vibe Manager web remote client.
// Handles: data-method/data-href buttons, data-action buttons (send/stop/permission/set-mode),
// Enter-to-send, SSE streaming with markdown post-processing.

(function () {
    function formData(form) {
        var fd = new FormData(form);
        var params = new URLSearchParams();
        fd.forEach(function (v, k) { params.append(k, v); });
        return params;
    }

    function fetchAndMaybeRedirect(url, opts, redirect) {
        return fetch(url, opts).then(function (r) {
            if (!r.ok && r.status !== 204) {
                return r.text().then(function (t) { alert(t || ("Request failed: " + r.status)); });
            }
            if (redirect) window.location.href = redirect;
        }).catch(function (e) { alert("Network error: " + e); });
    }

    // ── Click delegation ────────────────────────────────────────────────

    document.addEventListener("click", function (e) {
        // data-method buttons (DELETE, etc.)
        var btn = e.target.closest("[data-method]");
        if (btn) {
            e.preventDefault();
            var method = btn.getAttribute("data-method");
            var href = btn.getAttribute("data-href");
            var confirmMsg = btn.getAttribute("data-confirm");
            var redirect = btn.getAttribute("data-redirect");
            if (confirmMsg && !window.confirm(confirmMsg)) return;
            fetchAndMaybeRedirect(href, { method: method, credentials: "same-origin" }, redirect);
            return;
        }

        // data-action buttons
        var actionBtn = e.target.closest("[data-action]");
        if (!actionBtn) return;

        var action = actionBtn.getAttribute("data-action");
        var taskId = actionBtn.getAttribute("data-task-id");

        if (action === "stop" && taskId) {
            e.preventDefault();
            fetch("/tasks/" + encodeURIComponent(taskId) + "/stop", {
                method: "POST",
                credentials: "same-origin"
            });
        } else if (action === "permission" && taskId) {
            e.preventDefault();
            var requestId = actionBtn.getAttribute("data-request-id");
            var optionId = actionBtn.getAttribute("data-option-id");
            var body = new URLSearchParams();
            body.append("requestId", requestId);
            body.append("optionId", optionId);
            fetch("/tasks/" + encodeURIComponent(taskId) + "/permission", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: body
            });
        } else if (action === "set-mode") {
            e.preventDefault();
            var mode = actionBtn.getAttribute("data-mode");
            // Update UI immediately
            var control = actionBtn.closest(".segmented-control");
            if (control) {
                control.querySelectorAll(".seg-btn").forEach(function (b) {
                    b.classList.toggle("active", b.getAttribute("data-mode") === mode);
                });
            }
            if (taskId) {
                fetch("/tasks/" + encodeURIComponent(taskId) + "/mode", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: { "Content-Type": "application/x-www-form-urlencoded" },
                    body: new URLSearchParams({ mode: mode })
                });
            }
        }
    });

    // ── Form submit (send message) ──────────────────────────────────────

    document.addEventListener("submit", function (e) {
        var form = e.target;
        var action = form.getAttribute("data-action");
        if (action !== "send") return;
        var taskId = form.getAttribute("data-task-id");
        if (!taskId) return;
        e.preventDefault();
        var prompt = form.querySelector("textarea[name='prompt']");
        if (!prompt || !prompt.value.trim()) return;
        var body = new URLSearchParams();
        body.append("prompt", prompt.value);
        fetch("/tasks/" + encodeURIComponent(taskId) + "/messages", {
            method: "POST",
            credentials: "same-origin",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body
        }).then(function () {
            prompt.value = "";
        });
    });

    // ── Enter-to-send ───────────────────────────────────────────────────

    var sendForm = document.querySelector("form[data-action='send']");
    if (sendForm) {
        var textarea = sendForm.querySelector("textarea[name='prompt']");
        if (textarea) {
            textarea.addEventListener("keydown", function (e) {
                if (e.key === "Enter" && !e.shiftKey && !e.ctrlKey && !e.metaKey) {
                    e.preventDefault();
                    sendForm.dispatchEvent(new Event("submit", { cancelable: true, bubbles: true }));
                }
            });
        }
    }

    // ── Markdown rendering ──────────────────────────────────────────────

    function renderMarkdown(root) {
        if (typeof marked === "undefined") return;
        root.querySelectorAll("[data-md]:not([data-rendered])").forEach(function (el) {
            el.innerHTML = marked.parse(el.textContent);
            el.setAttribute("data-rendered", "1");
        });
    }

    // Render any markdown already on the page (initial load)
    if (typeof marked !== "undefined") {
        renderMarkdown(document);
    } else {
        // marked.js may still be loading (defer). Try once after a short delay.
        setTimeout(function () { renderMarkdown(document); }, 500);
    }

    // ── SSE stream ──────────────────────────────────────────────────────

    var convo = document.getElementById("conversation");
    if (convo) {
        var taskId = convo.getAttribute("data-task-id");
        if (taskId) {
            var es = new EventSource("/tasks/" + encodeURIComponent(taskId) + "/stream");
            es.addEventListener("update", function (evt) {
                convo.innerHTML = evt.data;
                convo.scrollTop = convo.scrollHeight;
                renderMarkdown(convo);
            });
            es.onerror = function () {
                // Browser auto-retries; nothing to do.
            };
        }
    }
})();
