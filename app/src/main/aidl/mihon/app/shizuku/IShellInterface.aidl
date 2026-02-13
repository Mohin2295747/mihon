package mihon.app.shizuku;

import android.content.res.AssetFileDescriptor;

interface IShellInterface {
    void install(in AssetFileDescriptor apk);
    String runCommand(String command);
    void destroy();
}