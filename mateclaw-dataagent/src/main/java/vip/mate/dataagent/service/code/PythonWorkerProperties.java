package vip.mate.dataagent.service.code;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConfigurationProperties(prefix = "mateclaw.python.worker")
public class PythonWorkerProperties {
    private boolean enabled = true;
    private String pythonCommand = "";
    private String workerHome;
    private Path tempRoot = Paths.get(System.getProperty("java.io.tmpdir"), "mateclaw-python-worker");
    private int maxConcurrentTasks = 2;
    private int maxQueuedTasks = 32;
    private int maxCompletedTasks = 256;
    private long completedTaskTtlMillis = 300_000;
    private int defaultTimeoutSeconds = 60;
    private int maxStdoutBytes = 50_000;
    private int maxStderrBytes = 10_000;
    private long terminationGraceMillis = 250;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPythonCommand() { return pythonCommand; }
    public void setPythonCommand(String pythonCommand) { this.pythonCommand = pythonCommand; }
    public String getWorkerHome() { return workerHome; }
    public void setWorkerHome(String workerHome) { this.workerHome = workerHome; }
    public Path getTempRoot() { return tempRoot; }
    public void setTempRoot(Path tempRoot) { this.tempRoot = tempRoot; }
    public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
    public void setMaxConcurrentTasks(int maxConcurrentTasks) { this.maxConcurrentTasks = maxConcurrentTasks; }
    public int getMaxQueuedTasks() { return maxQueuedTasks; }
    public void setMaxQueuedTasks(int maxQueuedTasks) { this.maxQueuedTasks = maxQueuedTasks; }
    public int getMaxCompletedTasks() { return maxCompletedTasks; }
    public void setMaxCompletedTasks(int maxCompletedTasks) { this.maxCompletedTasks = maxCompletedTasks; }
    public long getCompletedTaskTtlMillis() { return completedTaskTtlMillis; }
    public void setCompletedTaskTtlMillis(long completedTaskTtlMillis) { this.completedTaskTtlMillis = completedTaskTtlMillis; }
    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public int getMaxStdoutBytes() { return maxStdoutBytes; }
    public void setMaxStdoutBytes(int maxStdoutBytes) { this.maxStdoutBytes = maxStdoutBytes; }
    public int getMaxStderrBytes() { return maxStderrBytes; }
    public void setMaxStderrBytes(int maxStderrBytes) { this.maxStderrBytes = maxStderrBytes; }
    public long getTerminationGraceMillis() { return terminationGraceMillis; }
    public void setTerminationGraceMillis(long terminationGraceMillis) { this.terminationGraceMillis = terminationGraceMillis; }
}
