package actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import entity.Config;
import org.jetbrains.annotations.NotNull;
import trackers.EyeTracker;
import utils.StimuliPresentationStatusWatcher;
import utils.TcpCheck;

import java.awt.*;
import java.io.IOException;

/**
 * This class is the action for automatically starting/stopping tracking when using iMotions Lab server.
 */
public class AutoStartStopIMotionsTrackingAction extends DumbAwareToggleAction {
    /**
     * This variable indicates whether to automatically start/stop tracking.
     */
    private static boolean isAutoTracking = false;

    /**
     * Checks whether automatically staring/stopping tracking is currently enabled.
     * @return true if auto-tracking is enabled, false otherwise
     */
    public static boolean isAutoTracking() {
        return isAutoTracking;
    }

    /**
     * This variable indicates if the iMotions Lab server is up
     * and if the stimulus presentation has started.
     */
    private static boolean shouldBeTracking = false;

    /**
     * Checks whether the iMotions Lab server is up and the stimulus presentation has started.
     * @return true if iMotions Lab server is up and stimulus presentation has started, false otherwise
     */
    public static boolean shouldBeTracking() {
        return shouldBeTracking;
    }

    /**
     * This variable is the configuration.
     */
    Config config = new Config();
    Project currentProject = null;

    /**
     * This variable represents an instance of {@link StimuliPresentationStatusWatcher} which is responsible
     * for monitoring the presentation status of stimuli via TCP communication with an iMotions Lab server.
     * <p>
     * It is initialized with:
     * <ul>
     *   <li>A server address ("127.0.0.1", aka "localhost").
     *   <li>A server port (8088).
     *   <li>A callback function for responding to processed events (no-op).
     * </ul>
     * Note that the server address and port must be the same as the one used
     * by the 'imotions_to_codegrits.py' script in the 'resources/scripts/' directory.
     * iMotions Lab server must be configured to send iMotions Events API events
     * as JSON, via TCP, on port 8088.
     * <p>
     * The {@link StimuliPresentationStatusWatcher} listens for status updates and emits corresponding events
     * ("start", "stop", "data", "close", or "failed") based on the received information. This watcher is
     * used to automatically start or stop tracking eye movements when a stimulus presentation begins or ends.
     */
    private final StimuliPresentationStatusWatcher stimuliWatcher =
            new StimuliPresentationStatusWatcher("127.0.0.1", 8088, s -> {
                switch (s) {
                    case "start":
                        EyeTracker.createNotification("stimuliWatcher: Stimulus presentation started");
                        shouldBeTracking = true;
                        break;
                    case "stop":
                        EyeTracker.createNotification("stimuliWatcher: Stimulus presentation stopped");
                        shouldBeTracking = false;
                        break;
                    case "close":
                        EyeTracker.createNotification("stimuliWatcher: iMotions Lab server disconnected");
                        shouldBeTracking = false;
                        break;
                    case "failed":
                        EyeTracker.createNotification("stimuliWatcher: iMotions Lab server connection failed");
                        shouldBeTracking = false;
                        break;
                }
            });

    /**
     * Returns the selected (checked) state of the action.
     *
     * @param e the action event representing the place and context in which the selected state is queried.
     * @return true if the action is selected, false otherwise
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return isAutoTracking;
    }

    /**
     * Updates the state of the action.  In this case, enables or disables this action,
     * depending on the CodeGRITS configuration.
     * <p>
     * The automatic start/stop of tracking should be enabled (available) only when:
     * <ol>
     *     <li>CodeGRITS is configured</li>
     *     <li>Eye tracking is enabled</li>
     *     <li>iMotions Lab is used for eye-tracking</li>
     * </ol>
     * It also automatically keeps `currentProject` up to date.
     *
     * @param e Carries information on the invocation place and data available
     */
    @Override
    public void update(@NotNull AnActionEvent e) {
        // CodeGRITS must be (1) configured (2) to use eye-tracking (3) with iMotions
        if (!config.configExists()) {
            e.getPresentation().setEnabled(false);
            return;
        }
        config.loadFromJson();   // should be fast enough for update()
        if (config.getCheckBoxes() == null || !config.getCheckBoxes().get(1)) {
            e.getPresentation().setEnabled(false);
            return;
        }
        e.getPresentation().setEnabled(
                config.getEyeTrackerDevice() == EyeTracker.EYE_TRACKER_IMOTIONS
        );

        currentProject = e.getProject();  // needed only if possibly enabled
        super.update(e);  // from ToggleAction
    }

    /**
     * Sets the selected state of the action to the specified value.
     *
     * @param e     the action event which caused the state change.
     * @param state the newly selected state of the action.
     */
    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        EyeTracker.createNotification(state ? "Auto tracking enabled" : "Auto tracking disabled");
        isAutoTracking = state;

        if (isAutoTracking) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                boolean isServerStarted = TcpCheck.isServerAvailable("127.0.0.1", 8088, 500);
                if (!isServerStarted) {
                    EyeTracker.createNotification("it looks like the server is not started");
                }
                EventQueue.invokeLater(new Thread(() -> {
                    try {
                        EyeTracker.createNotification("stimuliWatcher.start() from new thread...");
                        shouldBeTracking = false;
                        stimuliWatcher.start();
                    } catch (IOException ex) {
                        EyeTracker.createNotification("IOException from stimuliWatcher.start():<br>\n" + ex.getMessage() +
                                "<br>\n" + "turning off auto-tracking");
                        shouldBeTracking = false;
                        isAutoTracking = false;
                    }
                }));
//                try {
//                    Thread.sleep(10000);
//                    EyeTracker.createNotification("slept 10000 ms with Thread.sleep()");
//                } catch (InterruptedException ex) {
//                    EyeTracker.createNotification("InterruptedException after Thread.sleep()");
//                } finally {
//                    EyeTracker.createNotification("after Thread.sleep()");
//                }
            });
        } else {
            try {
                stimuliWatcher.stop();
            } catch (IOException ex) {
                EyeTracker.createNotification("IOException from stimuliWatcher.stop():\n<br>" + ex.getMessage());
            }
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;  // BGT (background thread) or EDT (event-dispatch thread)
    }
}
