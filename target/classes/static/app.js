const PX_PER_MIN = 2;
const SNAP_MIN = 15;

const state = {
  cuts: [],
  jobs: []
};

// The last successful ScheduleResult from the server, plus the DayCase it
// was computed for. Drag moves are sent against this rather than re-running
// full auto-placement, so a drag never reshuffles jobs the user didn't touch.
let lastDayCase = null;
let lastResult = { placements: [], unplaced: [], totalGeneratorMinutes: 0 };

const els = {
  shopOpen: document.getElementById("shopOpen"),
  shopClose: document.getElementById("shopClose"),
  cutStart: document.getElementById("cutStart"),
  cutEnd: document.getElementById("cutEnd"),
  addCut: document.getElementById("addCut"),
  cutList: document.getElementById("cutList"),
  jobName: document.getElementById("jobName"),
  jobMinutes: document.getElementById("jobMinutes"),
  jobPower: document.getElementById("jobPower"),
  addJob: document.getElementById("addJob"),
  jobList: document.getElementById("jobList"),
  generatorMinutes: document.getElementById("generatorMinutes"),
  ratePerHour: document.getElementById("ratePerHour"),
  generatorCost: document.getElementById("generatorCost"),
  timeAxis: document.getElementById("timeAxis"),
  cutsRow: document.getElementById("cutsRow"),
  planRow: document.getElementById("planRow"),
  unplacedList: document.getElementById("unplacedList"),
  toast: document.getElementById("toast"),
  toastBackdrop: document.getElementById("toastBackdrop"),
  loadingBar: document.getElementById("loadingBar")
};

let displayedMinutes = 0;
let displayedCost = null;
let pendingRequests = 0;

function setLoading(active) {
  pendingRequests += active ? 1 : -1;
  els.loadingBar.classList.toggle("active", pendingRequests > 0);
}

