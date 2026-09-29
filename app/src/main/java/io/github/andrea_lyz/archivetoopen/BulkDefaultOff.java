package io.github.andrea_lyz.archivetoopen;

import android.app.AppOpsManager;
import android.app.Application;
import android.app.Instrumentation;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Flips the China-region default for <em>every</em> installed user app, instead of only the ones
 * whose detail page happens to be opened.
 *
 * <p>{@link HibernationHooks} hangs off {@code updateState}, which only runs when a detail page is
 * created - an app the user never opens keeps the stock default (switch ON). This class removes
 * that blind spot by sweeping the installed packages once per Settings process.</p>
 *
 * <p>Getting a {@code Context} is the whole reason this needs a second hook: libxposed only hands
 * out {@code getPackageName()} and {@code getClassLoader()}, and both
 * {@code getInstalledApplications()} and {@code getSystemService(AppOpsManager.class)} need a
 * {@code Context}. {@code Instrumentation.callApplicationOnCreate(Application)} is the earliest
 * point where one is available, and unlike {@code Application.onCreate} it cannot be overridden by
 * the OEM's Application subclass, so the hook is guaranteed to fire.</p>
 *
 * <p>The sweep runs on its own thread: a few hundred packages means a few hundred binder calls, and
 * that must not sit on the UI thread. Only {@code MODE_DEFAULT} packages are touched, so a package
 * the user already decided about is never overridden, and re-runs are read-only and cheap.</p>
 *
 * <p>Overseas builds are skipped entirely, and nothing here runs outside {@code com.android.settings}
 * (the module's declared scope).</p>
 */
final class BulkDefaultOff {

    private static final String TAG = "ArchiveToOpen";

    private static final String CUSTOMIZE_UTILS_CLASS =
            "com.oplus.settings.utils.CustomizeFeatureUtils";

    /** App-op behind the switch; {@code MODE_IGNORED} on it means "exempt from hibernation". */
    private static final String OP_AUTO_REVOKE = "android:auto_revoke_permissions_if_unused";

    private static final int MODE_DEFAULT = 3;
    private static final int MODE_IGNORED = 1;

    private BulkDefaultOff() {
    }

    static void install(XposedModule module, ClassLoader classLoader) throws Throwable {
        Method isExpVersion =
                Class.forName(CUSTOMIZE_UTILS_CLASS, false, classLoader).getMethod("isExpVersion");

        Method callApplicationOnCreate =
                Instrumentation.class.getMethod("callApplicationOnCreate", Application.class);

        module.hook(callApplicationOnCreate)
                .setId("cn_auto_release_space_bulk_default_off")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        if (Boolean.FALSE.equals(isExpVersion.invoke(null))) {
                            Object application = chain.getArg(0);
                            if (application instanceof Application) {
                                sweep((Application) application, module);
                            }
                        }
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "bulk_default_off_schedule_failed", t);
                    }
                    return result;
                });

        module.log(Log.INFO, TAG,
                "hooked " + Instrumentation.class.getName() + "#callApplicationOnCreate");
    }

    private static void sweep(Application application, XposedModule module) {
        Thread worker = new Thread(() -> {
            try {
                PackageManager packageManager = application.getPackageManager();
                AppOpsManager appOps = application.getSystemService(AppOpsManager.class);
                if (packageManager == null || appOps == null) {
                    module.log(Log.WARN, TAG, "bulk_default_off_no_services");
                    return;
                }

                // setUidMode / unsafeCheckOpNoThrow are @SystemApi and absent from the public SDK,
                // so they are resolved reflectively just like the rest of this module.
                Method unsafeCheckOpNoThrow = AppOpsManager.class.getMethod(
                        "unsafeCheckOpNoThrow", String.class, int.class, String.class);
                Method setUidMode = AppOpsManager.class.getMethod(
                        "setUidMode", String.class, int.class, int.class);

                List<ApplicationInfo> installed = packageManager.getInstalledApplications(
                        PackageManager.MATCH_DISABLED_COMPONENTS);

                int changed = 0;
                int skipped = 0;
                for (ApplicationInfo info : installed) {
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        continue; // system apps are not hibernation targets anyway
                    }
                    try {
                        Object mode = unsafeCheckOpNoThrow.invoke(
                                appOps, OP_AUTO_REVOKE, info.uid, info.packageName);
                        if (!(mode instanceof Integer) || (Integer) mode != MODE_DEFAULT) {
                            skipped++; // the user already made a choice here
                            continue;
                        }
                        setUidMode.invoke(appOps, OP_AUTO_REVOKE, info.uid, MODE_IGNORED);
                        changed++;
                    } catch (Throwable ignored) {
                        // one package failing must not abort the sweep
                    }
                }
                module.log(Log.INFO, TAG, "bulk_default_off_done changed=" + changed
                        + " kept=" + skipped + " total=" + installed.size());
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "bulk_default_off_scan_failed", t);
            }
        }, "ArchiveToOpen-default-off");
        worker.setDaemon(true);
        worker.start();
    }
}
