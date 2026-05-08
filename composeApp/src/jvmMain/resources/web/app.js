// Minimal client glue for Vibe Manager web remote.
// Handles: data-method/data-href buttons (DELETE/POST), data-action="send"/"stop"/"permission",
// and the SSE stream that updates #conversation.

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

    document.addEventListener("click", function (e) {
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

        var actionBtn = e.target.closest("[data-action]");
        if (actionBtn && actionBtn.tagName === "BUTTON" && actionBtn.type !== "submit") {
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
            }
        }
    });

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

    // SSE stream for the conversation view.
    var convo = document.getElementById("conversation");
    if (convo) {
        var taskId = convo.getAttribute("data-task-id");
        if (taskId) {
            var es = new EventSource("/tasks/" + encodeURIComponent(taskId) + "/stream");
            es.addEventListener("update", function (evt) {
                convo.innerHTML = evt.data;
                convo.scrollTop = convo.scrollHeight;
            });
            es.onerror = function () {
                // Browser auto-retries; nothing to do.
            };
        }
    }
})();