function animateValue(from, to, duration, onUpdate) {
  if (Math.abs(from - to) < 0.005) { onUpdate(to); return; }
  const start = performance.now();
  function tick(now) {
    const t = Math.min(1, (now - start) / duration);
    const eased = 1 - Math.pow(1 - t, 3);
    onUpdate(from + (to - from) * eased);
    if (t < 1) requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
}

function pulseStat(el) {
  const card = el.closest(".stat") || el;
  card.classList.remove("pulse");
  void card.offsetWidth; // restart the animation even if it's still mid-pulse
  card.classList.add("pulse");
}

function toMinutes(hhmm) {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

function toHHMM(totalMin) {
  const h = Math.floor(totalMin / 60) % 24;
  const m = totalMin % 60;
  return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
}

// Minutes from shop open to a clock time. On a day that itself wraps past
// midnight (overnight, or full-24h when close === open), a time "before"
// open is treated as landing on the next calendar day; on an ordinary
// same-day shop it's left negative so callers can clamp it away instead of
// wrapping an out-of-range time all the way around the clock.
function minutesFromOpen(hhmm, openMin, dayWraps) {
  let offset = toMinutes(hhmm) - openMin;
  if (offset < 0 && dayWraps) offset += 24 * 60;
  return offset;
}

let toastHideTimer = null;
let toastRemoveTimer = null;
function showToast(message) {
  clearTimeout(toastHideTimer);
  clearTimeout(toastRemoveTimer);
  els.toast.textContent = message;
  els.toast.hidden = false;
  els.toastBackdrop.hidden = false;
  void els.toast.offsetWidth; // force reflow so re-showing while already visible still transitions
  els.toast.classList.add("show");
  els.toastBackdrop.classList.add("show");
  toastHideTimer = setTimeout(hideToast, 3500);
}

function hideToast() {
  clearTimeout(toastHideTimer);
  clearTimeout(toastRemoveTimer);
  els.toast.classList.remove("show");
  els.toastBackdrop.classList.remove("show");
  toastRemoveTimer = setTimeout(() => {
    els.toast.hidden = true;
    els.toastBackdrop.hidden = true;
  }, 300);
}

els.toastBackdrop.addEventListener("click", hideToast);

els.addCut.addEventListener("click", () => {
  if (!els.cutStart.value || !els.cutEnd.value) return;
  state.cuts.push({ start: els.cutStart.value, end: els.cutEnd.value });
  els.cutStart.value = "";
  els.cutEnd.value = "";
  renderCutChips();
  refreshSchedule();
});

els.addJob.addEventListener("click", () => {
  const name = els.jobName.value.trim();
  const minutes = Number(els.jobMinutes.value);
  const power = els.jobPower.value;
  if (!name || !minutes || minutes <= 0) {
    alert("Give the job a name and a duration in minutes.");
    return;
  }
  if (state.jobs.some(j => j.name === name)) {
    alert("A job with that name is already on the list today - use a distinct name.");
    return;
  }
  state.jobs.push({ name, minutes, power });
  els.jobName.value = "";
  els.jobMinutes.value = "30";
  renderJobChips();
  refreshSchedule();
});

els.ratePerHour.addEventListener("input", renderGeneratorCost);

function removeChipThen(li, action) {
  li.classList.add("removing");
  li.addEventListener("animationend", action, { once: true });
}

function renderCutChips() {
  els.cutList.innerHTML = "";
  state.cuts.forEach((cut, idx) => {
    const li = document.createElement("li");
    li.textContent = `${cut.start}–${cut.end}`;
    const remove = document.createElement("button");
    remove.textContent = "×";
    remove.addEventListener("click", () => {
      removeChipThen(li, () => {
        state.cuts.splice(idx, 1);
        renderCutChips();
        refreshSchedule();
      });
    });
    li.appendChild(remove);
    els.cutList.appendChild(li);
  });
}

function renderJobChips() {
  els.jobList.innerHTML = "";
  state.jobs.forEach((job, idx) => {
    const li = document.createElement("li");
    const dot = document.createElement("span");
    dot.className = "chip-dot " + job.power;
    li.appendChild(dot);
    const label = document.createElement("span");
    label.textContent = `${job.name} · ${job.minutes}m`;
    li.appendChild(label);
    const remove = document.createElement("button");
    remove.textContent = "×";
    remove.addEventListener("click", () => {
      removeChipThen(li, () => {
        state.jobs.splice(idx, 1);
        renderJobChips();
        refreshSchedule();
      });
    });
    li.appendChild(remove);
    els.jobList.appendChild(li);
  });
}

function currentDayCase() {
  return {
    case_id: "live",
    shop_open: els.shopOpen.value,
    shop_close: els.shopClose.value,
    cuts: state.cuts,
    jobs: state.jobs
  };
}

async function refreshSchedule() {
  const dayCase = currentDayCase();
  setLoading(true);
  let response;
  try {
    try {
      response = await fetch("/api/schedule", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(dayCase)
      });
    } catch (networkError) {
      console.error("Could not reach the server", networkError);
      showToast("Can't reach the server — is it still running? The plan below may be out of date.");
      return;
    }

    if (!response.ok) {
      console.error("Schedule request failed", await response.text());
      showToast("Could not build a plan for the current jobs and cuts.");
      return;
    }

    lastDayCase = dayCase;
    lastResult = await response.json();
    render();
  } finally {
    setLoading(false);
  }
}

async function moveJob(jobName, newStartHHMM, draggedBar) {
  const body = {
    day_case: lastDayCase,
    placements: lastResult.placements,
    unplaced: lastResult.unplaced,
    job_name: jobName,
    new_start: newStartHHMM
  };

  setLoading(true);
  let response;
  try {
    try {
      response = await fetch("/api/schedule/move", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
    } catch (networkError) {
      console.error("Could not reach the server", networkError);
      showToast("Can't reach the server — is it still running?");
      rejectDrag(draggedBar);
      return;
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ reason: "That move isn't allowed." }));
      showToast(error.reason || "That move isn't allowed.");
      rejectDrag(draggedBar);
      return;
    }

    lastResult = await response.json();
    render();
    flashSuccess(jobName);
  } finally {
    setLoading(false);
  }
}

function rejectDrag(bar) {
  if (!bar) { render(); return; }
  bar.classList.add("shake-reject");
  setTimeout(render, 300); // let the shake read before the bar snaps back to its real slot
}

function flashSuccess(jobName) {
  const bar = els.planRow.querySelector(`.bar[data-job-name="${CSS.escape(jobName)}"]`);
  if (!bar) return;
  bar.classList.add("just-moved");
  bar.addEventListener("animationend", () => bar.classList.remove("just-moved"), { once: true });
}

