package au.rifthouse.kiosk;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;

/**
 * Shows the RiftHouse Pantry page full screen. Asks for the server's address the first time.
 * After the set number of minutes with nobody touching the screen it goes back to the Pantry page, and (if allowed)
 * it brings itself back to the front if you have been away in another app. Settings: press and hold the top-left corner for 3 seconds.
 */
public class MainActivity extends Activity {
    static final String PREFS = "kiosk";
    private static final String DEFAULT_PAGE = "/pantry/";
    private static final int DEFAULT_PORT = 3000;
    private static final int DEFAULT_MINUTES = 15;

    private SharedPreferences sp;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView web;
    private SeasonOverlay overlay;
    private LinearLayout errorBox;
    private TextView errorText;
    private AlertDialog dialog;

    private long lastTouch = System.currentTimeMillis();
    private boolean idleHandled = false, pageFailed = false, clearHistoryNext = false;
    private float downX, downY;

    private final Runnable cornerHold = () -> openSettings(false);
    private final Runnable idleTick = new Runnable() {
        @Override public void run() { checkIdle(); handler.postDelayed(this, 20_000); }
    };
    private final Runnable retry = () -> { if (errorBox.getVisibility() == View.VISIBLE) loadHome(); };

    // ------------------------------------------------------------------ life cycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#171412"));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setUpWebView();

        overlay = new SeasonOverlay(this);
        root.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildErrorBox();
        setContentView(root);

