package eu.chainfire.libsuperuser;

import android.os.Handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Minimal compatibility shell used by the root helpers in this application. */
public final class Shell {

  public interface OnCommandResultListener {
    void onCommandResult(int commandCode, int exitCode, List<String> output);
  }

  public static final class SH {
    private SH() {}

    public static List<String> run(String command) {
      return execute(command, false);
    }
  }

  public static final class Builder {
    private boolean useSu;
    private Handler handler;
    private String shell = "sh";

    public Builder useSU() {
      useSu = true;
      return this;
    }

    /** Selects the shell binary used for non-root commands (e.g. "/bin/false" in tests). */
    public Builder setShell(String shellBinary) {
      if (shellBinary != null && !shellBinary.isEmpty()) shell = shellBinary;
      return this;
    }

    public Builder setHandler(Handler commandHandler) {
      handler = commandHandler;
      return this;
    }

    public Interactive open() {
      return new Interactive(useSu, handler, shell);
    }
  }

  public static final class Interactive {
    private final boolean useSu;
    private final Handler handler;
    private final String shell;
    private boolean running = true;

    private Interactive(boolean root, Handler commandHandler, String shellBinary) {
      useSu = root;
      handler = commandHandler;
      shell = shellBinary;
    }

    public boolean isRunning() {
      return running;
    }

    public void addCommand(String command) {
      execute(command, useSu, shell);
    }

    public void addCommand(String command, int commandCode, OnCommandResultListener listener) {
      Runnable task =
          () -> {
            ArrayList<String> output = new ArrayList<>(execute(command, useSu, shell));
            if (listener != null) listener.onCommandResult(commandCode, 0, output);
          };
      task.run();
    }

    public void waitForIdle() {}

    public void close() {
      running = false;
    }
  }

  private static List<String> execute(String command, boolean root) {
    return execute(command, root, "sh");
  }

  private static List<String> execute(String command, boolean root, String shell) {
    ArrayList<String> output = new ArrayList<>();
    Process process = null;
    try {
      process =
          root
              ? new ProcessBuilder("su", "-c", command).start()
              : new ProcessBuilder(shell, "-c", command).start();
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) output.add(line);
      }
      process.waitFor();
    } catch (IOException exception) {
      // Shell/su binary missing or not executable: return what we have, do not poison the thread.
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      if (process != null) process.destroy();
    }
    return output;
  }

  private Shell() {}
}