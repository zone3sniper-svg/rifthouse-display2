package au.rifthouse.kiosk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Brings the display back to the front: after you have been away for a while, and when the tablet starts up. */
public class ReturnReceiver extends BroadcastReceiver {
    static final String ACTION_RETURN = "au.rifthouse.kiosk.RETURN";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences sp = ctx.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean("ret", true) || sp.getString("addr", "").isEmpty()) return;
        try {
            Intent go = new Intent(ctx, MainActivity.class);
            go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            ctx.startActivity(go);
        } catch (RuntimeException e) {
            // Android refused to start it from the background (the "Display over other apps" permission is not on). Nothing else to do.
        }
    }
}
