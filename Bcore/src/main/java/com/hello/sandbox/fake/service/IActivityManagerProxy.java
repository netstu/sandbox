package com.hello.sandbox.fake.service;

import static android.content.pm.PackageManager.GET_META_DATA;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.Manifest;
import android.app.ActivityManager;
import android.app.IServiceConnection;
import android.content.ComponentName;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.util.Log;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityManagerOreo;
import black.android.app.BRComponentOptions;
import black.android.app.BRLoadedApkReceiverDispatcher;
import black.android.app.BRLoadedApkReceiverDispatcherInnerReceiver;
import black.android.app.BRLoadedApkServiceDispatcher;
import black.android.app.BRLoadedApkServiceDispatcherInnerConnection;
import black.android.content.BRContentProviderNative;
import black.android.content.pm.BRUserInfo;
import black.android.util.BRSingleton;
import com.hello.sandbox.SandBoxCore;
import com.hello.sandbox.app.BActivityThread;
import com.hello.sandbox.core.env.AppSystemEnv;
import com.hello.sandbox.entity.AppConfig;
import com.hello.sandbox.entity.am.RunningAppProcessInfo;
import com.hello.sandbox.entity.am.RunningServiceInfo;
import com.hello.sandbox.fake.delegate.AppInstrumentation;
import com.hello.sandbox.fake.delegate.ContentProviderDelegate;
import com.hello.sandbox.fake.delegate.InnerReceiverDelegate;
import com.hello.sandbox.fake.delegate.ServiceConnectionDelegate;
import com.hello.sandbox.fake.frameworks.BActivityManager;
import com.hello.sandbox.fake.frameworks.BPackageManager;
import com.hello.sandbox.fake.hook.ClassInvocationStub;
import com.hello.sandbox.fake.hook.MethodHook;
import com.hello.sandbox.fake.hook.ProxyMethod;
import com.hello.sandbox.fake.hook.ScanClass;
import com.hello.sandbox.fake.service.base.PkgMethodProxy;
import com.hello.sandbox.fake.service.context.providers.ContentProviderStub;
import com.hello.sandbox.proxy.ProxyManifest;
import com.hello.sandbox.proxy.record.ProxyBroadcastRecord;
import com.hello.sandbox.proxy.record.ProxyPendingRecord;
import com.hello.sandbox.utils.MethodParameterUtils;
import com.hello.sandbox.utils.Reflector;
import com.hello.sandbox.utils.compat.ActivityManagerCompat;
import com.hello.sandbox.utils.compat.BuildCompat;
import com.hello.sandbox.utils.compat.ParceledListSliceCompat;
import com.hello.sandbox.utils.compat.TaskDescriptionCompat;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;

/** Created by Milk on 3/30/21. * ∧＿∧ (`･ω･∥ 丶　つ０ しーＪ 此处无Bug */
@ScanClass(ActivityManagerCommonProxy.class)
public class IActivityManagerProxy extends ClassInvocationStub {
  public static final String TAG = "ActivityManagerStub";

  @Override
  protected Object getWho() {
    Object iActivityManager = null;
    if (BuildCompat.isOreo()) {
      iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
    } else if (BuildCompat.isL()) {
      iActivityManager = BRActivityManagerNative.get().gDefault();
    }
    return BRSingleton.get(iActivityManager).get();
  }

  @Override
  protected void inject(Object base, Object proxy) {
    Object iActivityManager = null;
    if (BuildCompat.isOreo()) {
      iActivityManager = BRActivityManagerOreo.get().IActivityManagerSingleton();
    } else if (BuildCompat.isL()) {
      iActivityManager = BRActivityManagerNative.get().gDefault();
    }
    BRSingleton.get(iActivityManager)._set_mInstance(proxy);
  }

  @Override
  public boolean isBadEnv() {
    return getProxyInvocation() != getWho();
  }

  @Override
  protected void onBindMethod() {
    super.onBindMethod();
    addMethodHook(new PkgMethodProxy("getAppStartMode"));
    addMethodHook(new PkgMethodProxy("setAppLockedVerifying"));
    addMethodHook(new PkgMethodProxy("reportJunkFromApp"));
  }

  @ProxyMethod("getContentProvider")
  public static class GetContentProvider extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Exception {
      int authIndex = getAuthIndex();
      Object auth = args[authIndex];
      Object content = null;

