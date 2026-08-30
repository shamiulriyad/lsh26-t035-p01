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
  toast: document.getElementById("toast")
};

function toMinutes(hhmm) {
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

function toHHMM(totalMin) {
  const h = Math.floor(totalMin / 60) % 24;
  const m = totalMin % 60;
  return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
}

let toastTimer = null;
function showToast(message) {
  els.toast.textContent = message;
  els.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { els.toast.hidden = true; }, 3500);
}

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
  if (!name || !minutes || minutes % 15 !== 0) {
    alert("Give the job a name and a duration that's a multiple of 15 minutes.");
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

function renderCutChips() {
  els.cutList.innerHTML = "";
  state.cuts.forEach((cut, idx) => {
    const li = document.createElement("li");
    li.textContent = `${cut.start}–${cut.end}`;
    const remove = document.createElement("button");
    remove.textContent = "×";
    remove.addEventListener("click", () => {
      state.cuts.splice(idx, 1);
      renderCutChips();
      refreshSchedule();
    });
    li.appendChild(remove);
    els.cutList.appendChild(li);
  });
}

function renderJobChips() {
  els.jobList.innerHTML = "";
  state.jobs.forEach((job, idx) => {
    const li = document.createElement("li");
    li.textContent = `${job.name} (${job.minutes}m, ${job.power})`;
    const remove = document.createElement("button");
    remove.textContent = "×";
    remove.addEventListener("click", () => {
      state.jobs.splice(idx, 1);
      renderJobChips();
      refreshSchedule();
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

  const response = await fetch("/api/schedule", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(dayCase)
  });

  if (!response.ok) {
    console.error("Schedule request failed", await response.text());
    showToast("Could not build a plan for the current jobs and cuts.");
    return;
  }

  lastDayCase = dayCase;
  lastResult = await response.json();
  render();
}

async function moveJob(jobName, newStartHHMM) {
  const body = {
    day_case: lastDayCase,
    placements: lastResult.placements,
    unplaced: lastResult.unplaced,
    job_name: jobName,
    new_start: newStartHHMM
  };

  const response = await fetch("/api/schedule/move", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ reason: "That move isn't allowed." }));
    showToast(error.reason || "That move isn't allowed.");
    render(); // snap back to the last known-good plan
    return;
  }

  lastResult = await response.json();
  render();
}

function render() {
  if (!lastDayCase) return;
  const openMin = toMinutes(lastDayCase.shop_open);
  const closeMin = toMinutes(lastDayCase.shop_close);
  const totalMin = Math.max(0, closeMin - openMin);
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

  lastDayCase.cuts.forEach(cut => {
    const start = toMinutes(cut.start) - openMin;
    const end = toMinutes(cut.end) - openMin;
    const bar = document.createElement("div");
    bar.className = "bar cut";
    bar.style.left = (start * PX_PER_MIN) + "px";
    bar.style.width = ((end - start) * PX_PER_MIN) + "px";
    bar.title = `Power cut ${cut.start}–${cut.end}`;
    els.cutsRow.appendChild(bar);
  });

  lastResult.placements.forEach(p => {
    const bar = document.createElement("div");
    const power = p.job.power.toLowerCase();
    bar.className = "bar " + power + (p.generatorMinutes > 0 ? " cost" : "");
    positionBar(bar, toMinutes(p.start) - openMin, p.job.minutes);
    bar.textContent = p.job.name;
    bar.title = `${p.job.name}: ${p.start}–${p.end}` +
      (p.generatorMinutes > 0 ? ` (${p.generatorMinutes} generator min)` : "") +
      " — drag to reschedule";
    bar.dataset.jobName = p.job.name;
    bar.dataset.minutes = p.job.minutes;
    attachDragHandlers(bar, openMin, totalMin);
    els.planRow.appendChild(bar);
  });

  els.generatorMinutes.textContent = lastResult.totalGeneratorMinutes;
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
    els.generatorCost.textContent = "";
    return;
  }
  const cost = (lastResult.totalGeneratorMinutes / 60) * rate;
  els.generatorCost.textContent = `≈ ৳${cost.toFixed(2)} today`;
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

      moveJob(jobName, newStart);
    }

    document.addEventListener("mousemove", onMouseMove);
    document.addEventListener("mouseup", onMouseUp);
  });
}

[els.shopOpen, els.shopClose].forEach(el => el.addEventListener("change", refreshSchedule));

refreshSchedule();
