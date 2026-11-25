# Keep JNI methods. The serial port library looks up mFd and native open/close by name.
-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class android_serialport_api.SerialPort {
    private java.io.FileDescriptor mFd;
    private static native java.io.FileDescriptor open(java.lang.String, int, int);
    public native void close();
}
