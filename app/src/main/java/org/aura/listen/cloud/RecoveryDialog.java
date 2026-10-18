package org.aura.listen.cloud;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Recovery codes and new passwords never cross the WebView bridge. */
public final class RecoveryDialog {
    private RecoveryDialog() {}
    public static void show(Activity activity, CloudSync cloud, CloudSync.Result result) {
        LinearLayout form = new LinearLayout(activity); form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(22 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, padding / 2);
        TextView help = new TextView(activity);
        help.setText(org.aura.listen.R.string.cloud_recovery_help);
        form.addView(help);
        EditText email = field(activity, form, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 254);
        EditText code = field(activity, form, "Recovery code from email", InputType.TYPE_CLASS_NUMBER, 10);
        EditText password = field(activity, form, "New password (12–128 characters)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 128);
        ScrollView scroll = new ScrollView(activity); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Reset your password").setView(scroll).setNegativeButton("Cancel", null)
            .setNeutralButton("Email code", null).setPositiveButton("Reset password", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String mail = email.getText().toString().trim();
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { email.setError("Enter a valid email."); return; }
                cloud.recover(mail, null, null, (message, backup) -> activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed() && dialog.isShowing()) help.setText(message);
                }));
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String mail = email.getText().toString().trim(), token = code.getText().toString().trim(), secret = password.getText().toString();
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { email.setError("Enter a valid email."); return; }
                if (!token.matches("[0-9]{6,10}")) { code.setError("Enter the code from your email."); return; }
                if (secret.length() < 12) { password.setError("Use at least 12 characters."); return; }
                password.setText(""); code.setText(""); dialog.dismiss(); cloud.recover(mail, token, secret, result);
            });
        });
        dialog.setOnDismissListener(d -> { password.setText(""); code.setText(""); });
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }
    private static EditText field(Activity activity, LinearLayout form, String label, int type, int max) {
        TextView title = new TextView(activity); title.setText(label); form.addView(title);
        EditText input = new EditText(activity); input.setId(View.generateViewId()); title.setLabelFor(input.getId());
        input.setInputType(type); input.setSingleLine(true); input.setContentDescription(label);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO); input.setSaveEnabled(false);
        form.addView(input); return input;
    }
}
