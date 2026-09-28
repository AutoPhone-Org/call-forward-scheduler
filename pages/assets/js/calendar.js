/* ==========================================================================
   呼叫转移排班助手 · 排班日历（月视图）
   读取编辑器生成的配置（people + roster），以月视图展示每日班次与当班人员。
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

  /* ---------- 月视图渲染 ---------- */

  function renderCalendar(config) {
    latestConfig = config || latestConfig;

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

    // 收集本月出现过的班次，生成图例
    var seenShifts = [];
    roster.forEach(function (day) {
      (day.assignments || []).forEach(function (a) {
        if (a.shift && seenShifts.indexOf(a.shift) < 0) seenShifts.push(a.shift);
      });
    });
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

    calGrid.innerHTML = headHTML + cells.join("");
  }

  /* ---------- 月份导航 ---------- */

  function shiftMonth(delta) {
    viewDate.setMonth(viewDate.getMonth() + delta);
    renderCalendar();
  }

  calPrev.addEventListener("click", function () {
    shiftMonth(-1);
  });
  calNext.addEventListener("click", function () {
    shiftMonth(1);
  });
  calToday.addEventListener("click", function () {
    viewDate = new Date();
    viewDate.setDate(1);
    renderCalendar();
  });

  /* ---------- 暴露给 editor.js ---------- */

  window.renderCalendar = renderCalendar;

  // 首次空渲染
  renderCalendar();
})();
