// MessagePigeon.exe: finds a JDK/Java 21+, asking the user for the JDK folder (JAVA_HOME) if none is found, then starts the app jar.
// Kept to C# 5 so it compiles with the csc.exe that ships with Windows (no SDK needed).
//
//   MessagePigeon.exe [app arguments]     start the app
//   MessagePigeon.exe /probe <java.exe>   exit code 0 = Java 21+, 2 = older Java, 3 = not a working Java
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Windows.Forms;
using Microsoft.Win32;

static class Launcher
{
    const int MinMajor = 21;
    const string Title = "MessagePigeon";
    const string JarName = "messagepigeon.jar";

    static string ConfigFile
    {
        get
        {
            return Path.Combine(
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), ".messagepigeon"),
                "java-path.txt");
        }
    }

    [STAThread]
    static int Main(string[] args)
    {
        if (args.Length == 2 && args[0] == "/probe")
        {
            int m = JavaMajor(args[1]);
            return m < 0 ? 3 : (m < MinMajor ? 2 : 0);
        }

        Application.EnableVisualStyles();
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string jar = Path.Combine(dir, JarName);
        if (!File.Exists(jar))
        {
            Error("Cannot find " + JarName + " next to this program:\n" + dir);
            return 1;
        }

        string java = FindJava();
        if (java == null)
        {
            java = Prompt();
        }
        if (java == null)
        {
            return 1;
        }
        return Start(java, jar, dir, args);
    }

    // ---- finding Java ---------------------------------------------------------------------------------------

    /// <summary>The first suitable java.exe: the remembered one, JAVA_HOME, PATH, the registry, common folders.</summary>
    static string FindJava()
    {
        HashSet<string> seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (string candidate in Candidates())
        {
            string exe = ToJavaExe(candidate);
            if (exe == null || !seen.Add(exe))
            {
                continue;
            }
            if (JavaMajor(exe) >= MinMajor)
            {
                return exe;
            }
        }
        return null;
    }

    static IEnumerable<string> Candidates()
    {
        // 1. What the user chose last time.
        string saved = null;
        try
        {
            if (File.Exists(ConfigFile))
            {
                saved = File.ReadAllText(ConfigFile).Trim();
            }
        }
        catch (IOException) { }
        if (!string.IsNullOrEmpty(saved))
        {
            yield return saved;
        }

        // 2. JAVA_HOME.
        string home = Environment.GetEnvironmentVariable("JAVA_HOME");
        if (!string.IsNullOrEmpty(home))
        {
            yield return home;
        }

        // 3. PATH.
        string path = Environment.GetEnvironmentVariable("PATH") ?? "";
        foreach (string d in path.Split(Path.PathSeparator))
        {
            if (d.Trim().Length > 0)
            {
                yield return d.Trim().Trim('"');
            }
        }

        // 4. Registry entries written by the usual installers.
        foreach (string h in RegistryHomes())
        {
            yield return h;
        }

        // 5. Typical install folders (newest name first).
        string[] vendors = { "Java", "Eclipse Adoptium", "Eclipse Foundation", "Zulu", "Microsoft",
                             "Amazon Corretto", "BellSoft", "Semeru", "RedHat" };
        foreach (string pf in new string[] { Environment.GetEnvironmentVariable("ProgramFiles"),
                                             Environment.GetEnvironmentVariable("ProgramW6432") })
        {
            if (string.IsNullOrEmpty(pf))
            {
                continue;
            }
            foreach (string v in vendors)
            {
                string root = Path.Combine(pf, v);
                if (!Directory.Exists(root))
                {
                    continue;
                }
                string[] kids = Directory.GetDirectories(root);
                Array.Sort(kids, StringComparer.OrdinalIgnoreCase);
                Array.Reverse(kids);
                foreach (string k in kids)
                {
                    yield return k;
                }
            }
        }
    }

    static IEnumerable<string> RegistryHomes()
    {
        List<string> homes = new List<string>();
        string[] javaSoft = { @"SOFTWARE\JavaSoft\JDK", @"SOFTWARE\JavaSoft\Java Development Kit",
                              @"SOFTWARE\JavaSoft\JRE", @"SOFTWARE\JavaSoft\Java Runtime Environment" };
        string[] vendor = { @"SOFTWARE\Eclipse Adoptium\JDK", @"SOFTWARE\Eclipse Adoptium\JRE",
                            @"SOFTWARE\Eclipse Foundation\JDK", @"SOFTWARE\Microsoft\JDK" };
        try
        {
            using (RegistryKey hklm = RegistryKey.OpenBaseKey(RegistryHive.LocalMachine, RegistryView.Registry64))
            {
                foreach (string root in javaSoft)
                {
                    using (RegistryKey k = hklm.OpenSubKey(root))
                    {
                        if (k == null) { continue; }
                        foreach (string sub in k.GetSubKeyNames())
                        {
                            using (RegistryKey s = k.OpenSubKey(sub))
                            {
                                object v = s == null ? null : s.GetValue("JavaHome");
                                if (v != null) { homes.Add(v.ToString()); }
                            }
                        }
                    }
                }
                foreach (string root in vendor)
                {
                    using (RegistryKey k = hklm.OpenSubKey(root))
                    {
                        if (k == null) { continue; }
                        foreach (string sub in k.GetSubKeyNames())
                        {
                            foreach (string flavour in new string[] { "hotspot", "openj9" })
                            {
                                using (RegistryKey s = k.OpenSubKey(sub + @"\" + flavour + @"\MSI"))
                                {
                                    object v = s == null ? null : s.GetValue("Path");
                                    if (v != null) { homes.Add(v.ToString()); }
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception) { /* registry unavailable: the other searches still run */ }
        homes.Reverse(); // later subkeys are usually newer versions
        return homes;
    }

    /// <summary>Turns a Java home, bin folder or exe path into the path of its java.exe, or null.</summary>
    static string ToJavaExe(string candidate)
    {
        try
        {
            string c = candidate.Trim().Trim('"');
            if (c.Length == 0)
            {
                return null;
            }
            if (c.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
            {
                c = Path.GetDirectoryName(c);
            }
            foreach (string probe in new string[] { Path.Combine(c, "java.exe"), Path.Combine(Path.Combine(c, "bin"), "java.exe") })
            {
                if (File.Exists(probe))
                {
                    return probe;
                }
            }
        }
        catch (ArgumentException) { }
        return null;
    }

    /// <summary>The Java major version of this java.exe (8, 17, 21, ...), or -1 if it will not run.</summary>
    static int JavaMajor(string javaExe)
    {
        try
        {
            if (!File.Exists(javaExe))
            {
                return -1;
            }
            ProcessStartInfo psi = new ProcessStartInfo(javaExe, "-version");
            psi.UseShellExecute = false;
            psi.CreateNoWindow = true;
            psi.RedirectStandardError = true;
            psi.RedirectStandardOutput = true;
            using (Process p = Process.Start(psi))
            {
                if (!p.WaitForExit(10000))
                {
                    p.Kill();
                    return -1;
                }
                string text = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                Match m = Regex.Match(text, "version \"(\\d+)(?:\\.(\\d+))?");
                if (!m.Success)
                {
                    return -1;
                }
                int major = int.Parse(m.Groups[1].Value);
                if (major == 1 && m.Groups[2].Success)
                {
                    major = int.Parse(m.Groups[2].Value); // "1.8.0_x" means Java 8
                }
                return major;
            }
        }
        catch (Exception)
        {
            return -1;
        }
    }

    // ---- asking the user ------------------------------------------------------------------------------------

    static string Prompt()
    {
        if (MessageBox.Show(
                "MessagePigeon needs a JDK (Java " + MinMajor + " or later), but none was found on this computer.\n\n"
                + "Click OK to choose your JDK folder: the one JAVA_HOME would point to, for example\n"
                + "C:\\Program Files\\Java\\jdk-" + MinMajor,
                Title, MessageBoxButtons.OKCancel, MessageBoxIcon.Information) != DialogResult.OK)
        {
            return null;
        }
        string start = null;
        string pf = Environment.GetEnvironmentVariable("ProgramFiles");
        if (!string.IsNullOrEmpty(pf) && Directory.Exists(Path.Combine(pf, "Java")))
        {
            start = Path.Combine(pf, "Java");
        }
        while (true)
        {
            string folder = FolderPicker.Pick("Select your JDK folder (JAVA_HOME), Java " + MinMajor + " or later", start);
            if (folder == null)
            {
                return null;
            }
            start = folder;
            string exe = ToJavaExe(folder); // also accepts the bin folder, to be forgiving
            int major = exe == null ? -1 : JavaMajor(exe);
            string problem = null;
            if (exe == null)
            {
                problem = "There is no bin\\java.exe in:\n" + folder
                    + "\n\nChoose the JDK folder itself (the one that contains bin).";
            }
            else if (major < 0)
            {
                problem = "The java.exe in this folder will not run:\n" + folder;
            }
            else if (major < MinMajor)
            {
                problem = "That is Java " + major + ". MessagePigeon needs Java " + MinMajor + " or later.\n" + folder;
            }
            if (problem == null)
            {
                Remember(HomeOf(exe));
                return exe;
            }
            if (MessageBox.Show(problem + "\n\nTry another folder?", Title, MessageBoxButtons.RetryCancel,
                    MessageBoxIcon.Warning) != DialogResult.Retry)
            {
                return null;
            }
        }
    }

    /// <summary>The JAVA_HOME for a java.exe: its folder, or the parent when that folder is called bin.</summary>
    static string HomeOf(string javaExe)
    {
        string bin = Path.GetDirectoryName(javaExe);
        if (string.Equals(Path.GetFileName(bin), "bin", StringComparison.OrdinalIgnoreCase))
        {
            return Path.GetDirectoryName(bin);
        }
        return bin;
    }

    static void Remember(string javaHome)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(ConfigFile));
            File.WriteAllText(ConfigFile, javaHome);
        }
        catch (Exception)
        {
            // not fatal: the user will simply be asked again next time
        }
    }

    // ---- starting the app -----------------------------------------------------------------------------------

    static int Start(string javaExe, string jar, string dir, string[] args)
    {
        string javaw = Path.Combine(Path.GetDirectoryName(javaExe), "javaw.exe");
        string exe = File.Exists(javaw) ? javaw : javaExe;
        string arguments = "-jar " + Quote(jar);
        foreach (string a in args)
        {
            arguments += " " + Quote(a);
        }
        ProcessStartInfo psi = new ProcessStartInfo(exe, arguments);
        psi.UseShellExecute = false;
        psi.CreateNoWindow = true;
        psi.WorkingDirectory = dir;
        try
        {
            using (Process p = Process.Start(psi))
            {
                if (p.WaitForExit(2500) && p.ExitCode != 0)
                {
                    Error("Java stopped straight away (exit code " + p.ExitCode + ").\n\nJava used:\n" + exe);
                    return p.ExitCode;
                }
            }
            return 0;
        }
        catch (Exception e)
        {
            Error("Could not start Java:\n" + exe + "\n\n" + e.Message);
            return 1;
        }
    }

    static string Quote(string s)
    {
        return "\"" + s.Replace("\"", "\\\"") + "\"";
    }

    static void Error(string message)
    {
        MessageBox.Show(message, Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
    }
}

/// <summary>
/// The modern Windows folder picker (address bar, typeable path). Falls back to the classic tree dialog if the
/// shell COM objects are unavailable.
/// </summary>
static class FolderPicker
{
    const uint FOS_PICKFOLDERS = 0x20;
    const uint FOS_FORCEFILESYSTEM = 0x40;
    const uint FOS_PATHMUSTEXIST = 0x800;
    const uint SIGDN_FILESYSPATH = 0x80058000;
    const int ERROR_CANCELLED = unchecked((int)0x800704C7);

    [ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
    class FileOpenDialogCoClass { }

    [ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IFileOpenDialog
    {
        [PreserveSig] int Show(IntPtr parent);
        void SetFileTypes(uint cFileTypes, IntPtr rgFilterSpec);
        void SetFileTypeIndex(uint iFileType);
        void GetFileTypeIndex(out uint piFileType);
        void Advise(IntPtr pfde, out uint pdwCookie);
        void Unadvise(uint dwCookie);
        void SetOptions(uint fos);
        void GetOptions(out uint pfos);
        void SetDefaultFolder(IShellItem psi);
        void SetFolder(IShellItem psi);
        void GetFolder(out IShellItem ppsi);
        void GetCurrentSelection(out IShellItem ppsi);
        void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string pszName);
        void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string pszName);
        void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string pszTitle);
        void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string pszText);
        void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string pszLabel);
        void GetResult(out IShellItem ppsi);
        void AddPlace(IShellItem psi, int fdap);
        void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string pszDefaultExtension);
        void Close(int hr);
        void SetClientGuid(ref Guid guid);
        void ClearClientData();
        void SetFilter(IntPtr pFilter);
        void GetResults(out IntPtr ppenum);
        void GetSelectedItems(out IntPtr ppsai);
    }

    [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IShellItem
    {
        void BindToHandler(IntPtr pbc, ref Guid bhid, ref Guid riid, out IntPtr ppv);
        void GetParent(out IShellItem ppsi);
        void GetDisplayName(uint sigdnName, [MarshalAs(UnmanagedType.LPWStr)] out string ppszName);
        void GetAttributes(uint sfgaoMask, out uint psfgaoAttribs);
        void Compare(IShellItem psi, uint hint, out int piOrder);
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
    static extern void SHCreateItemFromParsingName(string path, IntPtr pbc, ref Guid riid, out IShellItem item);

    /// <summary>The chosen folder, or null if the user cancelled.</summary>
    public static string Pick(string title, string startFolder)
    {
        try
        {
            IFileOpenDialog dialog = (IFileOpenDialog)new FileOpenDialogCoClass();
            uint options;
            dialog.GetOptions(out options);
            dialog.SetOptions(options | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM | FOS_PATHMUSTEXIST);
            dialog.SetTitle(title);
            if (!string.IsNullOrEmpty(startFolder) && Directory.Exists(startFolder))
            {
                Guid iid = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
                IShellItem item;
                SHCreateItemFromParsingName(startFolder, IntPtr.Zero, ref iid, out item);
                dialog.SetFolder(item);
            }
            int hr = dialog.Show(IntPtr.Zero);
            if (hr == ERROR_CANCELLED)
            {
                return null;
            }
            if (hr != 0)
            {
                Marshal.ThrowExceptionForHR(hr);
            }
            IShellItem result;
            dialog.GetResult(out result);
            string path;
            result.GetDisplayName(SIGDN_FILESYSPATH, out path);
            return path;
        }
        catch (COMException) { }
        catch (InvalidCastException) { }
        catch (EntryPointNotFoundException) { }

        using (FolderBrowserDialog d = new FolderBrowserDialog())
        {
            d.Description = title;
            d.ShowNewFolderButton = false;
            if (!string.IsNullOrEmpty(startFolder) && Directory.Exists(startFolder))
            {
                d.SelectedPath = startFolder;
            }
            return d.ShowDialog() == DialogResult.OK ? d.SelectedPath : null;
        }
    }
}
