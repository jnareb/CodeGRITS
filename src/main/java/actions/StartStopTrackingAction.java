package actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import components.ConfigDialog;
import entity.Config;
import org.jetbrains.annotations.NotNull;
import trackers.EyeTracker;
import trackers.IDETracker;
import trackers.ScreenRecorder;
import utils.AvailabilityChecker;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.Objects;

/**
 * This class is the action for starting/stopping tracking.
 */
public class StartStopTrackingAction extends AnAction {

    /**
     * The action ID. Must be the same as the ID in the plugin.xml file.
     */
    final public static String ACTION_ID = "CodeGRITS.StartStopTracking";
    /**
     * This variable indicates whether the tracking is started.
     */
    private static boolean isTracking = false;
    /**
     * This variable is the IDE tracker.
     */
    private static IDETracker iDETracker;
    /**
     * This variable is the eye tracker.
     */
    private static EyeTracker eyeTracker;
    /**
     * This variable is the screen recorder.
     */
    private final ScreenRecorder screenRecorder = ScreenRecorder.getInstance();
    /**
     * This variable is the configuration.
     */
    Config config = new Config();

    /**
     * Update the text of the action button.
     *
     * @param e The action event.
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setText(isTracking ? "Stop Tracking" : "Start Tracking");
    }

    /**
     * This method is called when the action is performed. It will start/stop tracking.
     *
     * @param e The action event.
     */
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();

        if (!tryLoadConfigWithNotifications(project))
            return;

        try {
            if (!isTracking) {
                if (isEyeTrackingSelected()) {
                    if (!isEyeTrackingAvailableWithNotifications())
                        return;
                }

                startTracking(project);

            } else {

                stopTracking();
            }
        } catch (ParserConfigurationException | TransformerException | IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void startTracking(Project project) throws IOException, ParserConfigurationException {
        isTracking = true;
        EyeTracker.createNotification("StartStopTrackingAction::startTracking(" + project + ") start...");

        ConfigAction.setIsEnabled(false);
        AddLabelActionGroup.setIsEnabled(true);

        String projectPath = project != null ? project.getBasePath() : "";
        String realDataOutputPath = Objects.equals(config.getDataOutputPath(), ConfigDialog.selectDataOutputPlaceHolder)
                ? projectPath : config.getDataOutputPath();
        realDataOutputPath += "/" + System.currentTimeMillis() + "/";

        EyeTracker.createNotification("StartStopTrackingAction::startTracking(): screenRecorder...<br>\n" +
                "config=<br>\n" + config.toString());

        if (isScreenRecordingSelected()) {
            screenRecorder.setDataOutputPath(realDataOutputPath);
            screenRecorder.startRecording();
        }

        EyeTracker.createNotification("StartStopTrackingAction::startTracking(): iDETracker...");

        iDETracker = IDETracker.getInstance();
        iDETracker.setProjectPath(projectPath);
        iDETracker.setDataOutputPath(realDataOutputPath);
        iDETracker.startTracking(project);

        EyeTracker.createNotification("StartStopTrackingAction::startTracking(): EyeTracker...<br>\n" +
                "projectPath=" + projectPath + "<br>\n" +
                "realDataOutputPath=" + realDataOutputPath + "<br>\n" +
                "pythonInterpreter: " + config.getPythonInterpreter() + "<br>\n" +
                "sampleFrequency: " + config.getSampleFreq() + "<br>\n" +
                "deviceIndex: " + config.getEyeTrackerDevice() + "<br>\n" +
                "project=" + project + "<br>\n");

        if (isEyeTrackingSelected()) {
            eyeTracker = new EyeTracker();
            eyeTracker.setProjectPath(projectPath);
            eyeTracker.setDataOutputPath(realDataOutputPath);
            eyeTracker.setPythonInterpreter(config.getPythonInterpreter());
            eyeTracker.setSampleFrequency(config.getSampleFreq());
            eyeTracker.setDeviceIndex(config.getEyeTrackerDevice());
            eyeTracker.setPythonScriptTobii();
            eyeTracker.setPythonScriptMouse();
            eyeTracker.setPythonScriptIMotions();
            eyeTracker.startTracking(project);
        }

        EyeTracker.createNotification("StartStopTrackingAction::startTracking(" + project + ")<br>\n" +
                "eyeTracker=" + eyeTracker + "<br>\n" +
                "projectPath=" + projectPath + "<br>\n" +
                "realDataOutputPath=" + realDataOutputPath + "<br>\n"
        );
    }

    public void stopTracking() throws TransformerException, IOException {
        isTracking = false;

        AddLabelAction.setIsEnabled(false);
        ConfigAction.setIsEnabled(true);

        iDETracker.stopTracking();
        if (isEyeTrackingSelected() && eyeTracker != null) {
            eyeTracker.stopTracking();
        }
        if (isScreenRecordingSelected()) {
            screenRecorder.stopRecording();
        }
        eyeTracker = null;
    }

    boolean tryLoadConfigWithNotifications(Project project) {
        EyeTracker.createNotification("StartStopTrackingAction::tryLoadConfigWithNotifications(" + project + ") start...");
        if (config.configExists()) {
            config.loadFromJson();
            return true;
        } else {
            Notification notification = new Notification("CodeGRITS Notification Group", "Configuration",
                    "Please configure the plugin first.", NotificationType.WARNING);
            notification.notify(project);
            return false;
        }
    }

    private Boolean isEyeTrackingSelected() {
        return config.getCheckBoxes().get(1);
    }

    private Boolean isScreenRecordingSelected() {
        return config.getCheckBoxes().get(2);
    }

    private boolean isEyeTrackingAvailableWithNotifications() throws IOException, InterruptedException {
        if (!AvailabilityChecker.checkPythonEnvironment(config.getPythonInterpreter())) {
            JOptionPane.showMessageDialog(null, "Python interpreter not found. Please configure the plugin first.");
            return false;
        }
        if (config.getEyeTrackerDevice() == EyeTracker.EYE_TRACKER_TOBII && !AvailabilityChecker.checkEyeTracker(config.getPythonInterpreter())) {
            JOptionPane.showMessageDialog(null, "Eye tracker not found. Please configure the mouse simulation first.");
            return false;
        }
        return true;
    }

    public static boolean isTracking() {
        return isTracking;
    }

    public static boolean isPaused() {
        if (iDETracker == null) {
            EyeTracker.createNotification("StartStopTrackingAction.isPaused() iDETracker is null");
            return false;
        }
        return !iDETracker.isTracking();
    }

    public static void pauseTracking() {
        iDETracker.pauseTracking();
        if (eyeTracker != null) {
            eyeTracker.pauseTracking();
        }
    }

    public static void resumeTracking() {
        iDETracker.resumeTracking();
        if (eyeTracker != null) {
            eyeTracker.resumeTracking();
        }
    }

}