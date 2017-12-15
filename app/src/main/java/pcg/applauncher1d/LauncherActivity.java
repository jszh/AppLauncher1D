package pcg.applauncher1d;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

import static android.content.ContentValues.TAG;

enum direction {
    forward, backward, nil
}

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class LauncherActivity extends AppCompatActivity {

    private View mContentView;
    private ArrayList<PInfo> mPackages;

    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {

            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // set UI layout
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_launcher);
        mContentView = findViewById(R.id.dummy);

        // get a list of apps
        mPackages = getPackages();


    }

    class PInfo {
        private String appname = "";
        private String pname = "";
        private String versionName = "";
        private Drawable icon;
        private void prettyPrint() {
            if (!appname.equals(pname)) {
                Log.v(TAG, appname + "\t" + pname + "\t" + versionName);
            }
        }
    }

    /*
     * launches an app from the given package info
     * https://stackoverflow.com/questions/3872063/launch-an-application-from-another-application-on-android
     */
    private void launchActivityFromPackage(PInfo p) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(p.pname);
        if (launchIntent != null) { //null pointer check in case package name was not found
            startActivity(launchIntent);
        }
    }

    /*
     * get all packages installed on this device
     * https://stackoverflow.com/questions/2695746/how-to-get-a-list-of-installed-android-applications-and-pick-one-to-run
     */
    private ArrayList<PInfo> getPackages() {
        ArrayList<PInfo> apps = getInstalledApps(false); /* false = no system packages */
        for (int i = 0; i < apps.size(); i++) {
            apps.get(i).prettyPrint();
        }
        return apps;
    }

    private ArrayList<PInfo> getInstalledApps(boolean getSysPackages) {
        ArrayList<PInfo> res = new ArrayList<>();
        List<PackageInfo> packs = getPackageManager().getInstalledPackages(0);
        for(int i = 0; i < packs.size(); i++) {
            PackageInfo p = packs.get(i);
            if ((!getSysPackages) && ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0)) {  // is system app
                continue;
            }
            PInfo newInfo = new PInfo();
            newInfo.appname = p.applicationInfo.loadLabel(getPackageManager()).toString();
            newInfo.pname = p.packageName;
            newInfo.versionName = p.versionName;
            newInfo.icon = p.applicationInfo.loadIcon(getPackageManager());
            res.add(newInfo);
        }
        return res;
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

}
