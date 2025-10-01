package actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
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
                        break;
                    case "stop":
                        EyeTracker.createNotification("stimuliWatcher: Stimulus presentation stopped");
                        break;
                    case "close":
                        EyeTracker.createNotification("stimuliWatcher: iMotions Lab server disconnected");
                        break;
                    case "failed":
                        EyeTracker.createNotification("stimuliWatcher: iMotions Lab server connection failed");
                        break;
                }
            });

    /**
     * Returns the selected (checked, pressed) state of the action.
     *
     * @param e the action event representing the place and context in which the selected state is queried.
     * @return true if the action is selected, false otherwise
     */
    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        return isAutoTracking;
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
                        stimuliWatcher.start();
                    } catch (IOException ex) {
                        EyeTracker.createNotification("IOException from stimuliWatcher.start():<br>\n" + ex.getMessage() +
                                "<br>\n" + "turning off auto-tracking");
                        isAutoTracking = false;
                    }
                }));
                try {
                    Thread.sleep(10000);
                    EyeTracker.createNotification("slept 10000 ms with Thread.sleep()");
                } catch (InterruptedException ex) {
                    EyeTracker.createNotification("InterruptedException after Thread.sleep()");
                } finally {
                    EyeTracker.createNotification("after Thread.sleep()");
                }
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
        return ActionUpdateThread.BGT;  // or EDT
    }
}
