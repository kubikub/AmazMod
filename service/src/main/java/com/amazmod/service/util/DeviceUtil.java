package com.amazmod.service.util;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInstaller;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.amazmod.service.Constants;
import com.amazmod.service.MainService;
import com.amazmod.service.PackageReceiver;
import com.amazmod.service.ui.ConfirmationWearActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;

public class DeviceUtil {

    public static boolean isDeviceLocked(Context context) {
        boolean isLocked;

        // First we check the locked state
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            boolean inKeyguardRestrictedInputMode = keyguardManager.inKeyguardRestrictedInputMode();

            if (inKeyguardRestrictedInputMode) {
                isLocked = true;

            } else {
                // If password is not set in the settings, the inKeyguardRestrictedInputMode() returns false,
                // so we need to check if screen on for this case
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    isLocked = !powerManager.isInteractive();
                } else {
                    //noinspection deprecation
                    isLocked = !powerManager.isScreenOn();
                }
            }
        } catch (NullPointerException e) {
            Log.e(Constants.TAG, "iDeviceLocked exception: " + e.toString());
            isLocked = false;
        }

        return isLocked;
    }

    public static boolean isDNDActive(Context context, ContentResolver cr) {

        boolean dndEnabled;
        try {

            int zenModeValue = Settings.Global.getInt(cr, "zen_mode");

            switch (zenModeValue) {
                case 0:
                    Log.d(Constants.TAG, "DnD lowSDK: OFF");
                    dndEnabled = false;
                    break;
                case 1:
                    Log.d(Constants.TAG, "DnD lowSDK: ON - Priority Only");
                    dndEnabled = true;
                    break;
                case 2:
                    Log.d(Constants.TAG, "DnD lowSDK: ON - Total Silence");
                    dndEnabled = true;
                    break;
                case 3:
                    Log.d(Constants.TAG, "DnD lowSDK: ON - Alarms Only");
                    dndEnabled = true;
                    break;
                default:
                    Log.d(Constants.TAG, "DnD lowSDK Unexpected Value: " + zenModeValue);
                    dndEnabled = false;
            }
        } catch (Settings.SettingNotFoundException e) {
            Log.e(Constants.TAG, "DnD lowSDK exception: " + e.toString());
            dndEnabled = false;
        }

        return dndEnabled;
    }

    public static void installPackage(Context context, String packageName, String filePath) {

        Log.d(Constants.TAG, "DeviceUtil installPackage packageName: " + packageName);
        PackageInstaller packageInstaller = context.getPackageManager().getPackageInstaller();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(packageName);
        PackageInstaller.Session session = null;

        int sessionId = 0;
        try {
            sessionId = packageInstaller.createSession(params);
            session = packageInstaller.openSession(sessionId);

            /*
            FileInputStream apkStream = new FileInputStream(filePath);
            OutputStream out = session.openWrite("amazmod_install", 0, -1);

            //Duplicate file to sdcard to test if copy is working fine
            FileOutputStream fos = new FileOutputStream("/sdcard/test.apk");
            long length = 0;
            try {
                byte[] buffer = new byte[1024];
                int c;
                while ((c = apkStream.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                    fos.write(buffer, 0, c);
                    length++;
                }

            }catch (Exception e) {
                Log.e(Constants.TAG, "PackageReceiver installPackage exception: " + e.toString());
            } finally {
                Log.d(Constants.TAG, "PackageReceiver installPackage length: " + length);
                session.fsync(out);
                apkStream.close();
                out.close();
                fos.close();
            }
            */

            long sizeBytes = 0;
            final File file = new File(filePath);
            if (file.isFile())
                sizeBytes = file.length();

            InputStream in = null;
            OutputStream out = null;
            in = new FileInputStream(filePath);
            out = session.openWrite("amazmod_install", 0, sizeBytes);

            int total = 0;
            byte[] buffer = new byte[65536];
            int c;
            while ((c = in.read(buffer)) != -1) {
                total += c;
                out.write(buffer, 0, c);
            }
            session.fsync(out);
            in.close();
            out.close();
            Log.d(Constants.TAG, "DeviceUtil installPackage sizeBytes: " + sizeBytes + " // total: " + total);

            Intent intent = new Intent(context, ConfirmationWearActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Constants.TEXT, "Auto Install");
            intent.putExtra(Constants.TIME, "5");
            intent.putExtra(Constants.MODE, Constants.NORMAL);

            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
            IntentSender mIntentSender = pendingIntent.getIntentSender();

            for (String s : session.getNames()) {
                Log.i(Constants.TAG, "DeviceUtil installPackage session.getNames: " + s);
            }
            session.commit(mIntentSender);

            //Intent intent = new Intent(Intent.ACTION_MAIN);
            //session.commit(PendingIntent.getBroadcast(context, sessionId,
            //        intent, PendingIntent.FLAG_UPDATE_CURRENT).getIntentSender());

        } catch (Exception e) {
            Log.e(Constants.TAG, "DeviceUtil installPackage exception: " + e.toString());
        } finally {
            if (session != null) {
                Log.i(Constants.TAG, "DeviceUtil installPackage session.close session: " + sessionId);
                session.close();
                Log.i(Constants.TAG, "DeviceUtil installPackage finished");
            }
        }
    }

    public static void installApk(Context context, String apkPath, String mode) {

        Log.d(Constants.TAG, "DeviceUtil installApk apkPath: " + apkPath + " | mode: " + mode);

        Intent intent = new Intent(context, ConfirmationWearActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra(Constants.TEXT, "Installing APK");
        intent.putExtra(Constants.TIME, "3");
        intent.putExtra(Constants.MODE, mode);
        intent.putExtra(Constants.PKG, apkPath);
        context.startActivity(intent);

    }

    public static void installApkAdb(Context context, File apk, boolean isReboot) {

        PackageReceiver.setIsAmazmodInstall(true);
        final String installScript = copyScriptFile(context, "install_apk.sh").getAbsolutePath();
        final String busyboxPath = installBusybox(context);
        String installCommand;
        String apkFile = apk.getAbsolutePath();
        //Log.d(Constants.TAG, "DeviceUtil installApkAdb installScript: " + installScript);
        //Log.d(Constants.TAG, "DeviceUtil installApkAdb apkFile: " + apkFile);

        //Delete APK after installation if the "reboot" toggle is enabled (workaround to avoid adding a new field to bundle)
        if (isReboot)
            installCommand = String.format("log -pw -tAmazMod $(sh %s %s %s %s 2>&1)", installScript, apkFile, "DEL", busyboxPath);
        else
            installCommand = String.format("log -pw -tAmazMod $(sh %s %s %s %s 2>&1)", installScript, apkFile, "OK", busyboxPath);

        Log.d(Constants.TAG, "DeviceUtil installApkAdb installCommand: " + installCommand);

        try {
            Runtime.getRuntime().exec(new String[]{"sh", "-c", installCommand},
                    null, Environment.getExternalStorageDirectory());
        } catch (IOException e) {
            Log.e(Constants.TAG, "DeviceUtil installApkAdb IOException" + e.getMessage(), e);
        }

    }

    public static File copyScriptFile(Context context, String fileName) {

        File file = new File(context.getFilesDir(), fileName);
        InputStream inFile = context.getResources().openRawResource(context.getResources()
                .getIdentifier(fileName.replace(".sh", ""), "raw", context.getPackageName()));

        try {

            if (file.exists() && file.isFile() && (file.length() == inFile.available())) {
                Log.d(Constants.TAG, "DeviceUtil copyScriptFile file already exists, size: " + inFile.available());
                return file;
            }

            OutputStream output = new FileOutputStream(file);
            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inFile.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            output.close();
            inFile.close();
        } catch (Exception e) {
            Log.e(Constants.TAG, "DeviceUtil copyScriptFile exception: " + e.toString());
        }
        return file;
    }

    public static String installBusybox(Context context) {

        final String fileName = "busybox";
        String utilFolderPath = context.getFilesDir() + File.separator + "bin";
        final File utilFolder = new File(utilFolderPath);

        if (utilFolder.exists() || utilFolder.mkdir()) {

            final File file = new File(utilFolderPath, fileName);

            try {

                InputStream inFile = context.getResources().openRawResource(context.getResources()
                        .getIdentifier(fileName, "raw", context.getPackageName()));

                if (file.exists() && file.isFile() && (file.length() == inFile.available())) {
                    Log.d(Constants.TAG, "DeviceUtil installBusybox file already exists, size: " + inFile.available());
                    return utilFolderPath;
                }


                OutputStream output = new FileOutputStream(file);
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = inFile.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.flush();
                output.close();
                inFile.close();

                final String busybox = file.getAbsolutePath();

                final String installCommand = "chmod 755 " + busybox
                        + " && " + busybox + " --install -s " + utilFolderPath;

                Log.d(Constants.TAG, "DeviceUtil installBusybox installCommand: " + installCommand);

                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", installCommand}, null, utilFolder);

                int code = process.waitFor();
                if (code != 0)
                    Log.e(Constants.TAG, "DeviceUtil installBusybox error! " + code);

            } catch (InterruptedException e) {
                Log.e(Constants.TAG, "DeviceUtil installBusybox InterruptedException: " + e.toString());
                utilFolderPath = null;
            } catch (IOException e) {
                Log.e(Constants.TAG, "DeviceUtil installBusybox IOException: " + e.toString());
                utilFolderPath = null;
            }

        } else
            utilFolderPath = null;

        return utilFolderPath;
    }

    public static void killBackgroundTasks(Context context, Boolean suicide) {

        Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks");

        ActivityManager.RunningAppProcessInfo myProcess = null;

        ActivityManager am = (ActivityManager) context.getApplicationContext().getSystemService(ACTIVITY_SERVICE);

        if (am != null) {

            List<ApplicationInfo> packages = context.getPackageManager().getInstalledApplications(0);

            List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();

            for (ApplicationInfo packageInfo : packages) {
                if (!((packageInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 1)) {
                    if (packageInfo.packageName.contains("amazmod") && !suicide)
                        //Do not suicide;
                        Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks skipping " + packageInfo.processName);
                    else {
                        Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks killing package: " + packageInfo.packageName);
                        am.killBackgroundProcesses(packageInfo.packageName);
                    }
                }
            }

            for (ActivityManager.RunningAppProcessInfo info : processes) {

                if (info.processName.contains("amazmod")) {
                    myProcess = info;
                } else if (info.processName.contains("watch.launcher") && !suicide) {
                    //Do not kill launcher;
                    Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks skipping " + info.processName);
                } else {

                    Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks killing process: " + info.processName);

                    android.os.Process.killProcess(info.pid);
                    android.os.Process.sendSignal(info.pid, android.os.Process.SIGNAL_KILL);
                    if (info.processName.contains("process.media"))
                        am.killBackgroundProcesses("com.android.providers.media");
                    else
                        am.killBackgroundProcesses(info.processName);
                }
            }

            if ((myProcess != null) && suicide) {
                Log.d(Constants.TAG, "DeviceUtil killBackgroundTasks myProcess: " + myProcess.processName);
                am.killBackgroundProcesses(myProcess.processName);
                android.os.Process.sendSignal(myProcess.pid, android.os.Process.SIGNAL_KILL);
            }

        } else {
            Log.e(Constants.TAG, "DeviceUtil killBackgroundTasks failed - null ActivityManager!");
        }
    }

    public static boolean saveBatteryData(Context context, boolean updateSettings) {
        Intent batteryStatus = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (batteryStatus != null) {

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = level / (float) scale;

            Log.d(Constants.TAG, "DeviceUtil saveBatteryData batteryPct: " + batteryPct);

            return MainService.saveBatteryDb(batteryPct, updateSettings);

        } else {

            Log.e(Constants.TAG, "DeviceUtil saveBatteryData register receiver error!");
            return false;
        }

    }

}
