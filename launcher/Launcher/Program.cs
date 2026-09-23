using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Security.Principal;
using System.Threading;
using System.Windows.Forms;

namespace CognexJobx.Launcher
{
    /// <summary>
    /// 单文件启动器（模式参考 CameraViewerDotnet）：
    /// 1. 检测本机是否存在 Java 25+（JAVA_HOME / PATH / 常见安装目录）；
    /// 2. 没有则静默安装内嵌的 OpenJDK JRE 25 MSI（需要管理员，未提权时自动以 runas 重启自身）；
    /// 3. 安装后设置 JAVA_HOME（用户级，能写则再写机器级）；
    /// 4. 把内嵌的 fat jar 释放到 %LOCALAPPDATA%\CognexJobxBackupTool\App 并启动。
    /// </summary>
    internal static class Program
    {
        private const string JdkMsiResource = "zulu-jdk25.msi";
        private const string AppJarResource = "app.jar";
        private const string AppDirName = "CognexJobxBackupTool";
        private const string AppJarName = "jobx文件备份助手.jar";
        private const int RequiredMajor = 25;

        [STAThread]
        private static int Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            using (var form = new LauncherForm())
            {
                form.Shown += async (s, e) =>
                {
                    try
                    {
                        string java = FindJava();
                        if (java == null)
                        {
                            // 未提权则先提权重启（MSI 为 per-machine 安装，需要管理员）
                            if (!IsElevated())
                            {
                                RelaunchElevated();
                                Application.Exit();
                                return;
                            }

                            SetStep(form, "正在安装 Java 25 运行时...", "Installing OpenJDK JRE 25 (silent)...");
                            int code = await InstallJdkAsync(form);
                            if (code != 0 && code != 3010) // 3010 = 成功但需要重启
                            {
                                MessageBox.Show(form,
                                    "JDK 25 运行时安装失败，msiexec 退出码：" + code +
                                    "\n\n请以管理员身份运行本程序后重试。",
                                    "Jobx 文件备份助手", MessageBoxButtons.OK, MessageBoxIcon.Error);
                                Application.Exit();
                                return;
                            }

                            java = FindJava();
                            if (java == null)
                            {
                                MessageBox.Show(form,
                                    "JDK 25 安装后仍未找到 java.exe，请手动安装 JDK 25 或设置 JAVA_HOME。",
                                    "Jobx 文件备份助手", MessageBoxButtons.OK, MessageBoxIcon.Error);
                                Application.Exit();
                                return;
                            }
                        }

                        // 设置 JAVA_HOME（优先机器级，失败则用户级）
                        SetJavaHome(Path.GetDirectoryName(Path.GetDirectoryName(java)));

                        SetStep(form, "正在启动软件...", "Starting jobx 文件备份助手...");
                        string appDir = Path.Combine(
                            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                            AppDirName, "App");
                        Directory.CreateDirectory(appDir);
                        string appPath = Path.Combine(appDir, AppJarName);
                        ExtractResource(AppJarResource, appPath);

                        string javaw = Path.Combine(Path.GetDirectoryName(java), "javaw.exe");
                        if (!File.Exists(javaw)) javaw = java;

                        Process.Start(new ProcessStartInfo
                        {
                            FileName = javaw,
                            Arguments = "-jar \"" + appPath + "\"",
                            WorkingDirectory = appDir,
                            UseShellExecute = false,
                        });
                        Application.Exit();
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show(form, "启动失败：" + ex.Message, "Jobx 文件备份助手",
                            MessageBoxButtons.OK, MessageBoxIcon.Error);
                        Application.Exit();
                    }
                };
                Application.Run(form);
            }
            return 0;
        }

        private static void SetStep(LauncherForm form, string zh, string en)
        {
            form.lblStep.Text = zh;
            form.lblDetail.Text = en;
            form.Refresh();
        }

        private static bool IsElevated()
        {
            using (var identity = WindowsIdentity.GetCurrent())
            {
                var principal = new WindowsPrincipal(identity);
                return principal.IsInRole(WindowsBuiltInRole.Administrator);
            }
        }

