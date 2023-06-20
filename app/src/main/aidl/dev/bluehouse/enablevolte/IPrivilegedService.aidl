// IPrivilegedService.aidl
package dev.bluehouse.enablevolte;

// Declare any non-default types here with import statements

interface IPrivilegedService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    void blah() = 2;
}