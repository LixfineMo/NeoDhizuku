package android.app.admin;

import android.content.ComponentName;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.RequiresApi;

public interface IDevicePolicyManager extends IInterface {
    void setActiveAdmin(ComponentName policyReceiver, boolean refreshing, int userHandle);

    void removeActiveAdmin(ComponentName policyReceiver, int userHandle);

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean setDeviceOwner(ComponentName who, int userId, boolean setProfileOwnerOnCurrentUserIfNecessary);

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    boolean setDeviceOwner(ComponentName who, String ownerName, int userId, boolean setProfileOwnerOnCurrentUserIfNecessary);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean setDeviceOwner(ComponentName who, String ownerName, int userId);

    boolean setDeviceOwner(String packageName, String ownerName);

    /**
     * C-01：原先 ProfileOwner 路径只能通过反射 DevicePolicyManager 的隐藏方法调用，
     * 这里补齐 stub 声明，使得可以像 DeviceOwner 一样走 IDevicePolicyManager 直调，
     * 不再需要篡改系统单例 DevicePolicyManager 的 mService 字段。
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    boolean setProfileOwner(ComponentName who, int userId);

    @RequiresApi(Build.VERSION_CODES.N)
    boolean setProfileOwner(ComponentName who, String ownerName, int userId);

    boolean setProfileOwner(ComponentName who, String ownerName);

    abstract class Stub extends Binder implements IDevicePolicyManager {
        public static IDevicePolicyManager asInterface(IBinder obj) {
            throw new UnsupportedOperationException();
        }
    }
}
