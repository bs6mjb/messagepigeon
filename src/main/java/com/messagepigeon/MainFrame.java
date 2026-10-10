package com.messagepigeon;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.*;

/** Profile sidebar on the left, one tab per open queue on the right. */
public final class MainFrame extends JFrame {
    private static final String EMPTY = "empty";
    private static final String TABS = "tabs";

    private final ProfileStore store;
    private final List<ConnectionProfile> profiles = new ArrayList<>();
    private final DefaultListModel<ConnectionProfile> listModel = new DefaultListModel<>();
    private final JList<ConnectionProfile> list = new JList<>(listModel);
    private final Map<ConnectionProfile, Connection> connections = new HashMap<>();
    private final Set<ConnectionProfile> connecting = new HashSet<>();
    private final JTabbedPane tabs = new JTabbedPane();
    private final CardLayout centreLayout = new CardLayout();
    private final JPanel centre = new JPanel(centreLayout);

    public MainFrame(ProfileStore store) {
        super(Ui.TITLE);
        this.store = store;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                List<Connection> open = new ArrayList<>(connections.values());
                open.forEach(Connection::close);
                open.forEach(c -> c.awaitClose(2000));
            }
        });

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new ProfileRenderer());
        list.setToolTipText("Right-click for options. Double-click to open a queue.");
        installListMenu();

        JButton add = new JButton("+ New Profile");
        add.addActionListener(e -> editProfile(null));
        JPanel side = new JPanel(new BorderLayout());
        JLabel heading = new JLabel("Profiles");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        side.add(heading, BorderLayout.NORTH);
        side.add(new JScrollPane(list), BorderLayout.CENTER);
        side.add(add, BorderLayout.SOUTH);
        side.setMinimumSize(new Dimension(160, 100));

        JLabel hint = new JLabel("<html><div style='text-align:center'>Right-click a profile and choose "
                + "<b>Open Queue...</b><br>to view a queue in a tab.</div></html>", SwingConstants.CENTER);
        hint.setForeground(Color.GRAY);
        centre.add(hint, EMPTY);
        centre.add(tabs, TABS);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, centre);
        split.setDividerLocation(230);
        add(split, BorderLayout.CENTER);
        setSize(1300, 780);
        setLocationRelativeTo(null);

        setIconImages(AppIcon.images());
        createActions();
        setJMenuBar(buildMenuBar());
        add(buildToolBar(), BorderLayout.NORTH);
        tabs.addChangeListener(e -> updateMenus());
        loadProfiles();
        updateCentre(); // also sets which actions are enabled
    }

    // ---- actions, menu bar and icon bar ---------------------------------------------------------------------

    private static final String GLYPH = "glyph";

    /** Actions that only make sense while a queue tab is open; the menu items and icon bar buttons follow them. */
    private final List<Action> tabActions = new ArrayList<>();
    private Action newProfileAction;
    private Action openQueueAction;
    private Action closeTabAction;
    private Action disconnectAllAction;
    private Action exitAction;
    private Action refreshAction;
    private Action insertAction;
    private Action addFileAction;
    private Action addFolderAction;
    private Action exportAction;
    private Action importAction;
    private Action deleteSelectedAction;
    private Action deleteAllAction;
    private Action helpAction;
    private Action libraryAction;
    private Action aboutAction;

    private Action action(String text, MenuIcons.Glyph glyph, String tip, KeyStroke key, Runnable run) {
        AbstractAction a = new AbstractAction(text, MenuIcons.of(glyph)) {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                run.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, key == null ? tip : tip + " (" + describe(key) + ")");
        if (key != null) {
            a.putValue(Action.ACCELERATOR_KEY, key);
        }
        a.putValue(GLYPH, glyph);
        return a;
    }

    private Action tabAction(String text, MenuIcons.Glyph glyph, String tip, KeyStroke key, Runnable run) {
        Action a = action(text, glyph, tip, key, run);
        tabActions.add(a);
        return a;
    }

    private static String describe(KeyStroke k) {
        String mods = java.awt.event.InputEvent.getModifiersExText(k.getModifiers());
        String key = java.awt.event.KeyEvent.getKeyText(k.getKeyCode());
        return mods.isEmpty() ? key : mods + "+" + key;
    }

    private void createActions() {
        int ctrl = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        newProfileAction = action("New Profile...", MenuIcons.Glyph.NEW_PROFILE, "Create a connection profile",
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_N, ctrl), () -> editProfile(null));
        openQueueAction = action("Open Queue...", MenuIcons.Glyph.OPEN_QUEUE,
                "Open a queue on the selected profile", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, ctrl),
                this::openQueueForSelection);
        closeTabAction = tabAction("Close Tab", MenuIcons.Glyph.CLOSE_TAB, "Close the current queue tab",
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_W, ctrl), this::closeCurrentTab);
        disconnectAllAction = action("Disconnect All", MenuIcons.Glyph.DISCONNECT,
                "Disconnect from every queue manager and close all tabs", null, this::disconnectAll);
        exitAction = action("Exit", MenuIcons.Glyph.EXIT, "Exit " + Ui.TITLE,
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, ctrl),
                () -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        refreshAction = tabAction("Refresh", MenuIcons.Glyph.REFRESH, "Read the current queue again",
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F5, 0), () -> withTab(t -> t.browse(null)));
        insertAction = tabAction("Insert Message...", MenuIcons.Glyph.INSERT_MESSAGE, "Add one message by typing it",
                null, () -> withTab(QueueTab::insertMessage));
        addFileAction = tabAction("Add From File...", MenuIcons.Glyph.ADD_FILE,
                "Add one message per line of a text file", null, () -> withTab(QueueTab::addFromFile));
        addFolderAction = tabAction("Add From Folder...", MenuIcons.Glyph.ADD_FOLDER,
                "Add one message per file in a folder", null, () -> withTab(QueueTab::addFromFolder));
        exportAction = tabAction("Export to File...", MenuIcons.Glyph.EXPORT,
                "Export to File - save every message on the queue to a file", null,
                () -> withTab(QueueTab::fullExport));
        importAction = tabAction("Import from File...", MenuIcons.Glyph.IMPORT,
                "Import from File - add the messages from an export file to the queue", null,
                () -> withTab(QueueTab::importFile));
        deleteSelectedAction = tabAction("Delete Selected...", MenuIcons.Glyph.TRASH,
                "Remove the highlighted messages from the queue", null, () -> withTab(QueueTab::deleteSelected));
        deleteAllAction = tabAction("Delete All Messages...", MenuIcons.Glyph.TRASH_ALL,
                "Remove every message from the queue", null, () -> withTab(QueueTab::clearQueue));
        helpAction = action("Help Contents", MenuIcons.Glyph.HELP, "Open the help",
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0), () -> HelpWindow.show(this));
        libraryAction = action("IBM MQ Client Library...", MenuIcons.Glyph.LIBRARY,
                "Show or change the copy of IBM's MQ client library in use", null, this::chooseLibrary);
        aboutAction = action("About " + Ui.TITLE, MenuIcons.Glyph.ABOUT, "Version and Java details", null,
                () -> new AboutDialog(this).setVisible(true));
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(menuItem(newProfileAction));
        file.add(menuItem(openQueueAction));
        file.add(menuItem(closeTabAction));
        file.addSeparator();
        file.add(menuItem(disconnectAllAction));
        file.addSeparator();
        file.add(menuItem(exitAction));

        JMenu queue = new JMenu("Queue");
        queue.setMnemonic('Q');
        queue.add(menuItem(refreshAction));
        queue.addSeparator();
        queue.add(menuItem(insertAction));
        queue.add(menuItem(addFileAction));
        queue.add(menuItem(addFolderAction));
        queue.addSeparator();
        queue.add(menuItem(exportAction));
        queue.add(menuItem(importAction));
        queue.addSeparator();
        queue.add(menuItem(deleteSelectedAction));
        queue.add(menuItem(deleteAllAction));

        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(menuItem(helpAction));
        help.add(menuItem(libraryAction));
        help.addSeparator();
        help.add(menuItem(aboutAction));

        bar.add(file);
        bar.add(queue);
        bar.add(help);
        return bar;
    }

    /** The row of icons under the menu bar: the same actions as the menus, one click away. */
    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setRollover(true);
        bar.add(tool(newProfileAction));
        bar.add(tool(openQueueAction));
        bar.add(tool(disconnectAllAction));
        bar.addSeparator();
        bar.add(tool(refreshAction));
        bar.add(tool(closeTabAction));
        bar.addSeparator();
        bar.add(tool(insertAction));
        bar.add(tool(addFileAction));
        bar.add(tool(addFolderAction));
        bar.addSeparator();
        bar.add(tool(exportAction));
        bar.add(tool(importAction));
        bar.addSeparator();
        bar.add(tool(deleteSelectedAction));
        bar.add(tool(deleteAllAction));
        bar.addSeparator();
        bar.add(tool(helpAction));
        return bar;
    }

    /** A menu item for an action, with a faded icon for when it is disabled. */
    private static JMenuItem menuItem(Action a) {
        JMenuItem mi = new JMenuItem(a);
        mi.setDisabledIcon(MenuIcons.faded((MenuIcons.Glyph) a.getValue(GLYPH)));
        return mi;
    }

    /** An icon-only button for an action: larger icon, the action's description as its tooltip. */
    private static JButton tool(Action a) {
        JButton b = new JButton(a);
        b.setHideActionText(true);
        b.setIcon(MenuIcons.of((MenuIcons.Glyph) a.getValue(GLYPH), 24));
        b.setFocusable(false);
        b.setMargin(new Insets(4, 6, 4, 6));
        return b;
    }

    private void updateMenus() {
        boolean has = tabs.getTabCount() > 0;
        for (Action a : tabActions) {
            a.setEnabled(has);
        }
    }

    private void withTab(Consumer<QueueTab> action) {
        if (tabs.getSelectedComponent() instanceof QueueTab t) {
            action.accept(t);
        }
    }

    /** Help > IBM MQ Client Library...: shows which copy is in use and lets the user point at a different one. */
    private void chooseLibrary() {
        String current = System.getProperty(MqLibrary.PROPERTY, "(on the class path)");
        java.nio.file.Path remembered = MqLibrary.remembered(MqLibrary.settingsDir());
        String message = "IBM MQ client library in use:\n" + current
                + (remembered != null && !remembered.toString().equals(current)
                        ? "\n\nRemembered for next time:\n" + remembered : "")
                + "\n\nChoose a different copy? It takes effect the next time " + Ui.TITLE + " starts.";
        Object[] options = {"Choose another...", "Close"};
        if (JOptionPane.showOptionDialog(this, message, "IBM MQ client library", JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]) != 0) {
            return;
        }
        java.nio.file.Path start = remembered != null ? remembered
                : java.nio.file.Path.of(System.getProperty("user.home"));
        java.nio.file.Path chosen = MqLibrary.choose(this, MqLibrary.settingsDir(), Ui.TITLE, start);
        if (chosen != null) {
            Ui.info(this, "IBM MQ Client Library", "Saved:\n" + chosen + "\n\nRestart " + Ui.TITLE + " to use it.");
        }
    }

    private void closeCurrentTab() {
        Component c = tabs.getSelectedComponent();
        if (c != null) {
            tabs.remove(c);
            updateCentre();
        }
    }

    private void openQueueForSelection() {
        ConnectionProfile p = list.getSelectedValue();
        if (p == null) {
            Ui.info(this, "Open Queue", "Select a profile in the sidebar first.");
            return;
        }
        openQueue(p);
    }

    private void disconnectAll() {
        for (ConnectionProfile p : new ArrayList<>(connections.keySet())) {
            disconnect(p);
        }
    }

    // ---- sidebar --------------------------------------------------------------------------------------------

    private void installListMenu() {
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                popup(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    ConnectionProfile p = profileAt(e.getPoint());
                    if (p != null) {
                        openQueue(p);
                    }
                }
            }

            private void popup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                ConnectionProfile p = profileAt(e.getPoint());
                if (p != null) {
                    list.setSelectedValue(p, true);
                } else {
                    list.clearSelection();
                }
                buildMenu(p).show(list, e.getX(), e.getY());
            }
        });
    }

    /** The profile under the point, or null if the click was on empty space. */
    private ConnectionProfile profileAt(Point pt) {
        int i = list.locationToIndex(pt);
        if (i < 0 || !list.getCellBounds(i, i).contains(pt)) {
            return null;
        }
        return listModel.get(i);
    }

    private JPopupMenu buildMenu(ConnectionProfile p) {
        JPopupMenu m = new JPopupMenu();
        if (p != null) {
            boolean on = connections.containsKey(p);
            m.add(item("Open Queue...", MenuIcons.Glyph.OPEN_QUEUE, () -> openQueue(p)));
            if (on) {
                m.add(item("Disconnect", MenuIcons.Glyph.DISCONNECT, () -> disconnect(p)));
            } else {
                JMenuItem connect = item("Connect", MenuIcons.Glyph.CONNECT, () -> ensureConnected(p, null));
                connect.setEnabled(!connecting.contains(p));
                m.add(connect);
            }
            JMenuItem history = item("Queue History...", MenuIcons.Glyph.CLEAR_HISTORY, () -> showHistory(p));
            history.setEnabled(!p.recent().isEmpty());
            m.add(history);
            m.addSeparator();
        }
        m.add(item("New Profile...", MenuIcons.Glyph.NEW_PROFILE, () -> editProfile(null)));
        if (p != null) {
            m.add(item("Edit Profile...", MenuIcons.Glyph.EDIT, () -> editProfile(p)));
            m.add(item("Duplicate Profile", MenuIcons.Glyph.COPY, () -> duplicate(p)));
            m.add(item("Delete Profile", MenuIcons.Glyph.TRASH, () -> deleteProfile(p)));
        }
        return m;
    }

    private static JMenuItem item(String text, MenuIcons.Glyph glyph, Runnable action) {
        JMenuItem i = new JMenuItem(text, MenuIcons.of(glyph));
        i.setDisabledIcon(MenuIcons.faded(glyph));
        i.addActionListener(e -> action.run());
        return i;
    }

    // ---- profiles -------------------------------------------------------------------------------------------

    private void loadProfiles() {
        try {
            profiles.addAll(store.load());
        } catch (Exception e) {
            Ui.error(this, "Could not read saved profiles", e);
        }
        profiles.forEach(listModel::addElement);
    }

    private void persistProfiles() {
        try {
            store.save(profiles);
        } catch (Exception e) {
            Ui.error(this, "Could not save profiles", e);
        }
    }

    private void editProfile(ConnectionProfile existing) {
        if (existing != null && connections.containsKey(existing)) {
            if (!Ui.confirm(this, "Edit Profile", "'" + existing.name + "' is connected. Editing it will disconnect and close its tabs.")) {
                return;
            }
            disconnect(existing);
        }
        ConnectionProfile base = existing == null ? new ConnectionProfile() : existing;
        new ProfileDialog(this, base, saved -> {
            if (existing == null) {
                profiles.add(saved);
                listModel.addElement(saved);
            } else {
                int i = profiles.indexOf(existing);
                profiles.set(i, saved);
                listModel.set(i, saved);
            }
            persistProfiles();
            list.setSelectedValue(saved, true);
        }).setVisible(true);
    }

    private void duplicate(ConnectionProfile p) {
        ConnectionProfile c = p.copy();
        c.name = p.name + " (copy)";
        profiles.add(c);
        listModel.addElement(c);
        persistProfiles();
    }

    private void deleteProfile(ConnectionProfile p) {
        if (!Ui.confirm(this, "Delete Profile", "Delete profile '" + p.name + "'?")) {
            return;
        }
        disconnect(p);
        profiles.remove(p);
        listModel.removeElement(p);
        persistProfiles();
    }

    // ---- connections and tabs -------------------------------------------------------------------------------

    /** Connects if needed (asking for the password when none is stored), then runs {@code then} on the UI thread. */
    private void ensureConnected(ConnectionProfile p, Consumer<Connection> then) {
        Connection existing = connections.get(p);
        if (existing != null) {
            if (then != null) {
                then.accept(existing);
            }
            return;
        }
        if (connecting.contains(p)) {
            return;
        }
        ConnectionProfile use = p.copy();
        if (!use.userId.isBlank() && use.password.isEmpty()) {
            JPasswordField pw = new JPasswordField(20);
            if (JOptionPane.showConfirmDialog(this, pw, "Password for " + use.userId + " on " + p.name,
                    JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return;
            }
            use.password = new String(pw.getPassword());
        }
        Connection c = new Connection(p);
        connecting.add(p);
        list.repaint();
        c.worker.execute(() -> {
            try {
                c.mq.connect(use);
                SwingUtilities.invokeLater(() -> {
                    connecting.remove(p);
                    connections.put(p, c);
                    list.repaint();
                    if (then != null) {
                        then.accept(c);
                    }
                });
            } catch (Throwable t) {
                c.worker.shutdown();
                SwingUtilities.invokeLater(() -> {
                    connecting.remove(p);
                    list.repaint();
                    Ui.error(this, "Could not connect to " + p.name, t);
                });
            }
        });
    }

    private void disconnect(ConnectionProfile p) {
        Connection c = connections.remove(p);
        if (c == null) {
            return;
        }
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (((QueueTab) tabs.getComponentAt(i)).connection() == c) {
                tabs.removeTabAt(i);
            }
        }
        c.close();
        updateCentre();
        list.repaint();
    }

    private void openQueue(ConnectionProfile p) {
        String queue = askQueue(p);
        if (queue == null) {
            return;
        }
        ensureConnected(p, conn -> {
            p.rememberQueue(queue);
            persistProfiles();
            for (int i = 0; i < tabs.getTabCount(); i++) {
                QueueTab t = (QueueTab) tabs.getComponentAt(i);
                if (t.connection() == conn && t.queueName().equals(queue)) {
                    tabs.setSelectedIndex(i);
                    t.browse(null);
                    return;
                }
            }
            QueueTab tab = new QueueTab(this, conn, queue);
            tabs.addTab(null, tab);
            int idx = tabs.indexOfComponent(tab);
            tabs.setTabComponentAt(idx, tabHeader(tab, queue + "  [" + p.name + "]"));
            tabs.setSelectedIndex(idx);
            updateCentre();
            tab.browse(null);
        });
    }

    private String askQueue(ConnectionProfile p) {
        JComboBox<String> box = new JComboBox<>(p.recent().toArray(new String[0]));
        box.setEditable(true);
        box.setPrototypeDisplayValue("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
        box.setSelectedItem(p.lastQueue);
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Queue name on " + p.queueManager + " (" + p.name + "):"), BorderLayout.NORTH);
        panel.add(box, BorderLayout.CENTER);
        if (JOptionPane.showConfirmDialog(this, panel, "Open Queue", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return null;
        }
        Object v = box.getEditor().getItem();
        String q = v == null ? "" : v.toString().trim();
        if (q.isEmpty()) {
            Ui.info(this, "Open Queue", "Enter a queue name.");
            return null;
        }
        return q;
    }

    /** Right-click > Queue History...: a grid of the profile's remembered queues to remove from or clear. */
    private void showHistory(ConnectionProfile p) {
        new QueueHistoryDialog(this, p, this::persistProfiles).setVisible(true);
    }

    private JComponent tabHeader(QueueTab tab, String title) {
        JPanel h = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        h.setOpaque(false);
        h.add(new JLabel(title));
        JButton x = new JButton("×");
        x.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        x.setContentAreaFilled(false);
        x.setFocusable(false);
        x.setToolTipText("Close this tab");
        x.addActionListener(e -> {
            tabs.remove(tab);
            updateCentre();
        });
        h.add(x);
        return h;
    }

    private void updateCentre() {
        centreLayout.show(centre, tabs.getTabCount() == 0 ? EMPTY : TABS);
        updateMenus();
    }

    // ---- list rendering -------------------------------------------------------------------------------------

    /** Shows each profile with a green dot when connected, amber while connecting. */
    private final class ProfileRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected,
                boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(l, value, index, selected, focus);
            ConnectionProfile p = (ConnectionProfile) value;
            Color dot = connections.containsKey(p) ? new Color(0x2E9E4F)
                    : connecting.contains(p) ? new Color(0xE0A100) : new Color(0xB0B0B0);
            label.setIcon(new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    g.setColor(dot);
                    g.fillOval(x, y, 10, 10);
                }

                @Override
                public int getIconWidth() {
                    return 10;
                }

                @Override
                public int getIconHeight() {
                    return 10;
                }
            });
            label.setIconTextGap(8);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setToolTipText(p.userId.isBlank() ? p.host + ":" + p.port + "  " + p.queueManager
                    : p.userId + "@" + p.host + ":" + p.port + "  " + p.queueManager + " / " + p.channel);
            return label;
        }
    }
}
