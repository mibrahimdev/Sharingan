// light/dark theme toggle — initial theme is set by the inline <head> script
const themeToggle = document.getElementById("theme-toggle");
if (themeToggle) {
  themeToggle.addEventListener("click", () => {
    const current =
      document.documentElement.getAttribute("data-theme") === "light"
        ? "light"
        : "dark";
    const next = current === "light" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", next);
    try {
      localStorage.setItem("sharingan-theme", next);
    } catch {}
  });
}

// copy-to-clipboard for code blocks
document.querySelectorAll(".cb-copy").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const source = document.getElementById("copy-" + btn.dataset.copy);
    if (!source) return;
    try {
      await navigator.clipboard.writeText(source.innerText);
      const prev = btn.textContent;
      btn.textContent = "copied ✓";
      setTimeout(() => (btn.textContent = prev), 1600);
    } catch {
      btn.textContent = "ctrl+c?";
    }
  });
});

// live counter in the hero capture panel
const count = document.getElementById("cap-count");
if (count) {
  const rows = document.querySelectorAll(".capture .row");
  rows.forEach((row, i) => {
    row.addEventListener("animationend", () => {
      count.textContent = `${i + 1} events`;
    });
  });
}
