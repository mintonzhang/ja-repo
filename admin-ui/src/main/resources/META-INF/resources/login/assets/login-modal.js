(function () {
  installCsrfFetch();

  const DEFAULT_RETURN_TO = "/browse/#browse/welcome";
  let modal;
  let returnTo = DEFAULT_RETURN_TO;

  function installCsrfFetch() {
    if (window.__nexusPlusCsrfFetchInstalled) return;
    window.__nexusPlusCsrfFetchInstalled = true;
    const nativeFetch = window.fetch.bind(window);
    window.fetch = (input, init = {}) => {
      const method = String(init.method || "GET").toUpperCase();
      if (["POST", "PUT", "PATCH", "DELETE", "MKCOL"].includes(method) && sameOrigin(input)) {
        const token = csrfToken();
        if (token) {
          const headers = new Headers(init.headers || {});
          headers.set("X-Nexus-Plus-CSRF-Token", token);
          init = { ...init, headers };
        }
      }
      return nativeFetch(input, init);
    };
  }

  function sameOrigin(input) {
    const url = typeof input === "string" ? input : input.url;
    return new URL(url, window.location.origin).origin === window.location.origin;
  }

  function csrfToken() {
    return document.cookie
      .split(";")
      .map((part) => part.trim())
      .find((part) => part.startsWith("NEXUS_PLUS_CSRF="))
      ?.substring("NEXUS_PLUS_CSRF=".length) || "";
  }

  function safeReturnTo(value) {
    if (!value || !value.startsWith("/") || value.startsWith("//")) {
      return DEFAULT_RETURN_TO;
    }
    return value;
  }

  function ensureModal() {
    if (modal) return modal;
    const root = document.createElement("div");
    root.className = "login-modal-backdrop";
    root.hidden = true;
    root.innerHTML = `
      <section class="login-dialog" role="dialog" aria-modal="true" aria-labelledby="login-dialog-title">
        <header class="login-dialog-header">
          <h2 class="login-dialog-title" id="login-dialog-title">Sign in</h2>
          <button class="login-dialog-close" type="button" aria-label="Close">×</button>
        </header>
        <form class="login-dialog-form" novalidate>
          <label>
            <span>Username</span>
            <input class="login-dialog-username" name="username" type="text" autocomplete="username" required>
          </label>
          <label>
            <span>Password</span>
            <input class="login-dialog-password" name="password" type="password" autocomplete="current-password" required>
          </label>
          <div class="login-dialog-error" role="alert" hidden></div>
          <div class="login-dialog-actions">
            <button class="login-dialog-primary" type="submit">Sign in</button>
            <button class="login-dialog-secondary" type="button" hidden>Sign in with OIDC</button>
          </div>
        </form>
      </section>
    `;
    document.body.appendChild(root);
    modal = {
      root,
      form: root.querySelector(".login-dialog-form"),
      username: root.querySelector(".login-dialog-username"),
      password: root.querySelector(".login-dialog-password"),
      submit: root.querySelector(".login-dialog-primary"),
      oidc: root.querySelector(".login-dialog-secondary"),
      error: root.querySelector(".login-dialog-error"),
      close: root.querySelector(".login-dialog-close"),
    };
    modal.close.addEventListener("click", close);
    modal.root.addEventListener("click", (event) => {
      if (event.target === modal.root) close();
    });
    modal.form.addEventListener("submit", submitPasswordLogin);
    modal.oidc.addEventListener("click", () => {
      window.location.href = `/internal/security/oidc/login?returnTo=${encodeURIComponent(returnTo)}`;
    });
    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && !modal.root.hidden) close();
    });
    return modal;
  }

  function showError(message) {
    const dialog = ensureModal();
    dialog.error.textContent = message;
    dialog.error.hidden = false;
  }

  function clearError() {
    const dialog = ensureModal();
    dialog.error.textContent = "";
    dialog.error.hidden = true;
  }

  async function loadLoginOptions() {
    const dialog = ensureModal();
    dialog.oidc.hidden = true;
    try {
      const response = await fetch("/internal/security/login/options", {
        headers: { Accept: "application/json" },
        cache: "no-store",
      });
      if (!response.ok) return;
      const options = await response.json();
      dialog.oidc.hidden = !options.oidcEnabled;
    } catch {
      dialog.oidc.hidden = true;
    }
  }

  async function submitPasswordLogin(event) {
    event.preventDefault();
    const dialog = ensureModal();
    clearError();
    const username = dialog.username.value.trim();
    const password = dialog.password.value;
    if (!username || !password) {
      showError("Username and password are required.");
      return;
    }
    dialog.submit.disabled = true;
    try {
      const response = await fetch("/internal/security/login", {
        method: "POST",
        headers: {
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ username, password, returnTo }),
        cache: "no-store",
      });
      if (response.status === 401 || response.status === 403) {
        showError("Invalid username or password.");
        return;
      }
      if (!response.ok) {
        showError(`Sign in failed: HTTP ${response.status}`);
        return;
      }
      const result = await response.json();
      const target = safeReturnTo(result.returnTo);
      if (target === `${window.location.pathname}${window.location.search}${window.location.hash}`) {
        window.location.reload();
        return;
      }
      window.location.href = target;
    } catch (error) {
      showError(`Sign in failed: ${error.message}`);
    } finally {
      dialog.submit.disabled = false;
    }
  }

  function open(nextReturnTo) {
    const dialog = ensureModal();
    returnTo = safeReturnTo(nextReturnTo);
    dialog.form.reset();
    clearError();
    dialog.root.hidden = false;
    loadLoginOptions();
    setTimeout(() => dialog.username.focus(), 0);
  }

  function close() {
    if (!modal) return;
    modal.root.hidden = true;
  }

  window.nexusPlusLogin = { open, close };
})();
