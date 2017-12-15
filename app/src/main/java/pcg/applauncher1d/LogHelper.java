package pcg.applauncher1d;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Handles logging
 * Created by Jason on 7/31/15.
 */
public class LogHelper {

    private LogHelper() { }

    private static String userName;

    private static char option;

    private static void writeToFile(String fileName, String body) {
        FileOutputStream fos;

        try {
            final File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Handwriting/" );

            if (!dir.exists()) {
                dir.mkdirs();
            }

            final File myFile = new File(dir, fileName + ".txt");

            if (!myFile.exists()) {
                myFile.createNewFile();
            }

            fos = new FileOutputStream(myFile, true);

            fos.write(body.getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Gesture-Log", e.getLocalizedMessage());
        }
    }

    public static void writeToLog(String str) {
        if (userName == null || userName.equals("test") || userName.equals("")) return;
        if (!str.endsWith("\n")) str += "\n";
        writeToFile(userName + "_" + option, str);
    }

    public static void setOption(char option) {
        LogHelper.option = option;
    }

    public static void setUserName(String userName) {
        LogHelper.userName = userName;
    }

}