      if (auth instanceof String) {
        if (ProxyManifest.isProxy((String) auth)) {
          return method.invoke(who, args);
        }

        if (BuildCompat.isQ()) {
          args[1] = SandBoxCore.getHostPkg();
        }

        if (auth.equals("settings")
            || auth.equals("media")
            || auth.equals("telephony")
            || auth.equals("com.huawei.android.launcher.settings")
            || auth.equals("com.hihonor.android.launcher.settings")) {
          content = method.invoke(who, args);
          ContentProviderDelegate.update(content, (String) auth);
          return content;
        } else {
          Log.d(TAG, "hook getContentProvider: " + auth);

          ProviderInfo providerInfo =
              SandBoxCore.getBPackageManager()
                  .resolveContentProvider(
                      (String) auth, GET_META_DATA, BActivityThread.getUserId());
          if (providerInfo == null) {
            //                        Log.d(TAG, "hook system: " + auth);
            //                        Object invoke = method.invoke(who, args);
            //                        if (invoke != null) {
            //                            Object provider = Reflector.with(invoke)
            //                                    .field("provider")
            //                                    .get();
            //                            if (provider != null && !(provider instanceof Proxy)) {
            //                                Reflector.with(invoke)
            //                                        .field("provider")
            //                                        .set(new
            // SettingsProviderStub().wrapper((IInterface) provider, SandBoxCore.getHostPkg()));
            //                            }
            //                        }
            return null;
          }

          Log.d(TAG, "hook app: " + auth);
          IBinder providerBinder = null;
          if (BActivityThread.getAppPid() != -1) {
            AppConfig appConfig =
                SandBoxCore.getBActivityManager()
                    .initProcess(
                        providerInfo.packageName,
                        providerInfo.processName,
                        BActivityThread.getUserId());
            if (appConfig.bpid != BActivityThread.getAppPid()) {
              providerBinder =
                  SandBoxCore.getBActivityManager().acquireContentProviderClient(providerInfo);
            }
            args[authIndex] = ProxyManifest.getProxyAuthorities(appConfig.bpid);
            args[getUserIndex()] = SandBoxCore.getHostUserId();
          }
          if (providerBinder == null) return null;

          content = method.invoke(who, args);
          Reflector.with(content).field("info").set(providerInfo);
          Reflector.with(content)
              .field("provider")
              .set(
                  new ContentProviderStub()
                      .wrapper(
                          BRContentProviderNative.get().asInterface(providerBinder),
                          BActivityThread.getAppPackageName()));
        }

        return content;
      }
      return method.invoke(who, args);
    }

    private int getAuthIndex() {
      // 10.0
      if (BuildCompat.isQ()) {
        return 2;
      } else {
        return 1;
      }
    }

