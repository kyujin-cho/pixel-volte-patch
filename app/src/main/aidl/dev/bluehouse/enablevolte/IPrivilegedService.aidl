// IPrivilegedService.aidl
package dev.bluehouse.enablevolte;

import android.content.ComponentName;
import android.os.Bundle;

// Shizuku UserService. Runs in a separate process as the shell (or root) uid.
interface IPrivilegedService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    /**
     * Starts the broker instrumentation from this (shell) process together with a
     * UiAutomationConnection owned by shell, so the broker can adopt the shell permission
     * identity through the public UiAutomation API. The optional watcher is an
     * IInstrumentationWatcher binder that AMS notifies when the instrumentation finishes.
     *
     * Returns null when the instrumentation was started, or a description of the failure.
     */
    String startBrokerInstrumentation(in ComponentName component, in Bundle arguments, int userId, IBinder watcher) = 2;
}