        /// <summary>以管理员权限重启自身，完成安装并继续启动流程。</summary>
        private static void RelaunchElevated()
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = Application.ExecutablePath,
                UseShellExecute = true,
                Verb = "runas",
            });
        }

        /// <summary>查找满足版本要求的 java.exe（25+），找不到返回 null。</summary>
        private static string FindJava()
        {
            var candidates = new System.Collections.Generic.List<string>();

            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
                candidates.Add(Path.Combine(javaHome, "bin", "java.exe"));

            // PATH 中的 java
            string pathJava = FindOnPath("java.exe");
            if (pathJava != null) candidates.Add(pathJava);

            // 常见安装目录（含 Zulu MSI 默认路径）
            string[] roots =
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Zulu"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Zulu"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Java"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Eclipse Adoptium"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Eclipse Foundation"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Microsoft"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86), "Java"),
            };
            foreach (string root in roots)
            {
                try
                {
                    if (!Directory.Exists(root)) continue;
                    foreach (string jdk in Directory.GetDirectories(root))
                    {
                        string j = Path.Combine(jdk, "bin", "java.exe");
                        if (File.Exists(j)) candidates.Add(j);
                    }
                }
                catch { }
            }

            foreach (string candidate in candidates)
            {
                try
                {
                    if (File.Exists(candidate) && GetJavaMajor(candidate) >= RequiredMajor)
                        return candidate;
                }
                catch { }
            }
            return null;
        }

        private static string FindOnPath(string file)
        {
            var psi = new ProcessStartInfo
            {
                FileName = "where.exe",
                Arguments = file,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                CreateNoWindow = true,
            };
            try
            {
                using (var p = Process.Start(psi))
                {
                    string line = p.StandardOutput.ReadLine();
                    p.WaitForExit(10000);
                    if (p.ExitCode == 0 && !string.IsNullOrEmpty(line) && File.Exists(line))
                        return line;
                }
            }
            catch { }
            return null;
        }

        /// <summary>运行 `java -version`（输出到 stderr），解析主版本号。</summary>
        private static int GetJavaMajor(string javaExe)
        {
            var psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = "-version",
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
            };
            using (var p = Process.Start(psi))
            {
                string output = p.StandardError.ReadToEnd() + p.StandardOutput.ReadToEnd();
                p.WaitForExit(15000);

                // 形如 version "25.0.2"、version "1.8.0_392"
                int idx = output.IndexOf("version \"", StringComparison.Ordinal);
                if (idx < 0) return -1;
                string ver = output.Substring(idx + 9);
                int end = ver.IndexOf('"');
                if (end > 0) ver = ver.Substring(0, end);
                ver = ver.Trim();
                if (ver.StartsWith("1.")) ver = ver.Substring(2); // 1.8.x -> 8.x
                int dot = ver.IndexOf('.');
                if (dot > 0) ver = ver.Substring(0, dot);
                int major;
                return int.TryParse(ver, out major) ? major : -1;
            }
        }

        /// <summary>释放内嵌的 Zulu MSI 并以完全静默方式安装。</summary>
        private static async System.Threading.Tasks.Task<int> InstallJdkAsync(LauncherForm form)
        {
            string temp = Path.Combine(Path.GetTempPath(), "CognexJobx-zulu-jdk25.msi");
            ExtractResource(JdkMsiResource, temp);

            var psi = new ProcessStartInfo
            {
                FileName = "msiexec.exe",
                Arguments = "/i \"" + temp + "\" /quiet /norestart",
                UseShellExecute = true,
            };
            var tcs = new System.Threading.Tasks.TaskCompletionSource<int>();
            using (var p = new Process { StartInfo = psi, EnableRaisingEvents = true })
            {
                p.Exited += (sender, e) => tcs.TrySetResult(p.ExitCode);
                p.Start();
                var delay = System.Threading.Tasks.Task.Delay(TimeSpan.FromMinutes(15));
                var done = await System.Threading.Tasks.Task.WhenAny(tcs.Task, delay);
                if (done != tcs.Task)
                {
                    try { p.Kill(); } catch { }
                    return -1;
                }
                return tcs.Task.Result;
            }
        }

        /// <summary>设置 JAVA_HOME：先试机器级（需管理员），失败退到用户级。</summary>
        private static void SetJavaHome(string jdkDir)
        {
            if (string.IsNullOrEmpty(jdkDir) || !Directory.Exists(jdkDir)) return;
            try
            {
                Environment.SetEnvironmentVariable("JAVA_HOME", jdkDir, EnvironmentVariableTarget.Machine);
            }
            catch
            {
                try { Environment.SetEnvironmentVariable("JAVA_HOME", jdkDir, EnvironmentVariableTarget.User); }
                catch { }
            }
        }

        private static void ExtractResource(string name, string dest)
        {
            using (var stream = Assembly.GetExecutingAssembly().GetManifestResourceStream(name))
            {
                if (stream == null)
                    throw new InvalidOperationException("内嵌资源缺失：" + name);
                if (File.Exists(dest) && !TryDeleteFile(dest))
                {
                    // 被占用且无法结束进程时换时间戳文件名，保证总能释放出新版本
                    dest = dest + "." + DateTime.Now.Ticks + ".jar";
                }
                using (var fs = new FileStream(dest, FileMode.Create, FileAccess.Write))
                {
                    stream.CopyTo(fs);
                }
            }
        }

        /// <summary>删除文件，若被占用（上次运行的 java 进程未退出）则先结束相关 java 进程再重试；成功删除返回 true。</summary>
        private static bool TryDeleteFile(string path)
        {
            if (!File.Exists(path)) return true;
            for (int attempt = 0; attempt < 4; attempt++)
            {
                try { File.Delete(path); return true; }
                catch (IOException) { }
                catch (UnauthorizedAccessException) { }
                if (attempt == 0) KillJavaHoldingFile(path);
                Thread.Sleep(500);
            }
            return !File.Exists(path);
        }

        /// <summary>结束命令行中包含本 jar 路径的 java 进程。</summary>
        private static void KillJavaHoldingFile(string jarPath)
        {
            try
            {
                var query = new System.Management.SelectQuery("Win32_Process",
                    "Name='java.exe' OR Name='javaw.exe'");
                using (var searcher = new System.Management.ManagementObjectSearcher(query))
                {
                    foreach (System.Management.ManagementObject mo in searcher.Get())
                    {
                        try
                        {
                            string cmd = mo["CommandLine"] as string ?? "";
                            if (cmd.IndexOf(jarPath, StringComparison.OrdinalIgnoreCase) >= 0)
                            {
                                mo.InvokeMethod("Terminate", null);
                            }
                        }
                        catch { }
                    }
                }
            }
            catch { }
        }
    }

    /// <summary>简单的启动进度窗口。</summary>
    internal class LauncherForm : Form
    {
        public Label lblStep;
        public Label lblDetail;

        public LauncherForm()
        {
            Text = "Jobx 文件备份助手";
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            MinimizeBox = false;
            ClientSize = new Size(460, 160);
            BackColor = Color.White;

            var title = new Label
            {
                Text = "Jobx 文件备份助手",
                Font = new Font("Microsoft YaHei UI", 14F, FontStyle.Bold),
                AutoSize = true,
                Location = new Point(24, 24),
            };
            lblStep = new Label
            {
                Text = "正在准备...",
                Font = new Font("Microsoft YaHei UI", 10F),
                AutoSize = true,
                Location = new Point(24, 70),
            };
            lblDetail = new Label
            {
                Text = "Preparing...",
                ForeColor = Color.Gray,
                AutoSize = true,
                Location = new Point(24, 98),
            };
            Controls.Add(title);
            Controls.Add(lblStep);
            Controls.Add(lblDetail);
        }
    }
}