    private int getUserIndex() {
      return getAuthIndex() + 1;
    }
  }

  @ProxyMethod("startService")
  public static class StartService extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      Intent intent = (Intent) args[1];
      String resolvedType = (String) args[2];
      ResolveInfo resolveInfo =
          SandBoxCore.getBPackageManager()
              .resolveService(intent, 0, resolvedType, BActivityThread.getUserId());
      if (resolveInfo == null) {
        return method.invoke(who, args);
      }

      int requireForegroundIndex = getRequireForeground();
      boolean requireForeground = false;
      if (requireForegroundIndex != -1) {
        requireForeground = (boolean) args[requireForegroundIndex];
      }
      return SandBoxCore.getBActivityManager()
          .startService(intent, resolvedType, requireForeground, BActivityThread.getUserId());
    }

    public int getRequireForeground() {
      if (BuildCompat.isOreo()) {
        return 3;
      }
      return -1;
    }
  }

  @ProxyMethod("stopService")
  public static class StopService extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      Intent intent = (Intent) args[1];
      String resolvedType = (String) args[2];
      return SandBoxCore.getBActivityManager()
          .stopService(intent, resolvedType, BActivityThread.getUserId());
    }
  }

  @ProxyMethod("stopServiceToken")
  public static class StopServiceToken extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      ComponentName componentName = (ComponentName) args[0];
      IBinder token = (IBinder) args[1];
      SandBoxCore.getBActivityManager()
          .stopServiceToken(componentName, token, BActivityThread.getUserId());
      return true;
    }
  }

  @ProxyMethod("bindService")
  public static class BindService extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      Intent intent = (Intent) args[2];
      if (BuildCompat.isT() && intent != null) {
        // bindwebview 的 服务 不需要代理 , 类似还需要添加其他机型的适配
        ComponentName componentName = intent.getComponent();
        if (componentName != null) {
          String webviewClass = "org.chromium.content.app.SandboxedProcessService0";
          String webviewPackage = "com.huawei.webview";
          if (webviewClass.equals(componentName.getClassName())
              && webviewPackage.equals(componentName.getPackageName())) {
            return method.invoke(who, args);
          }
        }
      }
      if (intent != null) {
        ComponentName componentName = intent.getComponent();
        if (componentName != null) {
          String screenOrientationServiceClass =
              "com.hello.sandbox.ui.screen.ScreenOrientationService";
          String miheappPackage = "com.hello.miheapp";
          if (screenOrientationServiceClass.equals(componentName.getClassName())
              && miheappPackage.equals(componentName.getPackageName())) {
            return method.invoke(who, args);
          }
        }
      }
      String resolvedType = (String) args[3];
      IServiceConnection connection = (IServiceConnection) args[4];

      int userId = intent.getIntExtra("_B_|_UserId", -1);
      userId = userId == -1 ? BActivityThread.getUserId() : userId;
      ResolveInfo resolveInfo =
          SandBoxCore.getBPackageManager().resolveService(intent, 0, resolvedType, userId);
      if (resolveInfo != null || AppSystemEnv.isOpenPackage(intent.getComponent())) {
        Intent proxyIntent =
            SandBoxCore.getBActivityManager()
                .bindService(
                    intent,
                    connection == null ? null : connection.asBinder(),
                    resolvedType,
                    userId);
        if (connection != null) {
          if (intent.getComponent() == null && resolveInfo != null) {
            intent.setComponent(
                new ComponentName(
                    resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name));
          }
          IServiceConnection proxy = ServiceConnectionDelegate.createProxy(connection, intent);
          args[4] = proxy;

          WeakReference<?> weakReference =
              BRLoadedApkServiceDispatcherInnerConnection.get(connection).mDispatcher();
          if (weakReference != null) {
            BRLoadedApkServiceDispatcher.get(weakReference.get())._set_mConnection(proxy);
          }
        }
        if (proxyIntent != null) {
          args[2] = proxyIntent;
          return method.invoke(who, args);
        }
      }
      return 0;
    }

    @Override
    protected boolean isEnable() {
      return SandBoxCore.get().isBlackProcess() || SandBoxCore.get().isServerProcess();
    }
  }

  @ProxyMethod("bindServiceInstance")
  public static class BindServiceInstance extends BindIsolatedService {}

  // 10.0
  @ProxyMethod("bindIsolatedService")
  public static class BindIsolatedService extends BindService {
    @Override
    protected Object beforeHook(Object who, Method method, Object[] args) throws Throwable {
      // instanceName
      args[6] = null;
      return super.beforeHook(who, method, args);
    }
  }

  @ProxyMethod("unbindService")
  public static class UnbindService extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      IServiceConnection iServiceConnection = (IServiceConnection) args[0];
      if (iServiceConnection == null) {
        return method.invoke(who, args);
      }
      SandBoxCore.getBActivityManager()
          .unbindService(iServiceConnection.asBinder(), BActivityThread.getUserId());
      ServiceConnectionDelegate delegate =
          ServiceConnectionDelegate.getDelegate(iServiceConnection.asBinder());
      if (delegate != null) {
        args[0] = delegate;
      }
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("getRunningAppProcesses")
  public static class GetRunningAppProcesses extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      RunningAppProcessInfo runningAppProcesses =
          BActivityManager.get()
              .getRunningAppProcesses(
                  BActivityThread.getAppPackageName(), BActivityThread.getUserId());
      if (runningAppProcesses == null) {
        return new ArrayList<>();
      }
      return runningAppProcesses.mAppProcessInfoList;
    }
  }

  @ProxyMethod("getServices")
  public static class GetServices extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      RunningServiceInfo runningServices =
          BActivityManager.get()
              .getRunningServices(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
      if (runningServices == null) {
        return new ArrayList<>();
      }
      return runningServices.mRunningServiceInfoList;
    }
  }

  @ProxyMethod("getIntentSender")
  public static class GetIntentSender extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      int type = (int) args[0];
      Intent[] intents = (Intent[]) args[getIntentsIndex(args)];
      MethodParameterUtils.replaceFirstAppPkg(args);

      for (int i = 0; i < intents.length; i++) {
        Intent intent = intents[i];
        switch (type) {
          case ActivityManagerCompat.INTENT_SENDER_ACTIVITY:
            Intent shadow = new Intent();
            shadow.setComponent(
                new ComponentName(
                    SandBoxCore.getHostPkg(),
                    ProxyManifest.getProxyPendingActivity(BActivityThread.getAppPid())));
            ProxyPendingRecord.saveStub(shadow, intent, BActivityThread.getUserId());
            intents[i] = shadow;
            break;
        }
      }
      IInterface invoke = (IInterface) method.invoke(who, args);
      if (invoke != null) {
        String[] packagesForUid =
            BPackageManager.get().getPackagesForUid(BActivityThread.getCallingBUid());
        if (packagesForUid.length < 1) {
          packagesForUid = new String[] {SandBoxCore.getHostPkg()};
        }
        SandBoxCore.getBActivityManager()
            .getIntentSender(
                invoke.asBinder(), packagesForUid[0], BActivityThread.getCallingBUid());
      }
      return invoke;
    }

    private int getIntentsIndex(Object[] args) {
      for (int i = 0; i < args.length; i++) {
        if (args[i] instanceof Intent[]) {
          return i;
        }
      }
      if (BuildCompat.isR()) {
        return 6;
      } else {
        return 5;
      }
    }
  }

  @ProxyMethod("getPackageForIntentSender")
  public static class getPackageForIntentSender extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      IInterface invoke = (IInterface) args[0];
      return SandBoxCore.getBActivityManager().getPackageForIntentSender(invoke.asBinder());
    }
  }

  @ProxyMethod("getUidForIntentSender")
  public static class getUidForIntentSender extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      IInterface invoke = (IInterface) args[0];
      return SandBoxCore.getBActivityManager().getUidForIntentSender(invoke.asBinder());
    }
  }

  @ProxyMethod("getIntentSenderWithSourceToken")
  public static class GetIntentSenderWithSourceToken extends GetIntentSender {}

  @ProxyMethod("getIntentSenderWithFeature")
  public static class GetIntentSenderWithFeature extends GetIntentSender {}

  @ProxyMethod("broadcastIntentWithFeature")
  public static class BroadcastIntentWithFeature extends BroadcastIntent {}

  @ProxyMethod("broadcastIntent")
  public static class BroadcastIntent extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      int intentIndex = getIntentIndex(args);
      Intent intent = (Intent) args[intentIndex];
      String resolvedType = (String) args[intentIndex + 1];
      Intent proxyIntent =
          SandBoxCore.getBActivityManager()
              .sendBroadcast(intent, resolvedType, BActivityThread.getUserId());
      if (proxyIntent != null) {
        proxyIntent.setExtrasClassLoader(AppInstrumentation.get().getDelegateAppClassLoader());
        ProxyBroadcastRecord.saveStub(proxyIntent, intent, BActivityThread.getUserId());
        args[intentIndex] = proxyIntent;
      }
      // ignore permission
      for (int i = 0; i < args.length; i++) {
        Object o = args[i];
        if (o instanceof String[]) {
          args[i] = null;
        }
      }
      return method.invoke(who, args);
    }

    int getIntentIndex(Object[] args) {
      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];
        if (arg instanceof Intent) {
          return i;
        }
      }
      return 1;
    }
  }

  @ProxyMethod("unregisterReceiver")
  public static class unregisterReceiver extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("finishReceiver")
  public static class finishReceiver extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("publishService")
  public static class PublishService extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("peekService")
  public static class PeekService extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      MethodParameterUtils.replaceLastAppPkg(args);
      Intent intent = (Intent) args[0];
      String resolvedType = (String) args[1];
      IBinder peek =
          SandBoxCore.getBActivityManager()
              .peekService(intent, resolvedType, BActivityThread.getUserId());
      return peek;
    }
  }

  // todo
  @ProxyMethod("sendIntentSender")
  public static class SendIntentSender extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      if (BuildCompat.isU() && args != null && args.length >= 9 &&(args[8] instanceof Bundle)){
        Bundle bundle = (Bundle) args[8];
        bundle.putBoolean(BRComponentOptions.get().KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED(), true);
        bundle.putBoolean(BRComponentOptions.get().KEY_PENDING_INTENT_BACKGROUND_ACTIVITY_ALLOWED_BY_PERMISSION(), true);
      }
      return method.invoke(who,args);
    }
  }

  // android 10
  @ProxyMethod("registerReceiverWithFeature")
  public static class RegisterReceiverWithFeature extends RegisterReceiver {}

  @ProxyMethod("registerReceiver")
  public static class RegisterReceiver extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      MethodParameterUtils.replaceFirstAppPkg(args);
      int receiverIndex = getReceiverIndex();
      if (args[receiverIndex] != null) {
        IIntentReceiver intentReceiver = (IIntentReceiver) args[receiverIndex];
        IIntentReceiver proxy = InnerReceiverDelegate.createProxy(intentReceiver);

        WeakReference<?> weakReference =
            BRLoadedApkReceiverDispatcherInnerReceiver.get(intentReceiver).mDispatcher();
        if (weakReference != null) {
          BRLoadedApkReceiverDispatcher.get(weakReference.get())._set_mIIntentReceiver(proxy);
        }

        args[receiverIndex] = proxy;
      }
      // ignore permission
      if (args[getPermissionIndex()] != null) {
        args[getPermissionIndex()] = null;
      }
      return method.invoke(who, args);
    }

    public int getReceiverIndex() {
      if (BuildCompat.isS()) {
        return 4;
      } else if (BuildCompat.isR()) {
        return 3;
      }
      return 2;
    }

    public int getPermissionIndex() {
      if (BuildCompat.isS()) {
        return 6;
      } else if (BuildCompat.isR()) {
        return 5;
      }
      return 4;
    }
  }

  @ProxyMethod("grantUriPermission")
  public static class GrantUriPermission extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      MethodParameterUtils.replaceLastUid(args);
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("setServiceForeground")
  public static class setServiceForeground extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      //            if (args[0] instanceof ComponentName) {
      //                args[0] = new ComponentName(SandBoxCore.getHostPkg(),
      // ProxyManifest.getProxyService(BActivityThread.getAppPid()));
      //            }
      //            return method.invoke(who, args);
      return 0;
    }
  }

  @ProxyMethod("getHistoricalProcessExitReasons")
  public static class getHistoricalProcessExitReasons extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return ParceledListSliceCompat.create(new ArrayList<>());
    }
  }

  @ProxyMethod("getCurrentUser")
  public static class getCurrentUser extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      Object blackBox =
          BRUserInfo.get()
              ._new(BActivityThread.getUserId(), "sandbox", BRUserInfo.get().FLAG_PRIMARY());
      return blackBox;
    }
  }

  @ProxyMethod("checkPermission")
  public static class checkPermission extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      MethodParameterUtils.replaceLastUid(args);
      String permission = (String) args[0];
      if (permission.equals(Manifest.permission.ACCOUNT_MANAGER)
          || permission.equals(Manifest.permission.SEND_SMS)) {
        return PackageManager.PERMISSION_GRANTED;
      }
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("checkUriPermission")
  public static class checkUriPermission extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return PERMISSION_GRANTED;
    }
  }

  // for < Android 10
  @ProxyMethod("setTaskDescription")
  public static class SetTaskDescription extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      ActivityManager.TaskDescription td = (ActivityManager.TaskDescription) args[1];
      args[1] = TaskDescriptionCompat.fix(td);
      return method.invoke(who, args);
    }
  }

  @ProxyMethod("setRequestedOrientation")
  public static class setRequestedOrientation extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(who, args);
      } catch (Throwable e) {
        e.printStackTrace();
      }
      return 0;
    }
  }

  @ProxyMethod("registerUidObserver")
  public static class registerUidObserver extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return 0;
    }
  }

  @ProxyMethod("unregisterUidObserver")
  public static class unregisterUidObserver extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return 0;
    }
  }

  @ProxyMethod("updateConfiguration")
  public static class updateConfiguration extends MethodHook {

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
      return 0;
    }
  }
}