function render() {
  if (!lastDayCase) return;
  const openMin = toMinutes(lastDayCase.shop_open);
  const closeMin = toMinutes(lastDayCase.shop_close);
  const dayWraps = closeMin <= openMin;
  let totalMin = closeMin - openMin;
  if (dayWraps) totalMin += 24 * 60;
  const widthPx = totalMin * PX_PER_MIN;

  [els.timeAxis, els.cutsRow, els.planRow].forEach(el => {
    el.innerHTML = "";
    el.style.width = widthPx + "px";
  });

  for (let m = 0; m <= totalMin; m += 60) {
    const tick = document.createElement("div");
    tick.className = "tick";
    tick.style.left = (m * PX_PER_MIN) + "px";
    const hour = Math.floor((openMin + m) / 60) % 24;
    tick.textContent = String(hour).padStart(2, "0") + ":00";
    els.timeAxis.appendChild(tick);
  }

  lastDayCase.cuts.forEach((cut, idx) => {
    const start = minutesFromOpen(cut.start, openMin, dayWraps);
    let end = minutesFromOpen(cut.end, openMin, dayWraps);
    if (dayWraps && end <= start) end += 24 * 60;
    const bar = document.createElement("div");
    bar.className = "bar cut";
    bar.style.left = (start * PX_PER_MIN) + "px";
    bar.style.width = ((end - start) * PX_PER_MIN) + "px";
    bar.style.animationDelay = (idx * 35) + "ms, 0ms";
    bar.title = `Power cut ${cut.start}–${cut.end}`;
    els.cutsRow.appendChild(bar);
  });

  lastResult.placements.forEach((p, idx) => {
    const bar = document.createElement("div");
    const power = p.job.power.toLowerCase();
    bar.className = "bar " + power + (p.generatorMinutes > 0 ? " cost" : "");
    positionBar(bar, minutesFromOpen(p.start, openMin, dayWraps), p.job.minutes);
    bar.style.animationDelay = (idx * 35) + "ms";
    bar.textContent = p.job.name;
    bar.title = `${p.job.name}: ${p.start}–${p.end}` +
      (p.generatorMinutes > 0 ? ` (${p.generatorMinutes} generator min)` : "") +
      " — drag to reschedule";
    bar.dataset.jobName = p.job.name;
    bar.dataset.minutes = p.job.minutes;
    attachDragHandlers(bar, openMin, totalMin);
    els.planRow.appendChild(bar);
  });

  const newMinutes = lastResult.totalGeneratorMinutes;
  if (newMinutes !== displayedMinutes) {
    animateValue(displayedMinutes, newMinutes, 450, v => {
      els.generatorMinutes.textContent = Math.round(v);
    });
    pulseStat(els.generatorMinutes);
    displayedMinutes = newMinutes;
  }
  renderGeneratorCost();

  els.unplacedList.innerHTML = "";
  lastResult.unplaced.forEach(u => {
    const li = document.createElement("li");
    li.textContent = `${u.job.name}: ${u.reason}`;
    els.unplacedList.appendChild(li);
  });
}

function positionBar(bar, startOffsetMin, durationMin) {
  bar.style.left = (startOffsetMin * PX_PER_MIN) + "px";
  bar.style.width = Math.max(2, durationMin * PX_PER_MIN) + "px";
}

function renderGeneratorCost() {
  const rate = Number(els.ratePerHour.value);
  if (!rate || rate <= 0) {
    els.generatorCost.textContent = "—";
    displayedCost = null;
    return;
  }
  const cost = (lastResult.totalGeneratorMinutes / 60) * rate;
  if (displayedCost === null || Math.abs(cost - displayedCost) > 0.004) {
    animateValue(displayedCost, cost, 400, v => {
      els.generatorCost.textContent = `৳${v.toFixed(2)}`;
    });
    pulseStat(els.generatorCost);
  }
  displayedCost = cost;
}

function attachDragHandlers(bar, openMin, totalMin) {
  bar.addEventListener("mousedown", (downEvent) => {
    downEvent.preventDefault();
    const startLeftPx = parseFloat(bar.style.left);
    const startMouseX = downEvent.clientX;
    const durationMin = Number(bar.dataset.minutes);
    const jobName = bar.dataset.jobName;
    bar.classList.add("dragging");

    function onMouseMove(moveEvent) {
      const deltaPx = moveEvent.clientX - startMouseX;
      let newLeftPx = startLeftPx + deltaPx;
      newLeftPx = Math.max(0, Math.min(newLeftPx, (totalMin - durationMin) * PX_PER_MIN));
      bar.style.left = newLeftPx + "px";
    }

    function onMouseUp(upEvent) {
      document.removeEventListener("mousemove", onMouseMove);
      document.removeEventListener("mouseup", onMouseUp);
      bar.classList.remove("dragging");

      const deltaPx = upEvent.clientX - startMouseX;
      let newLeftPx = startLeftPx + deltaPx;
      newLeftPx = Math.max(0, Math.min(newLeftPx, (totalMin - durationMin) * PX_PER_MIN));
      const rawMin = newLeftPx / PX_PER_MIN;
      const snappedMin = Math.round(rawMin / SNAP_MIN) * SNAP_MIN;
      const newStart = toHHMM(openMin + snappedMin);

      moveJob(jobName, newStart, bar);
    }

    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
  });
}

[els.shopOpen, els.shopClose].forEach(el => el.addEventListener("change", refreshSchedule));

refreshSchedule();
