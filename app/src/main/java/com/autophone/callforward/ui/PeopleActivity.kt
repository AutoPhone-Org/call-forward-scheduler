package com.autophone.callforward.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.autophone.callforward.R
import com.autophone.callforward.data.RosterStore
import com.autophone.callforward.model.DayRoster
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 人员管理界面：列表展示人员 + 手机号，支持新增/删除。
 * 数据经 [RosterStore] 持久化，删除时同步清理排班中对该人员的引用（置空指派）。
 */
class PeopleActivity : AppCompatActivity() {

    private lateinit var store: RosterStore
    private lateinit var listView: ListView
    private lateinit var emptyText: android.widget.TextView
    private lateinit var addButton: MaterialButton

    private var people = linkedMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_people)

        store = RosterStore(this)
        listView = findViewById(R.id.peopleList)
        emptyText = findViewById(R.id.peopleEmpty)
        addButton = findViewById(R.id.peopleAddButton)

        loadPeople()
        addButton.setOnClickListener { showAddDialog() }
        listView.setOnItemClickListener { _, _, position, _ ->
            people.keys.elementAtOrNull(position)?.let { showDeleteDialog(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        loadPeople()
    }

    private fun loadPeople() {
        people = store.loadPeople().mapValues { it.value.phone }.toMap(linkedMapOf())
        render()
    }

    private fun render() {
        emptyText.visibility = if (people.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        val rows = people.entries.map { (name, phone) -> "$name  ($phone)" }
        val adapter = object : ArrayAdapter<String>(
            this, android.R.layout.simple_list_item_1, rows
        ) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val tv = view.findViewById<android.widget.TextView>(android.R.id.text1)
                tv.setTextColor(getColor(R.color.text_primary))
                tv.textSize = 16f
                return view
            }
        }
        listView.adapter = adapter
    }

    private fun showAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        val nameLayout = TextInputLayout(this).apply {
            hint = getString(R.string.people_name_hint)
        }
        val nameInput = TextInputEditText(this).apply {
            setSingleLine(true)
        }
        nameLayout.addView(nameInput)
        container.addView(nameLayout)

        val phoneLayout = TextInputLayout(this).apply {
            hint = getString(R.string.people_phone_hint)
        }
        val phoneInput = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
        }
        phoneLayout.addView(phoneInput)
        container.addView(phoneLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.people_add)
            .setView(container)
            .setPositiveButton(R.string.people_confirm) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val phone = phoneInput.text?.toString()?.trim().orEmpty()
                addPerson(name, phone)
            }
            .setNegativeButton(R.string.people_cancel, null)
            .show()
    }

    private fun addPerson(name: String, phone: String) {
        when {
            name.isEmpty() -> toast(R.string.people_err_name_empty)
            phone.isEmpty() -> toast(R.string.people_err_phone_empty)
            people.containsKey(name) -> toast(R.string.people_err_duplicate)
            else -> {
                people[name] = phone
                persist()
                render()
            }
        }
    }

    private fun showDeleteDialog(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.people_delete_title)
            .setMessage(getString(R.string.people_delete_confirm, name))
            .setPositiveButton(R.string.people_confirm) { _, _ ->
                people.remove(name)
                // 清理排班中对被删人员的引用后一并保存
                val roster = store.loadRoster().map { day ->
                    DayRoster(
                        date = day.date,
                        assignments = day.assignments.filterNot { it.personName == name },
                    )
                }
                store.save(people, roster)
                render()
            }
            .setNegativeButton(R.string.people_cancel, null)
            .show()
    }

    private fun persist() {
        store.save(people, store.loadRoster())
    }

    private fun toast(resId: Int) {
        android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
    }
}
