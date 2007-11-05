package edu.wisc.ssec.mcidasv.chooser;


import ucar.unidata.beans.NonVetoableProperty;
import ucar.unidata.beans.Property;
import edu.wisc.ssec.mcidasv.data.AddeSoundingAdapter;
import ucar.unidata.data.sounding.RAOB;
import ucar.unidata.data.sounding.SoundingAdapter;
import ucar.unidata.data.sounding.SoundingOb;
import ucar.unidata.data.sounding.SoundingStation;


import ucar.unidata.gis.mcidasmap.McidasMap;

import ucar.unidata.idv.chooser.IdvChooser;

import ucar.unidata.metdata.Station;



import ucar.unidata.ui.ChooserPanel;
import ucar.unidata.idv.chooser.adde.AddeChooser;
import ucar.unidata.idv.chooser.adde.AddeServer;

import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.PreferenceList;
import ucar.unidata.view.CompositeRenderer;
import ucar.unidata.view.geoloc.NavigatedPanel;
import ucar.unidata.view.station.StationLocationMap;

import visad.DateTime;

import visad.FlatField;



import java.awt.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.swing.*;

import javax.swing.border.EtchedBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;


/**
 *  This is the class that can be used for selecting soundings from a
 *  upperair netCDF data file.  The variables that are to be used
 *  are configurable.
 *
 *  @author Don Murray Unidata/UCAR
 *  @version $Id$
 */
public class SoundingSelector extends ChooserPanel {

    /** _more_ */
    private IdvChooser idvChooser;

    /** This is a virtual timestamp that tracks if the threaded adde connection should be aborted or not */
    private int connectionStep = 0;

    /** Server property identifier */
    private static String CMD_SERVER = "cmd.server";

    /** list of servers */
    private PreferenceList servers;

    /** input for file name */
    private JTextField selectedFileDisplay;

    /** list of times */
    private JList timesList;

    /** list of observations */
    private JList obsList;

    /** selected observations */
    private Vector selectedObs = new Vector();

    /** station location map */
    private StationLocationMap stationMap;

    /** current directory */
    private String directory = null;

    /** current file name */
    private String filename = null;

    /** frame for the display */
    private static JFrame frame;

    /** file browser */
    private SoundingFileBrowser fileBrowser = null;


    /** _more_          */
    AddeChooser addeChooser;

    /** dataset group selector */
    private JComboBox groupSelector;

    /** selected file */
    private File selectedFile;

    /** declutter flag */
    private boolean declutter = true;

    /** sounding adapter used by this seleccor */
    SoundingAdapter soundingAdapter;

    /** flag for allowing multiple selections */
    private boolean multipleSelect = false;

    /** flag for server vs. file */
    private boolean forServer = true;

    /** flag for server vs. file */
    private boolean showMainHoursOnly = true;


    /**
     * Construct an object for selecting sounding files starting at
     * the current directory and from a default ADDE server
     *
     * @param servers  list of servers
     */
    public SoundingSelector(PreferenceList servers) {
        this(servers, true, false);
    }


    /**
     * Construct an object for selecting sounding files starting at
     * the current directory and from a default ADDE server, set the
     * multipleSelect flag to the given value
     *
     * @param servers  list of servers
     * @param forServer   true for server vs. file
     * @param multipleSelect  true to select multiple stations
     */
    public SoundingSelector(PreferenceList servers, boolean forServer,
                            boolean multipleSelect) {
        this(servers, ".", "", forServer, multipleSelect);
    }


    /**
     * Construct an object for selecting sounding files starting at
     * the current directory and from a default ADDE server, set the
     * multipleSelect flag to the given value
     *
     *
     * @param idvChooser _more_
     * @param servers  list of servers
     * @param forServer   true for server vs. file
     * @param multipleSelect  true to select multiple stations
     */
    public SoundingSelector(IdvChooser idvChooser, PreferenceList servers,
                            boolean forServer, boolean multipleSelect) {
        this(idvChooser, servers, ".", "", forServer, multipleSelect);
    }




    /**
     * Construct an object for selecting sounding files starting at
     * the current directory and from a default ADDE server, set the
     * multipleSelect flag to the given value
     *
     * @param servers    list of servers
     * @param directoryName  directory for files
     * @param serverName  default server
     */
    public SoundingSelector(PreferenceList servers, String directoryName,
                            String serverName) {
        this(servers, directoryName, serverName, true, false);
    }


