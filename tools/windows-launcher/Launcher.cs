using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;

internal static class Launcher
{
    private const string Title = "용사가 다한다";
    private static readonly string Root = AppDomain.CurrentDomain.BaseDirectory;

    [STAThread]
    private static void Main()
    {
        Application.EnableVisualStyles();
        using (var window = new Form())
        {
            window.Text = Title;
            window.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            window.FormBorderStyle = FormBorderStyle.FixedDialog;
            window.ControlBox = false;
            window.StartPosition = FormStartPosition.CenterScreen;
            window.AutoScaleMode = AutoScaleMode.Dpi;
            window.ClientSize = new Size(390, 87);
            var label = new Label { Text = "게임을 준비하고 있어요…", AutoSize = true, Location = new Point(20, 16) };
            var progress = new ProgressBar { Style = ProgressBarStyle.Marquee, Location = new Point(20, 48), Size = new Size(350, 12) };
            window.Controls.Add(label);
            window.Controls.Add(progress);
            window.Shown += async delegate
            {
                try
                {
                    await Task.Run((Action)StartGame);
                }
                catch (Exception error)
                {
                    MessageBox.Show(window, error.Message, Title, MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
                finally { window.Close(); }
            };
            Application.Run(window);
        }
    }

    private static void StartGame()
    {
        string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
        string java = String.IsNullOrWhiteSpace(javaHome) ? "javaw.exe"
            : Path.Combine(javaHome.Trim('"'), "bin", "javaw.exe");
        string libs = Path.Combine(Root, "lib");
        if (!Directory.Exists(libs))
        {
            if (!File.Exists(Path.Combine(Root, "gradlew.bat")))
                throw new InvalidOperationException("게임 폴더에서 실행해 주세요. 바탕화면에는 실행 파일의 바로가기를 만들 수 있어요.");
            PrepareDistribution();
            libs = Path.Combine(Root, "lwjgl3", "build", "install", "lwjgl3", "lib");
        }
        if (!Directory.Exists(libs) || Directory.GetFiles(libs, "*.jar").Length == 0)
            throw new InvalidOperationException("게임 라이브러리를 찾을 수 없어요. 게임 폴더를 다시 확인해 주세요.");
        var start = new ProcessStartInfo(java, "-Dfile.encoding=UTF-8 -cp \"" + Path.Combine(libs, "*")
            + "\" com.yongsa.lwjgl3.Lwjgl3Launcher");
        start.WorkingDirectory = Root;
        start.UseShellExecute = false;
        try { using (Process child = Process.Start(start)) { } }
        catch (System.ComponentModel.Win32Exception error)
        {
            throw new InvalidOperationException("JDK 17 설치와 JAVA_HOME 설정을 확인해 주세요.\n" + error.Message);
        }
    }

    private static void PrepareDistribution()
    {
        var start = new ProcessStartInfo(Path.Combine(Environment.SystemDirectory, "cmd.exe"),
            "/d /c gradlew.bat --console=plain :lwjgl3:installDist");
        start.WorkingDirectory = Root;
        start.UseShellExecute = false;
        start.CreateNoWindow = true;
        start.RedirectStandardOutput = true;
        start.RedirectStandardError = true;
        var tail = new StringBuilder();
        DataReceivedEventHandler collect = delegate(object sender, DataReceivedEventArgs e)
        {
            if (e.Data == null) return;
            lock (tail)
            {
                tail.AppendLine(e.Data);
                if (tail.Length > 3000) tail.Remove(0, tail.Length - 3000);
            }
        };
        using (var process = new Process { StartInfo = start })
        {
            process.OutputDataReceived += collect;
            process.ErrorDataReceived += collect;
            process.Start();
            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            process.WaitForExit();
            if (process.ExitCode != 0)
                throw new InvalidOperationException("게임을 준비하지 못했어요. JDK 17 설치와 첫 실행의 인터넷 연결을 확인해 주세요.\n\n" + tail);
        }
    }
}
