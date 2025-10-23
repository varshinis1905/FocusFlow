import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.TimerTask;
import java.util.Timer;
import java.util.ArrayList;

public class FocusFlow {
    private JFrame frame;
    private DefaultListModel<Task> taskModel;
    private JList<Task> taskList;
    private String dataFile = "data/tasks.txt";

    // Pomodoro state
    private JLabel timerLabel;
    private Timer timer;
    private int remainingSeconds = 25 * 60;
    private boolean running = false;
    private boolean onBreak = false;
    private int workDuration = 25; // minutes
    private int shortBreak = 5; // minutes
    private int longBreak = 15; // minutes
    private int sessionsBeforeLong = 4;
    private int completedSessions = 0;

    public FocusFlow() {
        initUI();
        loadTasks();
        updateTimerLabel();
    }

    private void initUI() {
        frame = new JFrame("FocusFlow");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(820, 520);
        frame.setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(8,8));
        main.setBorder(new EmptyBorder(10,10,10,10));
        frame.setContentPane(main);

        // Left: To-Do
        JPanel left = new JPanel(new BorderLayout(6,6));
        left.setPreferredSize(new Dimension(360, 0));
        JLabel ttitle = new JLabel("FocusFlow — Tasks");
        ttitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        left.add(ttitle, BorderLayout.NORTH);

        taskModel = new DefaultListModel<>();
        taskList = new JList<>(taskModel);
        taskList.setCellRenderer(new DefaultListCellRenderer());
        JScrollPane scroll = new JScrollPane(taskList);
        left.add(scroll, BorderLayout.CENTER);

        JPanel taskControls = new JPanel();
        taskControls.setLayout(new BoxLayout(taskControls, BoxLayout.Y_AXIS));
        JTextField taskField = new JTextField();
        JButton addBtn = new JButton("Add Task");
        JButton delBtn = new JButton("Delete");
        JButton toggleBtn = new JButton("Toggle Done");
        JButton saveBtn = new JButton("Save Tasks");
        taskControls.add(new JLabel("New task:"));
        taskControls.add(taskField);
        taskControls.add(Box.createRigidArea(new Dimension(0,6)));
        taskControls.add(addBtn);
        taskControls.add(Box.createRigidArea(new Dimension(0,6)));
        taskControls.add(toggleBtn);
        taskControls.add(Box.createRigidArea(new Dimension(0,6)));
        taskControls.add(delBtn);
        taskControls.add(Box.createRigidArea(new Dimension(0,6)));
        taskControls.add(saveBtn);
        left.add(taskControls, BorderLayout.SOUTH);

        // Right: Pomodoro
        JPanel right = new JPanel(new BorderLayout(6,6));
        JLabel ptitle = new JLabel("Pomodoro Timer");
        ptitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        right.add(ptitle, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(10,10,10,10));