    /**
     * Construct an object for selecting sounding files starting at
     * the specified directory.
     *
     * @param servers          list of servers
     * @param directoryName    starting directory for files
     * @param serverName       default server
     * @param forServer        true for server vs. file
     * @param multipleSelect   true to select multiple stations
     */
    public SoundingSelector(PreferenceList servers, String directoryName,
                            String serverName, boolean forServer,
                            boolean multipleSelect) {

        this(null, servers, directoryName, serverName, forServer,
             multipleSelect);
    }


    /**
     * Construct an object for selecting sounding files starting at
     * the specified directory.
     *
     *
     * @param idvChooser _more_
     * @param servers          list of servers
     * @param directoryName    starting directory for files
     * @param serverName       default server
     * @param forServer        true for server vs. file
     * @param multipleSelect   true to select multiple stations
     */
    public SoundingSelector(IdvChooser idvChooser, PreferenceList servers,
                            String directoryName, String serverName,
                            boolean forServer, boolean multipleSelect) {

        this.idvChooser     = idvChooser;
        this.forServer      = forServer;
        this.servers        = servers;
        this.multipleSelect = multipleSelect;
        fileBrowser         = new SoundingFileBrowser(directoryName);
        fileBrowser.addPropertyChangeListener("soundingAdapter",
                new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                setSoundingAdapter((SoundingAdapter) evt.getNewValue());
            }
        });


    }

    /**
     * _more_
     *
     * @return _more_
     */
    protected JComponent doMakeContents() {
        // set the user interface
        // Top panel
        GuiUtils.tmpInsets = new Insets(2, 2, 2, 2);

        List servers =
            idvChooser.getIdv().getIdvChooserManager().getAddeServers(
                AddeServer.TYPE_ANY);
        addeChooser = new AddeChooser(idvChooser.getIdv().getIdvChooserManager(),null);
        groupSelector = GuiUtils.getEditableBox(Misc.newList("RTPTSRC"),
                null);

        JPanel selectorPanel;
        if (forServer) {
            JButton connectBtn = new JButton("Connect");
            connectBtn.addActionListener(this);
            connectBtn.setActionCommand(CMD_SERVER);
            JCheckBox mainHoursCbx = new JCheckBox("00 & 12Z only",
                                         showMainHoursOnly);
            mainHoursCbx.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    showMainHoursOnly =
                        ((JCheckBox) ev.getSource()).isSelected();
                    if (soundingAdapter != null) {
                        doUpdateInner(true);
                    }
                }
            });
            selectorPanel = GuiUtils.hbox(new Component[] {
                GuiUtils.rLabel("Server: "), addeChooser.getServerSelector(),
                GuiUtils.rLabel(" Group: "), groupSelector, GuiUtils.filler(),
                connectBtn, GuiUtils.filler(), mainHoursCbx
            });
        } else {
            selectorPanel = GuiUtils.hbox(GuiUtils.rLabel("File: "),
                                          fileBrowser.getContents());
        }
        selectorPanel = GuiUtils.inset(GuiUtils.leftCenter(selectorPanel,
                GuiUtils.filler()), 4);

        JPanel topPanel = selectorPanel;



        // Middle Panel
        JPanel middlePanel = new JPanel(new BorderLayout());

        obsList = new JList();
        obsList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                obsListClicked(e);
            }
        });
        JScrollPane obsPane = new JScrollPane(obsList);
        obsPane.setPreferredSize(new Dimension(175, 50));

        // Add the times panel
        JScrollPane timesPane = new JScrollPane(createTimesList());
        timesPane.setPreferredSize(new Dimension(175, 50));

        JPanel left = GuiUtils.doLayout(new Component[] {
                          new JLabel("Available Times:"),
                          timesPane, new JLabel("Selected Soundings:"),
                          obsPane }, 1, GuiUtils.WT_N, GuiUtils.WT_NYNY);

        middlePanel.add(GuiUtils.inset(left, 5), BorderLayout.WEST);

        // Add the station display panel
        JPanel p = new JPanel();

        p.setBorder(BorderFactory.createTitledBorder("Available Stations"));
        p.setLayout(new BorderLayout());

        CompositeRenderer mapRenderer = new CompositeRenderer();
        mapRenderer.addRenderer(new McidasMap("/auxdata/maps/OUTLSUPW"));
        mapRenderer.addRenderer(new McidasMap("/auxdata/maps/OUTLSUPU"));
        mapRenderer.setColor(MAP_COLOR);
        stationMap = new StationLocationMap(multipleSelect, mapRenderer);




        stationMap.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent pe) {
                if (pe.getPropertyName().equals(
                        StationLocationMap.SELECTED_PROPERTY)) {
                    stationSelected((Station) pe.getNewValue());
                } else if (pe.getPropertyName().equals(
                        StationLocationMap.UNSELECTED_PROPERTY)) {
                    stationUnselected((Station) pe.getNewValue());
                }
            }
        });

        /*
          NavigatedPanel np = stationMap.getNavigatedPanel();
          np.setPreferredSize(new Dimension(400, 300));
          JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
          JCheckBox declutCheck = createDeclutterCheckBox();
          toolPanel.add(declutCheck);
          JToolBar toolBar = np.getToolBar();
          toolPanel.add(toolBar);
          JPanel statusPanel = new JPanel(new BorderLayout());
          statusPanel.setBorder(new EtchedBorder());
          JLabel positionLabel = new JLabel("position");
          statusPanel.add(positionLabel, BorderLayout.CENTER);
          np.setPositionLabel(positionLabel);
          p.add(toolPanel, BorderLayout.NORTH);
          p.add(np, BorderLayout.CENTER);
          p.add(statusPanel, BorderLayout.SOUTH);
          middlePanel.add(p, BorderLayout.CENTER);
        */
        middlePanel.add(stationMap, BorderLayout.CENTER);
        JComponent buttons = getDefaultButtons();
        if (idvChooser != null) {
            buttons = idvChooser.decorateButtons(buttons);
        }

        return GuiUtils.topCenterBottom(topPanel, middlePanel, buttons);


    }



    /**
     * Handle a station selection
     *
     * @param station  selected station
     */
    private void stationSelected(Station station) {
        List selectedTimes = getSelectedTimes();
        if ((selectedTimes == null) || (selectedTimes.size() < 1)) {
            return;
        }
        for (int i = 0; i < selectedTimes.size(); i++) {
            DateTime dt = (DateTime) selectedTimes.get(i);
            List times =
                soundingAdapter.getSoundingTimes((SoundingStation) station);
            if ((times != null) && (times.size() > 0)) {
                if (times.contains(dt)) {
                    SoundingOb newObs = new SoundingOb(station, dt);
                    if ( !selectedObs.contains(newObs)) {
                        selectedObs.add(newObs);
                    }
                }
            }
        }
        obsList.setListData(selectedObs);
        checkLoadData();
    }

    /**
     * get the addechooser we use
     *
     * @return adde chooser
     */
    public AddeChooser getAddeChooser() {
        return addeChooser;
    }


    /**
     * check whether data has been loaded or not
     */
    private void checkLoadData() {
        setHaveData(obsList.getModel().getSize() > 0);
    }

    /**
     * Unselect a station
     *
     * @param station  station to unselect
     */
    private void stationUnselected(Station station) {
        List selectedTimes = getSelectedTimes();
        if ((selectedTimes == null) || (selectedTimes.size() < 1)) {
            return;
        }
        for (int i = 0; i < selectedTimes.size(); i++) {
            SoundingOb newObs = new SoundingOb(station,
                                    (DateTime) selectedTimes.get(i));
            if (selectedObs.contains(newObs)) {
                selectedObs.remove(newObs);
            }
        }
        obsList.setListData(selectedObs);
        checkLoadData();
    }


    /**
     * Get the server name.
     *
     * @return server name
     */
    private String getServer() {
        return addeChooser.getServer();
    }

    /**
     * Get the dataset (group) name.
     *
     * @return group name
     */
    private String getGroupName() {
        return groupSelector.getSelectedItem().toString();
    }

    /**
     * Get the mandatory dataset name.
     *
     * @return mandatory dataset name
     */
    private String getMandatoryDataset() {
        return getGroupName() + "/UPPERMAND";
    }

    /**
     * Get the sig level dataset name.
     *
     * @return sig level dataset name
     */
    private String getSigLevelDataset() {
        return getGroupName() + "/UPPERSIG";
    }


    /**
     * Handle the selection of an ob
     *
     * @param event  MouseEvent for selection
     */
    private void obsListClicked(MouseEvent event) {
        if ( !SwingUtilities.isRightMouseButton(event)) {
            return;
        }
        int index = obsList.locationToIndex(new Point(event.getX(),
                        event.getY()));
        if ((index < 0) || (index >= selectedObs.size())) {
            return;
        }

        final SoundingOb obs   = (SoundingOb) selectedObs.get(index);

        JPopupMenu       popup = new JPopupMenu();
        JMenuItem        mi;

        mi = new JMenuItem("Remove " + obs);
        mi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectedObs.remove(obs);
                obsList.setListData(selectedObs);
                checkLoadData();
                stationMap.setSelectedStations(getCurrentSelectedStations());
            }
        });

        popup.add(mi);

        mi = new JMenuItem("Remove all");
        mi.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                selectedObs.removeAllElements();
                obsList.setListData(selectedObs);
                checkLoadData();
                stationMap.setSelectedStations(getCurrentSelectedStations());
            }
        });

        popup.add(mi);

        popup.show(obsList, event.getX(), event.getY());
    }




    /**
     * Update the selector.
     */
    public void doUpdate() {
        if ( !forServer) {
            return;
        }
        doUpdateInner(false);
    }


    /**
     * Really update the selector.
     *
     * @param forceNewAdapter If true then create a new adapter.
     *                        Else, tell the existing one to update.
     */
    private void doUpdateInner(final boolean forceNewAdapter) {
        //Use the timestep so we can tell if the user hit connect again while 
        //we are waiting in this thread. If they do then we abort this one.
        final int timestep = ++connectionStep;
        Misc.run(new Runnable() {
            public void run() {
                clearWaitCursor();
                showWaitCursor();
                try {
                    if (forceNewAdapter) {
                        AddeSoundingAdapter newAdapter =
                            new AddeSoundingAdapter(getServer(),
                                getMandatoryDataset(), getSigLevelDataset(),
                                showMainHoursOnly);
                        if (timestep != connectionStep) {
                            return;
                        }
                        soundingAdapter = null;
                        setSoundingAdapter(newAdapter);
                    } else {
                        List times = getSelectedTimes();
                        soundingAdapter.update();
                        setStations();
                        setTimesListData(times);
                    }
                } catch (Exception exc) {
                    LogUtil.logException("Updating sounding data", exc);
                }
                showNormalCursor();
            }
        });

    }


    /**
     *  Gets called when the user presses Cancel in multipleSelect mode
     *  This can get overwritten by a derived class to do something.
     */
    public void doCancel() {}

    /**
     * Get the SoundingAdapter used by this selector.
     * @return the SoundingAdapter used by this selector.
     */
    public SoundingAdapter getSoundingAdapter() {
        return soundingAdapter;
    }

    /**
     * Set the SoundingAdapter used by this selector
     *
     * @param newAdapter   new adapter
     */
    protected void setSoundingAdapter(SoundingAdapter newAdapter) {
        soundingAdapter = newAdapter;
        selectedObs.removeAllElements();
        obsList.setListData(selectedObs);
        setStations();
        setTimesListData();
        checkLoadData();
    }

    /**
     * Create the list of times.
     *
     * @return List of times
     */
    private JList createTimesList() {
        timesList = new JList();
        timesList.setSelectionMode(
            ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        timesList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                if ( !timesList.isSelectionEmpty()
                        && !e.getValueIsAdjusting()) {
                    Object[] t = timesList.getSelectedValues();
                    newTimes(Misc.toList(t));
                }
            }
        });
        return timesList;
    }

    /**
     * Set the new times
     *
     * @param times new times to use
     */
    private void newTimes(List times) {
        List current = stationMap.getSelectedStations();
        if ((current == null) || (current.size() < 1)) {
            return;
        }
        selectedObs.removeAllElements();
        for (int i = 0; i < times.size(); i++) {
            DateTime dt = (DateTime) times.get(i);
            for (int j = 0; j < current.size(); j++) {
                SoundingStation ss      = (SoundingStation) current.get(j);
                List            ssTimes =
                    soundingAdapter.getSoundingTimes(ss);
                if ((ssTimes != null) && (times.size() > 0)) {
                    if (ssTimes.contains(dt)) {
                        SoundingOb newObs = new SoundingOb(ss, dt);
                        if ( !selectedObs.contains(newObs)) {
                            selectedObs.add(newObs);
                        }
                    }
                }
            }
        }
        obsList.setListData(selectedObs);
        checkLoadData();
    }

    /**
     * Get the current list of stations that are selected
     */
    private void setStations() {
        List current = getCurrentSelectedStations();
        stationMap.setStations(getSoundingAdapter().getStations(), current,
                               declutter);
        stationMap.redraw();
    }

    /**
     * Create a declutter checkbox.
     *
     * @return the checkbox to handle decluttering.
     */
    private JCheckBox createDeclutterCheckBox() {
        JCheckBox cb = new JCheckBox("Declutter", declutter);
        cb.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                declutter = ((JCheckBox) e.getSource()).isSelected();
                stationMap.setDeclutter(declutter);
            }
        });
        return cb;
    }



    /**
     * Handle actions.
     *
     * @param e  ActionEvent to handle.
     */
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (cmd.equals(CMD_SERVER)) {
            doUpdateInner(true);
            //            servers.saveState(serverSelector);
        } else {
            super.actionPerformed(e);
        }
    }


    /**
     *  This looks in the selectedList of SoundingOb-s for all stations
     *  that are selectged for the current time. It creates and returns
     *  a list of the Station-s held by these current SoundingOb-s
     *
     * @return list of currently selected stations
     */
    private List getCurrentSelectedStations() {
        List     current     = new ArrayList();
        DateTime currentTime = getSelectedTime();
        for (int i = 0; i < selectedObs.size(); i++) {
            SoundingOb ob = (SoundingOb) selectedObs.get(i);
            if (ob.getTimestamp().equals(currentTime)) {
                current.add(ob.getStation());
            }
        }
        return current;

    }

    /**
     * Set the data in the times list
     */
    private void setTimesListData() {
        setTimesListData(null);
    }

    /**
     * Set the data in the times list
     *
     * @param selected  a list of times that should be selected
     */
    private void setTimesListData(List selected) {
        DateTime[] times = getSoundingAdapter().getSoundingTimes();
        if (times != null) {
            timesList.setListData(times);
            if ((selected != null) && (selected.size() > 0)) {
                ListModel lm      = timesList.getModel();
                int[]     indices = new int[times.length];
                int       l       = 0;
                for (int i = 0; i < lm.getSize(); i++) {
                    if (selected.contains(lm.getElementAt(i))) {
                        indices[l++] = i;
                    }
                }
                if (l > 0) {
                    int[] selectedIndices = new int[l];
                    System.arraycopy(indices, 0, selectedIndices, 0, l);
                    timesList.setSelectedIndices(selectedIndices);
                    timesList.ensureIndexIsVisible(selectedIndices[l - 1]);
                } else {
                    timesList.setSelectedValue(times[times.length - 1], true);
                }
            } else if (times.length > 0) {
                timesList.setSelectedValue(times[times.length - 1], true);
            }
        } else {
            LogUtil.userMessage("No data available");
        }
    }

    /**
     * Test routine
     *
     * @param argv  not used
     */
    public static void main(String[] argv) {
        frame = new JFrame("Sounding Selector");
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        //JOptionPane.setRootFrame(frame);
        SoundingSelector ss =
            new SoundingSelector(
                new PreferenceList(Misc.newList("adde.ucar.edu")),
                "/var/data/ldm/decoded", "adde.ucar.edu", true, true);

        Container contentPane = frame.getContentPane();

        contentPane.add(ss.getContents(), BorderLayout.CENTER);

        // Add a close button
        JPanel  panel   = new JPanel(false);
        JButton dismiss = new JButton("Dismiss");

        dismiss.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        panel.add(dismiss);
        contentPane.add(panel, BorderLayout.SOUTH);
        frame.pack();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setLocation(screenSize.width / 2 - 320,
                          screenSize.height / 2 - 125);
        frame.setVisible(true);
    }


    /**
     * Get the selected time.
     *
     * @return the time selected in the list
     */
    public DateTime getSelectedTime() {
        return (DateTime) timesList.getSelectedValue();
    }

    /**
     * Get the selected time.
     *
     * @return the time selected in the list
     */
    public List getSelectedTimes() {
        return Misc.toList(timesList.getSelectedValues());
    }

    /**
     * Get the selected soundings
     *
     * @return List of selected soundings
     */
    public List getSelectedSoundings() {
        return selectedObs;
    }


}

