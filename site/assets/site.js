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
