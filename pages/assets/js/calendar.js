/* ==========================================================================
   呼叫转移排班助手 · 排班日历（月视图 + 年视图）
   读取编辑器生成的配置（people + roster），月视图展示每日班次与当班人员，
   年视图按 12 个月汇总展示有排班的天数、涉及人员与班次。
   配色与编辑器一致：白班=蓝、夜班=深蓝、早/中班=紫、全天=青、晚班=橙。
   ========================================================================== */

(function () {
  "use strict";

  var calTitle = document.getElementById("calTitle");
  var calGrid = document.getElementById("calGrid");
  var calLegend = document.getElementById("calLegend");
  var calPrev = document.getElementById("calPrev");
  var calNext = document.getElementById("calNext");
  var calToday = document.getElementById("calToday");
  var calViewToggle = document.getElementById("calViewToggle");

  var WEEKDAYS = ["一", "二", "三", "四", "五", "六", "日"];

  // 班次 → 配色（与 hero-card 的 shift-dot 保持一致的语义色）
  var SHIFT_COLORS = {
    全天: { bg: "#0d9488", fg: "#ffffff" },
    白班: { bg: "#0ea5e9", fg: "#ffffff" },
    夜班: { bg: "#2563eb", fg: "#ffffff" },
    早班: { bg: "#7c3aed", fg: "#ffffff" },
    中班: { bg: "#9333ea", fg: "#ffffff" },
    晚班: { bg: "#f59e0b", fg: "#ffffff" },
  };

  function fallbackColor(shift) {
    return SHIFT_COLORS[shift] || { bg: "#64748b", fg: "#ffffff" };
  }

  // 当前展示的月份（Date 对象，取每月 1 日）
  var viewDate = new Date();
  viewDate.setDate(1);

  // 当前展示的年份（年视图用）
  var viewYear = viewDate.getFullYear();

  // 视图模式："month" | "year"
  var viewMode = "month";

  // 最新配置缓存
  var latestConfig = { people: {}, roster: [] };

  /* ---------- 工具 ---------- */

  function pad(n) {
    return String(n).padStart(2, "0");
  }

  function isoOf(d) {
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
  }

  function escapeHTML(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /* ---------- 图例 ---------- */

  function renderLegend(shiftNames) {
    var html = shiftNames
      .map(function (s) {
        var c = fallbackColor(s);
        return (
          '<span class="cal-tag" style="background-color:' +
          c.bg +
          ';border-color:#fff;">' +
          escapeHTML(s) +
          "</span>"
        );
      })
      .join("");
    calLegend.innerHTML = html;
  }

  // 收集配置中出现过的班次（去重，保持首次出现顺序）
  function collectShifts(roster) {
    var seen = [];
    roster.forEach(function (day) {
      (day.assignments || []).forEach(function (a) {
        if (a.shift && seen.indexOf(a.shift) < 0) seen.push(a.shift);
      });
    });
    return seen;
  }

  /* ---------- 月视图渲染 ---------- */

  function renderMonth() {
    var people = latestConfig.people || {};
    var roster = latestConfig.roster || [];

    // 建立 date → assignments 索引
    var byDate = {};
    roster.forEach(function (day) {
      byDate[day.date] = day.assignments || [];
    });

    // 更新标题
    calTitle.textContent =
      viewDate.getFullYear() + " 年 " + (viewDate.getMonth() + 1) + " 月";

    // 收集出现过的班次，生成图例
    var seenShifts = collectShifts(roster);
    if (seenShifts.length === 0) {
      seenShifts = Object.keys(SHIFT_COLORS);
    }
    renderLegend(seenShifts);

    // 计算月历网格
    var firstDay = new Date(viewDate.getFullYear(), viewDate.getMonth(), 1);
    var daysInMonth = new Date(
      viewDate.getFullYear(),
      viewDate.getMonth() + 1,
      0
    ).getDate();
    var startWeekday = (firstDay.getDay() + 6) % 7; // 周一=0

    var cells = [];

    // 表头
    var headHTML =
      '<div class="cal-row cal-head">' +
      WEEKDAYS.map(function (w) {
        return '<div class="cal-cell cal-head-cell">周' + w + "</div>";
      }).join("") +
      "</div>";

    // 前置空格（上月占位）
    var prevMonthDays = new Date(
      viewDate.getFullYear(),
      viewDate.getMonth(),
      0
    ).getDate();
    for (var i = 0; i < startWeekday; i++) {
      var pd = prevMonthDays - startWeekday + 1 + i;
      cells.push(
        '<div class="cal-cell cal-cell-other"><span class="cal-daynum">' +
          pd +
          "</span><div class='cal-events'></div></div>"
      );
    }

    // 当月
    var todayIso = isoOf(new Date());
    for (var d = 1; d <= daysInMonth; d++) {
      var dateISO =
        viewDate.getFullYear() +
        "-" +
        pad(viewDate.getMonth() + 1) +
        "-" +
        pad(d);
      var assignments = byDate[dateISO] || [];
      var isToday = dateISO === todayIso;

      var eventsHTML = assignments
        .map(function (a) {
          var phone = people[a.person] || "";
          var c = fallbackColor(a.shift);
          return (
            '<div class="cal-event" style="background-color:' +
            c.bg +
            ';color:' +
            c.fg +
            ';">' +
            '<span class="cal-event-shift">' +
            escapeHTML(a.shift) +
            "</span>" +
            '<span class="cal-event-person">' +
            escapeHTML(a.person || "未排") +
            (phone ? " · " + escapeHTML(phone) : "") +
            "</span>" +
            "</div>"
          );
        })
        .join("");

      cells.push(
        '<div class="cal-cell' +
          (isToday ? " cal-cell-today" : "") +
          (assignments.length > 0 ? " cal-cell-has-event" : "") +
          '" data-date="' +
          dateISO +
          '"><span class="cal-daynum">' +
          d +
          "</span><div class='cal-events'>" +
          eventsHTML +
          "</div></div>"
      );
    }

    // 补尾（下月占位，凑满整行）
    var totalCells = cells.length;
    var remainder = totalCells % 7;
    if (remainder !== 0) {
      for (var j = 0; j < 7 - remainder; j++) {
        cells.push(
          '<div class="cal-cell cal-cell-other"><span class="cal-daynum">' +
            (j + 1) +
            "</span><div class='cal-events'></div></div>"
        );
      }
    }

    calGrid.className = "cal-grid";
    calGrid.innerHTML = headHTML + cells.join("");
  }

  /* ---------- 年视图渲染 ---------- */

  function renderYear() {
    var roster = latestConfig.roster || [];

    calTitle.textContent = viewYear + " 年";

    // 收集该年出现过的班次，生成图例
    var yearShifts = collectShifts(
      roster.filter(function (day) {
        return String(day.date).slice(0, 4) === String(viewYear);
      })
    );
    if (yearShifts.length === 0) {
      yearShifts = Object.keys(SHIFT_COLORS);
    }
    renderLegend(yearShifts);

    // 按月统计：{ days: Set-like array, people: [], shifts: [] }
    var months = [];
    for (var m = 0; m < 12; m++) {
      months.push({ days: [], people: [], shifts: [] });
    }

    roster.forEach(function (day) {
      var date = String(day.date);
      if (date.slice(0, 4) !== String(viewYear)) return;
      var monthIdx = parseInt(date.slice(5, 7), 10) - 1;
      if (monthIdx < 0 || monthIdx > 11) return;

      var bucket = months[monthIdx];
      (day.assignments || []).forEach(function (a) {
        if (!a.shift && !a.person) return;
        if (bucket.days.indexOf(date) < 0) bucket.days.push(date);
        if (a.person && bucket.people.indexOf(a.person) < 0) {
          bucket.people.push(a.person);
        }
        if (a.shift && bucket.shifts.indexOf(a.shift) < 0) {
          bucket.shifts.push(a.shift);
        }
      });
    });

    var cards = months
      .map(function (bucket, idx) {
        var name = idx + 1;
        var dayCount = bucket.days.length;
        var peopleText = bucket.people.join("、");
        var shiftsHTML = bucket.shifts
          .map(function (s) {
            var c = fallbackColor(s);
            return (
              '<span class="cal-month-dot" style="background-color:' +
              c.bg +
              ';" title="' +
              escapeHTML(s) +
              '"></span>'
            );
          })
          .join("");

        var body;
        if (dayCount === 0) {
          body = '<div class="cal-month-empty">本月暂无排班</div>';
        } else {
          body =
            '<div class="cal-month-meta">' +
            '<div class="cal-month-people"><span class="cal-month-label">人员</span>' +
            '<span class="cal-month-value">' +
            escapeHTML(peopleText) +
            "</span></div>" +
            '<div class="cal-month-shifts"><span class="cal-month-label">班次</span>' +
            '<span class="cal-month-dots">' +
            shiftsHTML +
            "</span></div>" +
            "</div>";
        }

        return (
          '<div class="cal-month-card" data-month="' +
          pad(name) +
          '">' +
          '<div class="cal-month-head">' +
          '<span class="cal-month-name">' +
          name +
          "月</span>" +
          '<span class="cal-month-days">' +
          dayCount +
          " 天有排班</span>" +
          "</div>" +
          body +
          "</div>"
        );
      })
      .join("");

    calGrid.className = "cal-grid cal-year-grid";
    calGrid.innerHTML = cards;
  }

  /* ---------- 主入口：按视图模式渲染 ---------- */

  function renderCalendar(config) {
    latestConfig = config || latestConfig;
    if (viewMode === "year") {
      renderYear();
    } else {
      renderMonth();
    }
  }

  /* ---------- 视图切换 ---------- */

  function setViewMode(mode) {
    if (mode !== "month" && mode !== "year") return;
    viewMode = mode;

    // 更新 toggle 高亮
    var btns = calViewToggle.querySelectorAll(".cal-view-btn");
    for (var i = 0; i < btns.length; i++) {
      if (btns[i].getAttribute("data-view") === mode) {
        btns[i].classList.add("is-active");
      } else {
        btns[i].classList.remove("is-active");
      }
    }

    // 更新导航按钮语义
    var inYear = mode === "year";
    calPrev.setAttribute("title", inYear ? "上一年" : "上月");
    calNext.setAttribute("title", inYear ? "下一年" : "下月");
    calToday.textContent = inYear ? "返回今年" : "返回今日";

    renderCalendar();
  }

  calViewToggle.addEventListener("click", function (e) {
    var btn = e.target.closest(".cal-view-btn");
    if (!btn) return;
    setViewMode(btn.getAttribute("data-view"));
  });

  /* ---------- 月份/年份导航（带动画） ---------- */

  function renderWithAnimation(direction) {
    // direction: -1 向左切(上月/上一年)，1 向右切(下月/下一年)，0 无方向（首次/今日）
    calGrid.classList.remove("cal-anim-left", "cal-anim-right");
    if (direction !== 0) {
      void calGrid.offsetWidth; // 强制重排
      calGrid.classList.add(direction < 0 ? "cal-anim-right" : "cal-anim-left");
    }
    renderCalendar();
  }

  function shiftMonth(delta) {
    viewDate.setMonth(viewDate.getMonth() + delta);
    renderWithAnimation(delta);
  }

  function shiftYear(delta) {
    viewYear += delta;
    renderWithAnimation(delta);
  }

  calPrev.addEventListener("click", function () {
    if (viewMode === "year") shiftYear(-1);
    else shiftMonth(-1);
  });
  calNext.addEventListener("click", function () {
    if (viewMode === "year") shiftYear(1);
    else shiftMonth(1);
  });
  calToday.addEventListener("click", function () {
    var now = new Date();
    viewDate = now;
    viewDate.setDate(1);
    viewYear = now.getFullYear();
    renderWithAnimation(0);
  });

  /* ---------- 点击：定位到编辑器 / 切到该月月视图 ---------- */

  calGrid.addEventListener("click", function (e) {
    if (viewMode === "year") {
      var card = e.target.closest(".cal-month-card");
      if (!card) return;
      var monthIdx = parseInt(card.getAttribute("data-month"), 10) - 1;
      viewDate = new Date(viewYear, monthIdx, 1);
      setViewMode("month");
      return;
    }

    var cell = e.target.closest(".cal-cell[data-date]");
    if (!cell) return;
    var dateISO = cell.getAttribute("data-date");
    if (window.focusDay && window.focusDay(dateISO)) {
      // 定位成功后平滑滚动到编辑器区域
      var editor = document.getElementById("editor");
      if (editor) editor.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  });

  /* ---------- 拖拽切换（触摸/鼠标横滑） ---------- */

  var dragStartX = null;
  var dragStartY = null;
  var dragging = false;

  calGrid.addEventListener("touchstart", function (e) {
    var t = e.touches[0];
    dragStartX = t.clientX;
    dragStartY = t.clientY;
    dragging = true;
  }, { passive: true });

  calGrid.addEventListener("touchmove", function (e) {
    // 阻止垂直滚动时的误触发
    if (!dragging) return;
    var t = e.touches[0];
    var dx = t.clientX - dragStartX;
    var dy = t.clientY - dragStartY;
    if (Math.abs(dx) > Math.abs(dy)) {
      e.preventDefault();
    }
  }, { passive: false });

  calGrid.addEventListener("touchend", function (e) {
    if (!dragging) return;
    dragging = false;
    var t = e.changedTouches[0];
    var dx = t.clientX - dragStartX;
    var dy = t.clientY - dragStartY;
    if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) {
      if (viewMode === "year") shiftYear(dx > 0 ? -1 : 1);
      else shiftMonth(dx > 0 ? -1 : 1);
    }
    dragStartX = null;
    dragStartY = null;
  }, { passive: true });

  /* ---------- 暴露给 editor.js ---------- */

  window.renderCalendar = renderCalendar;

  // 首次空渲染
  setViewMode("month");
})();
