package io.github.andrea_lyz.archivetoopen;

import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * Two ColorOS 17 application-info tweaks, both keyed off the same region flag
 * ({@code CustomizeFeatureUtils.isExpVersion()}):
 *
 * <ol>
 *   <li><b>Overseas builds</b> - turn the first button back from "Archive" into "Open".</li>
 *   <li><b>China builds</b> - default the "自动释放应用空间" switch to off, while leaving a manual
 *   toggle alone. {@link HibernationHooks} covers the page being opened; {@link BulkDefaultOff}
 *   sweeps every other installed app so the default really is off everywhere.</li>
 * </ol>
 *
 * <h2>Overseas: Archive -&gt; Open</h2>
 *
 * <p>ColorOS 17 ships one Settings APK for both the China and the overseas region; the region is
 * resolved at runtime through {@code CustomizeFeatureUtils.isExpVersion()}, which is
 * {@code !isAppFeatureSupport("com.android.settings.cn_version")}. On an overseas build that makes
 * {@code AppInfoFeature.isSupportArchingFeature()} return {@code true}, and the application-info
 * page then swaps its first button:</p>
 *
 * <ul>
 *   <li>{@code AppInfoFeature.performOpenButton()} hides the "Open" button
 *   ({@code left_button}) whenever archiving is supported.</li>
 *   <li>{@code AppInfoFeature.updateAppArchiveAndRestoreButtonStatus()} returns early when it is
 *   not, and otherwise shows {@code recover_button} labelled {@code oplus_archive} ("归档").</li>
 * </ul>
 *
 * <p>The two buttons are siblings in a horizontal {@code LinearLayout}
 * ({@code res/layout/two_buttons_panel_app.xml}), with {@code recover_button} {@code GONE} by
 * default, so forcing the feature check to {@code false} restores exactly the China-region layout:
 * Open / Force stop / Uninstall.</p>
 *
 * <p>Hooking this single method is deliberate. Its two callers above are the only ones that draw
 * the app-info page; the shared helpers behind it are far too broad. {@code isExpVersion()} has
 * 150+ call sites (search ranking, NFC, ringtones, safety centre, the region wizard), and
 * {@code SysFeatureUtils.isSupportArchingFeature()} is used by the application list and the storage
 * page as well. Both would drag unrelated UI along with them.</p>
 *
 * <p>No user interface is provided on purpose: the module only needs to be enabled in LSPosed with
 * {@code com.android.settings} in its scope.</p>
 */
public final class ArchiveToOpenModule extends XposedModule {
    private static final String TAG = "ArchiveToOpen";

    private static final String SETTINGS_PACKAGE = "com.android.settings";

    /** ColorOS' adapter for the classic (non-SPA) application-info page. */
    private static final String APP_INFO_FEATURE =
            "com.oplus.settings.feature.appmanager.AppInfoFeature";

    /** {@code private boolean isSupportArchingFeature()} - note the OEM's spelling. */
    private static final String METHOD_IS_SUPPORT_ARCHING = "isSupportArchingFeature";

    private volatile boolean installed;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "module_loaded process=" + param.getProcessName()
                + " systemServer=" + param.isSystemServer()
                + " api=" + getApiVersion()
                + " framework=" + getFrameworkName() + "/" + getFrameworkVersion());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        if (!SETTINGS_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (installed) {
            return;
        }
        ClassLoader classLoader = param.getClassLoader();
        // Each hook installs independently: one failing must not take the other down with it.
        boolean ok = true;
        try {
            installArchiveToOpen(classLoader);
        } catch (Throwable t) {
            ok = false;
            log(Log.ERROR, TAG, "archive_to_open_install_failed", t);
        }
        try {
            HibernationHooks.install(this, classLoader);
        } catch (Throwable t) {
            ok = false;
            log(Log.ERROR, TAG, "hibernation_install_failed", t);
        }
        try {
            BulkDefaultOff.install(this, classLoader);
        } catch (Throwable t) {
            ok = false;
            log(Log.ERROR, TAG, "bulk_default_off_install_failed", t);
        }
        installed = ok;
    }

    private void installArchiveToOpen(ClassLoader classLoader) throws Throwable {
        Class<?> feature = Class.forName(APP_INFO_FEATURE, true, classLoader);
        Method target = feature.getDeclaredMethod(METHOD_IS_SUPPORT_ARCHING);
        target.setAccessible(true);

        // Returning false without calling chain.proceed() replaces the result outright, so the
        // whole archive/restore path is skipped as if the build were a China-region one.
        hook(target)
                .setId("app_info_archive_to_open")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> Boolean.FALSE);

        log(Log.INFO, TAG, "hooked " + APP_INFO_FEATURE + "#" + METHOD_IS_SUPPORT_ARCHING);
    }
}
