package io.github.andrea_lyz.archivetoopen;

import android.app.AppOpsManager;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Defaults the China-region "自动释放应用空间" switch to OFF.
 *
 * <p>Both regions share one controller, {@code OplusHibernationSwitchPreferenceController}
 * (an {@code HibernationSwitchPreferenceController} subclass). Its {@code updateState} picks the
 * title from the region - China gets {@code unused_apps_switch_v4} ("自动释放应用空间"), overseas
 * gets {@code unused_apps_switch_v2} ("管理闲置应用") - and the checked state comes from the base
 * class:</p>
 *
 * <pre>
 *   checked = isAppEligibleForHibernation() &amp;&amp; !isPackageHibernationExemptByUser()
 * </pre>
 *
 * <p>China-region builds therefore show the switch as ON for a freshly installed app. Flipping it
 * for real means writing the app-op and clearing the hibernation flags, which is exactly what the
 * controller's own {@code onPreferenceChange(preference, false)} does. That method is public on the
 * base class and is not overridden, so it is invoked reflectively instead of poking at the hidden
 * {@code AppOpsManager}/{@code AppHibernationManager} APIs directly.</p>
 *
 * <p><b>Only untouched apps are flipped.</b> The app-op mode tells the three cases apart:</p>
 * <ul>
 *   <li>{@code MODE_DEFAULT} - nobody ever set this, so it is still "the default" -&gt; turn off;</li>
 *   <li>{@code MODE_ALLOWED} - the user switched it on -&gt; leave alone;</li>
 *   <li>{@code MODE_IGNORED} - the user switched it off -&gt; leave alone.</li>
 * </ul>
 * <p>That makes the default OFF while a manual toggle still sticks.</p>
 *
 * <p>Overseas builds are never touched: the very first check bails out when
 * {@code CustomizeFeatureUtils.isExpVersion()} is true.</p>
 */
final class HibernationHooks {

    private static final String TAG = "ArchiveToOpen";

    private static final String CONTROLLER_CLASS =
            "com.oplus.settings.feature.appmanager.details.controller"
                    + ".OplusHibernationSwitchPreferenceController";
    private static final String CUSTOMIZE_UTILS_CLASS =
            "com.oplus.settings.utils.CustomizeFeatureUtils";

    private static final String PREFERENCE_CLASS = "androidx.preference.Preference";
    private static final String TWO_STATE_PREFERENCE_CLASS = "androidx.preference.TwoStatePreference";

    /** App-op behind the switch; {@code MODE_IGNORED} on it means "exempt from hibernation". */
    private static final String OP_AUTO_REVOKE = "android:auto_revoke_permissions_if_unused";

    private static final int MODE_DEFAULT = 3;

    private HibernationHooks() {
    }

    static void install(XposedModule module, ClassLoader classLoader) throws Throwable {
        Class<?> preferenceClass = Class.forName(PREFERENCE_CLASS, false, classLoader);
        Class<?> controllerClass = Class.forName(CONTROLLER_CLASS, false, classLoader);

        Method updateState = controllerClass.getDeclaredMethod("updateState", preferenceClass);
        updateState.setAccessible(true);

        Method isExpVersion =
                Class.forName(CUSTOMIZE_UTILS_CLASS, false, classLoader).getMethod("isExpVersion");

        module.hook(updateState)
                .setId("cn_auto_release_space_default_off")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    // Let the controller paint itself first, then correct the default.
                    Object result = chain.proceed();
                    try {
                        applyChinaDefault(chain, classLoader, isExpVersion);
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, "auto_release_space_default_off_failed", t);
                    }
                    return result;
                });

        module.log(Log.INFO, TAG, "hooked " + CONTROLLER_CLASS + "#updateState");
    }

    private static void applyChinaDefault(XposedInterface.Chain chain,
                                          ClassLoader classLoader,
                                          Method isExpVersion) throws Throwable {
        if (!Boolean.FALSE.equals(isExpVersion.invoke(null))) {
            return; // overseas build: leave "管理闲置应用" untouched
        }

        Object controller = chain.getThisObject();
        Object preference = chain.getArg(0);
        if (controller == null || preference == null) {
            return;
        }
        Class<?> twoStateClass = Class.forName(TWO_STATE_PREFERENCE_CLASS, false, classLoader);
        if (!twoStateClass.isInstance(preference)) {
            return;
        }

        Object appOps = getField(controller, "mAppOpsManager");
        Object uidBox = getField(controller, "mPackageUid");
        Object packageBox = getField(controller, "mPackageName");
        if (!(appOps instanceof AppOpsManager) || !(uidBox instanceof Integer)
                || !(packageBox instanceof String)) {
            return;
        }
        int uid = (Integer) uidBox;
        String packageName = (String) packageBox;

        Method unsafeCheckOpNoThrow = AppOpsManager.class.getMethod(
                "unsafeCheckOpNoThrow", String.class, int.class, String.class);
        Object modeBox = unsafeCheckOpNoThrow.invoke(appOps, OP_AUTO_REVOKE, uid, packageName);
        if (!(modeBox instanceof Integer) || (Integer) modeBox != MODE_DEFAULT) {
            return; // the user already made a choice here
        }

        // Reuse the controller's own listener so the app-op write, the hibernation flags and the
        // archive-button refresh all happen exactly as they would after a real tap.
        Method onPreferenceChange = controller.getClass().getMethod(
                "onPreferenceChange",
                Class.forName(PREFERENCE_CLASS, false, classLoader),
                Object.class);
        onPreferenceChange.invoke(controller, preference, Boolean.FALSE);

        // A programmatic call does not repaint the widget, so sync the visual state too.
        twoStateClass.getMethod("setChecked", boolean.class).invoke(preference, false);
    }

    private static Object getField(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }
}