        applyTheme();
        if (baseUrl().isEmpty()) openSettings(true); else loadHome();
    }

    @Override
    protected void onStart() {
        super.onStart();
        cancelReturn();
        lastTouch = System.currentTimeMillis();
        idleHandled = false;
        handler.removeCallbacks(idleTick);
        handler.postDelayed(idleTick, 20_000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        applyTheme(); // the date may have moved on to a different season
    }

    @Override
    protected void onPause() {
        web.onPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(idleTick);
        if (sp.getBoolean("ret", true) && !baseUrl().isEmpty()) scheduleReturn();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        web.destroy();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) goImmersive();
    }

    @SuppressWarnings("deprecation")
    private void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onBackPressed() {
        if (errorBox.getVisibility() == View.VISIBLE) return;
        if (web.canGoBack()) web.goBack();
        // otherwise do nothing: this is a display, not something to back out of
    }

    // ------------------------------------------------------------------ touch: idle timer and the hidden settings corner

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastTouch = System.currentTimeMillis();
        idleHandled = false;
        float corner = 80 * getResources().getDisplayMetrics().density;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getX(); downY = ev.getY();
                handler.removeCallbacks(cornerHold);
                if (downX < corner && downY < corner) handler.postDelayed(cornerHold, 3000);
                break;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(ev.getX() - downX) > 30 || Math.abs(ev.getY() - downY) > 30) handler.removeCallbacks(cornerHold);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(cornerHold);
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private void checkIdle() {
        if (dialog != null && dialog.isShowing()) return;
        long mins = Math.max(1, sp.getInt("mins", DEFAULT_MINUTES));
        if (!idleHandled && System.currentTimeMillis() - lastTouch >= mins * 60_000L && !baseUrl().isEmpty()) {
            idleHandled = true;      // once per quiet spell; the next touch starts a new one
            clearHistoryNext = true;
            loadHome();              // back to the Pantry page, even if it is already showing (so any tab you left open is reset)
        }
    }

    // ------------------------------------------------------------------ the web page

    @SuppressWarnings("SetJavaScriptEnabled")
    private void setUpWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setBuiltInZoomControls(false);
        s.setSupportZoom(false);
        CookieManager.getInstance().setAcceptCookie(true); // so you stay signed in
        web.setBackgroundColor(Color.parseColor("#171412"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String host = Uri.parse(baseUrl()).getHost();
                return host == null || !host.equalsIgnoreCase(r.getUrl().getHost()); // only pages from your server open here
            }
            @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap f) { pageFailed = false; }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) { pageFailed = true; showError("Cannot reach " + baseUrl() + "\n" + e.getDescription()); }
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (!pageFailed) hideError();
                if (clearHistoryNext) { clearHistoryNext = false; v.clearHistory(); }
            }
        });
    }

    /** "192.168.1.50" or "192.168.1.50:3000" or "http://host:3000/" all become "http://host:port". */
    static String normalise(String raw) {
        String a = raw == null ? "" : raw.trim().replaceAll("\\s+", "");
        if (a.isEmpty()) return "";
        String scheme = "http://";
        if (a.regionMatches(true, 0, "https://", 0, 8)) { scheme = "https://"; a = a.substring(8); }
        else if (a.regionMatches(true, 0, "http://", 0, 7)) a = a.substring(7);
        int slash = a.indexOf('/'); if (slash >= 0) a = a.substring(0, slash);
        if (a.isEmpty()) return "";
        if (!a.contains(":") && scheme.equals("http://")) a = a + ":" + DEFAULT_PORT; // RiftHouse listens on 3000 unless you changed it
        return scheme + a;
    }

    private String baseUrl() { return normalise(sp.getString("addr", "")); }

    private String homeUrl() {
        String page = sp.getString("page", DEFAULT_PAGE).trim();
        if (page.isEmpty()) page = DEFAULT_PAGE;
        if (!page.startsWith("/")) page = "/" + page;
        return baseUrl() + page;
    }

    private void loadHome() {
        if (baseUrl().isEmpty()) return;
        handler.removeCallbacks(retry);
        web.loadUrl(homeUrl());
    }

    // ------------------------------------------------------------------ "cannot reach the server" screen

    private void buildErrorBox() {
        float d = getResources().getDisplayMetrics().density;
        errorBox = new LinearLayout(this);
        errorBox.setOrientation(LinearLayout.VERTICAL);
        errorBox.setGravity(Gravity.CENTER);
        errorBox.setBackgroundColor(Color.parseColor("#171412"));
        errorBox.setPadding((int) (32 * d), (int) (32 * d), (int) (32 * d), (int) (32 * d));
        errorBox.setClickable(true);
        errorBox.setVisibility(View.GONE);
        TextView head = new TextView(this);
        head.setText("Cannot reach the kitchen screen");
        head.setTextColor(Color.parseColor("#F4EFE6")); head.setTextSize(26); head.setTypeface(Typeface.SERIF); head.setGravity(Gravity.CENTER);
        errorText = new TextView(this);
        errorText.setTextColor(Color.parseColor("#BBAE9C")); errorText.setTextSize(16); errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, (int) (12 * d), 0, (int) (20 * d));
        TextView note = new TextView(this);
        note.setText("It will keep trying every 15 seconds. Check the tablet is on the home wifi and the RiftHouse computer is switched on.");
        note.setTextColor(Color.parseColor("#BBAE9C")); note.setTextSize(14); note.setGravity(Gravity.CENTER);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER); row.setPadding(0, (int) (20 * d), 0, 0);
        Button again = new Button(this); again.setText("Try again"); again.setOnClickListener(v -> loadHome());
        Button set = new Button(this); set.setText("Change address"); set.setOnClickListener(v -> openSettings(false));
        row.addView(again); row.addView(set);
        errorBox.addView(head); errorBox.addView(errorText); errorBox.addView(note); errorBox.addView(row);
        root.addView(errorBox, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showError(String msg) {
        errorText.setText(msg);
        errorBox.setVisibility(View.VISIBLE);
        handler.removeCallbacks(retry);
        handler.postDelayed(retry, 15_000);
    }

    private void hideError() {
        handler.removeCallbacks(retry);
        errorBox.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ themes

    private void applyTheme() {
        overlay.set(Season.resolve(sp.getString("theme", "auto"), LocalDate.now()), sp.getBoolean("anim", true));
    }

    // ------------------------------------------------------------------ settings

    private void openSettings(boolean firstTime) {
        if (dialog != null && dialog.isShowing()) return;
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText addr = field(box, firstTime ? "Address of the RiftHouse computer" : "Address", "e.g. 192.168.1.50:3000", sp.getString("addr", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        TextView hint = new TextView(this);
        hint.setText("The numbers the computer has on your home wifi. If you leave off the port, " + DEFAULT_PORT + " is used.");
        hint.setTextSize(12); box.addView(hint);
        EditText page = field(box, "Page to open", DEFAULT_PAGE, sp.getString("page", DEFAULT_PAGE), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText mins = field(box, "Go back to that page after this many minutes with no touching", "15", String.valueOf(sp.getInt("mins", DEFAULT_MINUTES)), InputType.TYPE_CLASS_NUMBER);

        TextView tl = new TextView(this); tl.setText("Theme"); tl.setPadding(0, pad / 2, 0, 0); box.addView(tl);
        Spinner theme = new Spinner(this);
        theme.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Season.LABELS));
        String cur = sp.getString("theme", "auto");
        for (int i = 0; i < Season.KEYS.length; i++) if (Season.KEYS[i].equals(cur)) theme.setSelection(i);
        box.addView(theme);

        CheckBox anim = new CheckBox(this); anim.setText("Move the decorations (snow, confetti, bats, eggs)"); anim.setChecked(sp.getBoolean("anim", true)); box.addView(anim);
        CheckBox ret = new CheckBox(this); ret.setText("Come back to this app after I have been away in another app"); ret.setChecked(sp.getBoolean("ret", true)); box.addView(ret);
        Button perm = new Button(this); perm.setText("Allow “display over other apps” (needed to come back)");
        perm.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        box.addView(perm);

        ScrollView sv = new ScrollView(this); sv.addView(box);
        AlertDialog.Builder bd = new AlertDialog.Builder(this).setTitle(firstTime ? "Welcome — set up the display" : "Settings").setView(sv)
                .setPositiveButton("Save", null);
        if (!firstTime) bd.setNegativeButton("Cancel", null).setNeutralButton("Reload", (x, y) -> { clearHistoryNext = true; loadHome(); });
        bd.setCancelable(!firstTime);
        dialog = bd.create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = normalise(addr.getText().toString());
            if (a.isEmpty()) { Toast.makeText(this, "Type the address first, for example 192.168.1.50:3000", Toast.LENGTH_LONG).show(); return; }
            int m = DEFAULT_MINUTES;
            try { m = Math.max(1, Math.min(240, Integer.parseInt(mins.getText().toString().trim()))); } catch (NumberFormatException ignore) { /* keep 15 */ }
            sp.edit().putString("addr", a).putString("page", page.getText().toString().trim()).putInt("mins", m)
                    .putString("theme", Season.KEYS[theme.getSelectedItemPosition()]).putBoolean("anim", anim.isChecked()).putBoolean("ret", ret.isChecked()).apply();
            dialog.dismiss();
            applyTheme();
            clearHistoryNext = true;
            loadHome();
        }));
        dialog.show();
        goImmersive();
    }

    private EditText field(LinearLayout box, String label, String hint, String value, int type) {
        TextView l = new TextView(this); l.setText(label); l.setPadding(0, (int) (10 * getResources().getDisplayMetrics().density), 0, 0); box.addView(l);
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setInputType(type); e.setSingleLine(true); box.addView(e);
        return e;
    }

    // ------------------------------------------------------------------ coming back after being away

    private PendingIntent returnIntent() {
        Intent i = new Intent(this, ReturnReceiver.class).setAction(ReturnReceiver.ACTION_RETURN);
        return PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void scheduleReturn() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = System.currentTimeMillis() + Math.max(1, sp.getInt("mins", DEFAULT_MINUTES)) * 60_000L;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, returnIntent());
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, returnIntent());
    }

    private void cancelReturn() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(returnIntent());
    }
}
