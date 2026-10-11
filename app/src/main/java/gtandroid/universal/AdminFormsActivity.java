package gtandroid.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Экран выбора ADM-форм (в стиле скриншота).
 * Открывается из MainActivity → Настройки → ADM-формы.
 */
public class AdminFormsActivity extends Activity {

    private LinearLayout commandsContainer;
    private SharedPreferences prefs;

    /** Текущее состояние (в процессе редактирования). */
    private final Set<String> selected = new LinkedHashSet<>();

    /** Все команды в списке: встроенные + кастомные. */
    private final java.util.List<String> allCommands = new java.util.ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.AdminFormsTheme);
        setContentView(R.layout.activity_admin_forms);

        prefs = getSharedPreferences("GTAndroidData", MODE_PRIVATE);
        commandsContainer = findViewById(R.id.commandsContainer);

        // Начальное состояние — из prefs
        selected.clear();
        selected.addAll(AdminFormManager.getEnabledCommands(this));

        // Собираем полный список: встроенные + кастомные
        rebuildAllCommands();
        renderList();

        // Кнопки
        Button btnAdd = findViewById(R.id.btnAddCommand);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnCancel = findViewById(R.id.btnCancel);

        btnAdd.setOnClickListener(v -> showAddCommandDialog());
        btnSave.setOnClickListener(v -> saveAndFinish());
        btnCancel.setOnClickListener(v -> finish());
    }

    private void rebuildAllCommands() {
        allCommands.clear();
        // Встроенные
        for (String c : AdminFormManager.getBuiltinCommands()) {
            allCommands.add(c);
        }
        // Кастомные
        for (String c : AdminFormManager.getCustomCommands(this)) {
            if (!allCommands.contains(c)) allCommands.add(c);
        }
    }

    private void renderList() {
        commandsContainer.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);

        for (final String cmd : allCommands) {
            View row = inf.inflate(
                    R.layout.item_admin_form_command,
                    commandsContainer, false);
            CheckBox cb = row.findViewById(R.id.cbCommand);
            TextView tv = row.findViewById(R.id.tvCommand);
            ImageButton btnDel = row.findViewById(R.id.btnDelete);

            tv.setText(cmd);
            cb.setChecked(selected.contains(cmd));

            // Клик по всей строке — переключает чекбокс
            row.setOnClickListener(v -> {
                boolean newState = !cb.isChecked();
                cb.setChecked(newState);
                if (newState) selected.add(cmd);
                else selected.remove(cmd);
            });

            // Удаление — только для кастомных
            boolean isCustom = AdminFormManager.getCustomCommands(this).contains(cmd);
            if (isCustom) {
                btnDel.setVisibility(View.VISIBLE);
                btnDel.setOnClickListener(v -> {
                    new AlertDialog.Builder(AdminFormsActivity.this,
                            android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("Удалить команду?")
                            .setMessage("Убрать " + cmd + " из списка?")
                            .setPositiveButton("Удалить", (d, w) -> {
                                AdminFormManager.removeCustomCommand(
                                        AdminFormsActivity.this, cmd);
                                selected.remove(cmd);
                                rebuildAllCommands();
                                renderList();
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                });
            } else {
                btnDel.setVisibility(View.GONE);
            }

            commandsContainer.addView(row);
        }
    }

    private void showAddCommandDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        TextView label = new TextView(this);
        label.setText("Команда (с /)");
        label.setTextColor(0xffffffff);
        label.setTextSize(14f);
        label.setPadding(0, 0, 0, 8);
        layout.addView(label);

        final EditText et = new EditText(this);
        et.setHint("/mycommand");
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setTextColor(0xffffffff);
        et.setHintTextColor(0xff7a6da0);
        et.setBackgroundColor(0xff2a1a4a);
        et.setPadding(24, 18, 24, 18);
        layout.addView(et);

        TextView hint = new TextView(this);
        hint.setText("Команда должна начинаться с /. Она добавится в список.");
        hint.setTextColor(0xff9a8fbf);
        hint.setTextSize(12f);
        hint.setPadding(0, 12, 0, 0);
        layout.addView(hint);

        new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Новая команда")
                .setView(layout)
                .setPositiveButton("Добавить", (d, w) -> {
                    String raw = et.getText().toString().trim();
                    if (raw.isEmpty()) {
                        Toast.makeText(this, "Введите команду",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!raw.startsWith("/")) raw = "/" + raw;
                    if (AdminFormManager.addCustomCommand(
                            AdminFormsActivity.this, raw)) {
                        selected.add(raw.toLowerCase());
                        rebuildAllCommands();
                        renderList();
                        Toast.makeText(this, "✅ Добавлено: " + raw,
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Такая команда уже есть",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveAndFinish() {
        // Дополнительно добавляем в enabled все встроенные, если их нет
        // (нужно, чтобы "по умолчанию" совпадало с прошлым поведением)
        AdminFormManager.setEnabledCommands(this, selected);
        Toast.makeText(this,
                "✅ Сохранено: " + selected.size() + " команд",
                Toast.LENGTH_SHORT).show();
        finish();
    }
}