        timerLabel = new JLabel("", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Monospaced", Font.BOLD, 44));
        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(timerLabel);
        center.add(Box.createRigidArea(new Dimension(0,12)));

        JPanel controlRow = new JPanel(new FlowLayout());
        JButton startBtn = new JButton("Start");
        JButton pauseBtn = new JButton("Pause");
        JButton resetBtn = new JButton("Reset");
        controlRow.add(startBtn);
        controlRow.add(pauseBtn);
        controlRow.add(resetBtn);
        center.add(controlRow);

        center.add(Box.createRigidArea(new Dimension(0,12)));
        JPanel settings = new JPanel();
        settings.setLayout(new GridLayout(0,2,6,6));
        settings.add(new JLabel("Work (min):"));
        JSpinner workSpinner = new JSpinner(new SpinnerNumberModel(workDuration,1,120,1));
        settings.add(workSpinner);
        settings.add(new JLabel("Short Break (min):"));
        JSpinner shortSpinner = new JSpinner(new SpinnerNumberModel(shortBreak,1,60,1));
        settings.add(shortSpinner);
        settings.add(new JLabel("Long Break (min):"));
        JSpinner longSpinner = new JSpinner(new SpinnerNumberModel(longBreak,1,60,1));
        settings.add(longSpinner);
        settings.add(new JLabel("Sessions before long:"));
        JSpinner sessionsSpinner = new JSpinner(new SpinnerNumberModel(sessionsBeforeLong,1,10,1));
        settings.add(sessionsSpinner);

        center.add(settings);
        center.add(Box.createRigidArea(new Dimension(0,12)));

        JCheckBox soundCheck = new JCheckBox("Sound alerts", true);
        JCheckBox themeToggle = new JCheckBox("Light theme");
        center.add(soundCheck);
        center.add(themeToggle);

        right.add(center, BorderLayout.CENTER);

        // Footer: session info
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel sessionInfo = new JLabel("Completed sessions: 0");
        footer.add(sessionInfo);
        right.add(footer, BorderLayout.SOUTH);

        main.add(left, BorderLayout.WEST);
        main.add(right, BorderLayout.CENTER);

        // Actions
        addBtn.addActionListener(e -> {
            String t = taskField.getText().trim();
            if (t.isEmpty()) return;
            Task task = new Task(t, false);
            taskModel.addElement(task);
            taskField.setText(""); 
        });
        delBtn.addActionListener(e -> {
            int i = taskList.getSelectedIndex();
            if (i>=0) taskModel.remove(i);
        });
        toggleBtn.addActionListener(e -> {
            int i = taskList.getSelectedIndex();
            if (i>=0) {
                Task t = taskModel.get(i);
                t.setDone(!t.isDone());
                taskModel.set(i, t);
            }
        });
        saveBtn.addActionListener(e -> {
            saveTasks();
            JOptionPane.showMessageDialog(frame, "Tasks saved.");
        });

        startBtn.addActionListener(e -> {
            if (running) return;
            workDuration = (int)workSpinner.getValue();
            shortBreak = (int)shortSpinner.getValue();
            longBreak = (int)longSpinner.getValue();
            sessionsBeforeLong = (int)sessionsSpinner.getValue();
            if (!onBreak) {
                remainingSeconds = workDuration * 60;
            } else {
                remainingSeconds = (completedSessions % sessionsBeforeLong == 0) ? longBreak*60 : shortBreak*60;
            }
            startTimer(soundCheck.isSelected(), sessionInfo);
        });

        pauseBtn.addActionListener(e -> {
            stopTimer();
        });

        resetBtn.addActionListener(e -> {
            stopTimer();
            onBreak = false;
            remainingSeconds = workDuration * 60;
            updateTimerLabel();
        });

        themeToggle.addActionListener(e -> {
            boolean light = themeToggle.isSelected();
            applyTheme(light);
            SwingUtilities.updateComponentTreeUI(frame);
        });

        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                saveTasks();
            }
        });

        applyTheme(false);
        frame.setVisible(true);
    }

    private void startTimer(boolean sound, JLabel sessionInfo) {
        running = true;
        if (timer != null) timer.cancel();
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    remainingSeconds--;
                    if (remainingSeconds <= 0) {
                        timerCompleted(sound, sessionInfo);
                    }
                    updateTimerLabel();
                });
            }
        }, 0, 1000);
    }

    private void timerCompleted(boolean sound, JLabel sessionInfo) {
        stopTimer();
        if (!onBreak) {
            completedSessions++;
            if (sound) Toolkit.getDefaultToolkit().beep();
            onBreak = true;
            if (completedSessions % sessionsBeforeLong == 0) remainingSeconds = longBreak * 60;
            else remainingSeconds = shortBreak * 60;
            JOptionPane.showMessageDialog(frame, "Work session complete! Time for a break.");
        } else {
            if (sound) Toolkit.getDefaultToolkit().beep();
            onBreak = false;
            remainingSeconds = workDuration * 60;
            JOptionPane.showMessageDialog(frame, "Break finished! Back to work.");
        }
        sessionInfo.setText("Completed sessions: " + completedSessions);
    }

    private void stopTimer() {
        running = false;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void updateTimerLabel() {
        int s = remainingSeconds;
        int mm = s / 60;
        int ss = s % 60;
        timerLabel.setText(String.format("%02d:%02d", mm, ss));
    }

    private void applyTheme(boolean light) {
        if (light) {
            UIManager.put("Panel.background", Color.decode("#f6f7fb"));
            UIManager.put("Label.foreground", Color.decode("#111111"));
            UIManager.put("Button.background", Color.decode("#eaeef6"));
            UIManager.put("TextField.background", Color.decode("#ffffff"));
            UIManager.put("List.background", Color.decode("#ffffff"));
        } else {
            UIManager.put("Panel.background", Color.decode("#2e2e2e"));
            UIManager.put("Label.foreground", Color.decode("#eaeaea"));
            UIManager.put("Button.background", Color.decode("#444444"));
            UIManager.put("TextField.background", Color.decode("#3b3b3b"));
            UIManager.put("List.background", Color.decode("#333333"));
        }
    }

    private void loadTasks() {
        try {
            Path p = Paths.get(dataFile);
            if (!Files.exists(p)) {
                Files.createDirectories(p.getParent());
                Files.createFile(p);
                return;
            }
            BufferedReader br = Files.newBufferedReader(p);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Task t = new Task(line);
                taskModel.addElement(t);
            }
            br.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void saveTasks() {
        try {
            Path p = Paths.get(dataFile);
            Files.createDirectories(p.getParent());
            BufferedWriter bw = Files.newBufferedWriter(p);
            for (int i=0;i<taskModel.size();i++) {
                Task t = taskModel.get(i);
                bw.write(t.toCSV());
                bw.newLine();
            }
            bw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.put("Label.font", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Button.font", new Font("Segoe UI", Font.PLAIN, 13));
        } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new FocusFlow());
    }
}