package mihon.app.shizuku;

interface IShellInterface {
    void install(in AssetFileDescriptor apk) = 1;
    String runCommand(String command);

    void destroy() = 16777114;
}
