/* ==========================================================================
   呼叫转移排班助手 · 配置编辑器
   负责人员/转移类型/排班表的录入，并生成 callforward://import Deep Link
   ========================================================================== */

(function () {
  "use strict";

  // 默认班次名（与 App 端 DefaultTemplates.DEFAULT_SHIFTS 保持一致）
  var SHIFT_NAMES = ["全天", "白班", "夜班", "早班", "中班", "晚班"];

  // 转移类型取值（与 App 端 ForwardType 名称一一对应，小写）
  var FORWARD_TYPES = {
    unconditional: "无条件转移",
    busy: "遇忙转移",
    noanswer: "无应答转移",
    unreachable: "不可及转移",
  };

  var peopleList = document.getElementById("peopleList");
  var rosterList = document.getElementById("rosterList");
  var addPersonBtn = document.getElementById("addPerson");
  var addDayBtn = document.getElementById("addDay");
  var importLink = document.getElementById("importLink");
  var copyLinkBtn = document.getElementById("copyLink");
  var linkText = document.getElementById("linkText");
  var linkPreview = document.getElementById("linkPreview");
  var toast = document.getElementById("toast");

  /* ---------- 工具函数 ---------- */

  function todayISO() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, "0");
    var day = String(d.getDate()).padStart(2, "0");
    return d.getFullYear() + "-" + m + "-" + day;
  }

  function addDaysISO(iso, n) {
    var d = new Date(iso + "T00:00:00");
    d.setDate(d.getDate() + n);
    var m = String(d.getMonth() + 1).padStart(2, "0");
    var day = String(d.getDate()).padStart(2, "0");
    return d.getFullYear() + "-" + m + "-" + day;
  }

  function el(html) {
    var t = document.createElement("template");
    t.innerHTML = html.trim();
    return t.content.firstElementChild;
  }

  function showToast(msg) {
    toast.textContent = msg;
    toast.classList.add("show");
    clearTimeout(showToast._t);
    showToast._t = setTimeout(function () {
      toast.classList.remove("show");
    }, 2200);
  }

  function escapeAttr(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/"/g, "&quot;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  /* ---------- 人员 ---------- */

  function personRowHTML(name, phone) {
    return (
      '<div class="person-row">' +
      '<input class="input person-name" type="text" placeholder="姓名" value="' +
      escapeAttr(name || "") +
      '" />' +
      '<input class="input person-phone" type="tel" placeholder="手机号" value="' +
      escapeAttr(phone || "") +
      '" />' +
      '<button class="icon-btn" type="button" data-action="remove-person" title="删除">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
      "</button>" +
      "</div>"
    );
  }

  function addPerson(name, phone) {
    peopleList.appendChild(el(personRowHTML(name, phone)));
    refreshPersonOptions();
  }

  /* ---------- 排班日 ---------- */

  function assignmentRowHTML(peopleNames, shift, person) {
    var options = SHIFT_NAMES.map(function (s) {
      return (
        '<option value="' +
        escapeAttr(s) +
        '"' +
        (s === shift ? " selected" : "") +
        ">" +
        escapeAttr(s) +
        "</option>"
      );
    }).join("");

    var pOptions = peopleNames
      .map(function (p) {
        return (
          '<option value="' +
          escapeAttr(p) +
          '"' +
          (p === person ? " selected" : "") +
          ">" +
          escapeAttr(p) +
          "</option>"
        );
      })
      .join("");

    return (
      '<div class="assignment-row">' +
      '<select class="select assignment-shift">' +
      options +
      "</select>" +
      '<select class="select assignment-person">' +
      pOptions +
      "</select>" +
      '<button class="icon-btn" type="button" data-action="remove-assignment" title="删除班次">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
      "</button>" +
      "</div>"
    );
  }

  function dayCardHTML(date, assignments) {
    var peopleNames = collectPeopleNames();
    var rows = (assignments || [{ shift: "白班", person: peopleNames[0] || "" }])
      .map(function (a) {
        return assignmentRowHTML(peopleNames, a.shift, a.person);
      })
      .join("");

    return (
      '<div class="field-block day-card">' +
      '<div class="field-title" style="font-size:14.5px;">' +
      '<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color:#2563eb;"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>' +
      '<input class="input day-date" type="date" value="' +
      escapeAttr(date) +
      '" style="width:auto;padding:6px 10px;font-size:14px;" />' +
      '<button class="icon-btn" type="button" data-action="remove-day" title="删除当天" style="width:36px;height:36px;margin-left:auto;">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>' +
      "</button>" +
      "</div>" +
      '<div class="assignments-list">' +
      rows +
      "</div>" +
      '<button class="add-btn" type="button" data-action="add-assignment" style="margin-top:10px;">' +
      '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>' +
      "添加班次" +
      "</button>" +
      "</div>"
    );
  }

  function addDay(date, assignments) {
    rosterList.appendChild(el(dayCardHTML(date || todayISO(), assignments)));
  }

  /* ---------- 人员下拉选项联动 ---------- */

  function collectPeopleNames() {
    var names = [];
    peopleList.querySelectorAll(".person-name").forEach(function (input) {
      var v = input.value.trim();
      if (v) names.push(v);
    });
    return names;
  }

  function refreshPersonOptions() {
    var names = collectPeopleNames();
    rosterList.querySelectorAll(".assignment-row").forEach(function (row) {
      var select = row.querySelector(".assignment-person");
      var prev = select.value;
      var html = names
        .map(function (p) {
          return (
            '<option value="' +
            escapeAttr(p) +
            '"' +
            (p === prev ? " selected" : "") +
            ">" +
            escapeAttr(p) +
            "</option>"
          );
        })
        .join("");
      select.innerHTML = html;
      if (names.indexOf(prev) >= 0) select.value = prev;
    });
  }

  /* ---------- 收集配置 ---------- */

  function collectConfig() {
    var people = {};
    peopleList.querySelectorAll(".person-row").forEach(function (row) {
      var name = row.querySelector(".person-name").value.trim();
      var phone = row.querySelector(".person-phone").value.trim();
      if (name) people[name] = phone;
    });

    var forwardType = "unconditional";
    var checked = document.querySelector(
      'input[name="forwardType"]:checked'
    );
    if (checked) forwardType = checked.value;

    var roster = [];
    rosterList.querySelectorAll(".day-card").forEach(function (card) {
      var date = card.querySelector(".day-date").value;
      var assignments = [];
      card.querySelectorAll(".assignment-row").forEach(function (row) {
        var shift = row.querySelector(".assignment-shift").value;
        var person = row.querySelector(".assignment-person").value;
        if (shift && person) assignments.push({ shift: shift, person: person });
      });
      if (date) roster.push({ date: date, assignments: assignments });
    });

    return { people: people, forwardType: forwardType, roster: roster };
  }

  /* ---------- Deep Link 生成 ---------- */

  // UTF-8 安全的 Base64 编码：先 encodeURIComponent 转字节再 btoa
  function utf8Base64(str) {
    return btoa(unescape(encodeURIComponent(str)));
  }

  function buildImportUrl(config) {
    var json = JSON.stringify(config);
    var encoded = encodeURIComponent(utf8Base64(json));
    return "callforward://import?config=" + encoded;
  }

  function updateLink() {
    var config = collectConfig();
    var hasPeople = Object.keys(config.people).length > 0;
    var hasRoster = config.roster.length > 0;

    if (!hasPeople && !hasRoster) {
      importLink.removeAttribute("href");
      importLink.setAttribute("aria-disabled", "true");
      importLink.classList.add("disabled");
      linkText.textContent = "填写上方配置后，链接将在此显示。";
      return;
    }

    var url = buildImportUrl(config);
    importLink.setAttribute("href", url);
    importLink.removeAttribute("aria-disabled");
    importLink.classList.remove("disabled");
    linkText.textContent = url;
  }

  /* ---------- 事件委托 ---------- */

  document.addEventListener("click", function (e) {
    var btn = e.target.closest("button[data-action]");
    if (!btn) return;
    var action = btn.getAttribute("data-action");

    if (action === "add-person") {
      addPerson();
    } else if (action === "remove-person") {
      btn.closest(".person-row").remove();
      refreshPersonOptions();
      updateLink();
    } else if (action === "add-day") {
      addDay();
      updateLink();
    } else if (action === "remove-day") {
      btn.closest(".day-card").remove();
      updateLink();
    } else if (action === "add-assignment") {
      var card = btn.closest(".day-card");
      var names = collectPeopleNames();
      card
        .querySelector(".assignments-list")
        .appendChild(el(assignmentRowHTML(names, "白班", names[0] || "")));
      updateLink();
    } else if (action === "remove-assignment") {
      btn.closest(".assignment-row").remove();
      updateLink();
    }
  });

  document.addEventListener("input", function (e) {
    if (e.target.classList.contains("person-name")) {
      refreshPersonOptions();
    }
    updateLink();
  });

  document.addEventListener("change", function () {
    updateLink();
  });

  copyLinkBtn.addEventListener("click", function () {
    var url = linkText.textContent;
    if (!url || url.indexOf("callforward://") !== 0) {
      showToast("请先填写配置");
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(url).then(
        function () {
          showToast("链接已复制到剪贴板");
        },
        function () {
          fallbackCopy(url);
        }
      );
    } else {
      fallbackCopy(url);
    }
  });

  function fallbackCopy(text) {
    var ta = document.createElement("textarea");
    ta.value = text;
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    document.body.appendChild(ta);
    ta.select();
    try {
      document.execCommand("copy");
      showToast("链接已复制到剪贴板");
    } catch (err) {
      showToast("复制失败，请手动选择复制");
    }
    document.body.removeChild(ta);
  }

  /* ---------- 初始化示例数据 ---------- */

  function init() {
    // 默认人员示例
    addPerson("思源", "13800000001");
    addPerson("林澈", "13900000002");
    addPerson("苏芮", "13700000003");

    // 默认排班示例：今天白班 + 明天夜班
    var today = todayISO();
    addDay(today, [{ shift: "白班", person: "思源" }]);
    addDay(addDaysISO(today, 1), [{ shift: "夜班", person: "林澈" }]);

    updateLink();
  }

  init();
})();
