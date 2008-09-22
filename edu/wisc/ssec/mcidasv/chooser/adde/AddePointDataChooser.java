/*
 * $Id$
 *
 * Copyright 2007-2008
 * Space Science and Engineering Center (SSEC)
 * University of Wisconsin - Madison,
 * 1225 W. Dayton Street, Madison, WI 53706, USA
 *
 * http://www.ssec.wisc.edu/mcidas
 *
 * This file is part of McIDAS-V.
 * 
 * McIDAS-V is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * McIDAS-V is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with this program.  If not, see http://www.gnu.org/licenses
 */

package edu.wisc.ssec.mcidasv.chooser.adde;


import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.w3c.dom.Element;

import ucar.unidata.data.AddeUtil;
import ucar.unidata.data.point.AddePointDataSource;
import ucar.unidata.idv.chooser.IdvChooserManager;
import ucar.unidata.idv.chooser.adde.AddeServer;
import ucar.unidata.ui.symbol.StationModel;
import ucar.unidata.ui.symbol.StationModelManager;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.TwoFacedObject;
import ucar.visad.UtcDate;

import visad.DateTime;

import edu.wisc.ssec.mcidas.McIDASUtil;
import edu.wisc.ssec.mcidas.adde.AddePointDataReader;


/**
 * Selection widget for ADDE point data
 *
 * @author MetApps Development Team
 * @version $Revision$ $Date$
 */
public class AddePointDataChooser extends AddeChooser {

    /**
     * Property for the dataset name key.
     * @see #getDatasetName()
     */
    public static String DATASET_NAME_KEY = "name";

    /** Label for data type */
    protected static final String LABEL_DATATYPE = "Data Type:";

    /** Property for the data type. */
    public static String DATA_TYPE = "ADDE.POINT";

    /** UI widget for selecting data types */
    protected JComboBox dataTypes;

    /** UI widget for selecting station models */
    protected JComboBox stationModelBox;

    /** a selector for a particular level */
    protected JComboBox levelBox = null;

    /** label for METAR data */
    private static final String METAR = "Surface (METAR) Data";

    /** label for synoptic data */
    private static final String SYNOPTIC = "Synoptic Data";

    /** station model manager */
    private StationModelManager stationModelManager;

    /** Property for the number of times */
    public static String LEVELS = "data levels";

    /** Property for the time increment */
    public static String SELECTED_LEVEL = "selected level";

    /** box for the relative time */
    private JComboBox relTimeIncBox;

    /** box for the relative time */
    private JComponent relTimeIncComp;

    /** the relative time increment */
    private float relativeTimeIncrement = 1.f;


    /** Accounting information */
    protected static String user = "idv";
    protected static String proj = "0";

    protected boolean firstTime = true;
    protected boolean retry = true;

    /**
     * Create a chooser for Adde POINT data
     *
     * @param mgr The chooser manager
     * @param root The chooser.xml node
     */
    public AddePointDataChooser(IdvChooserManager mgr, Element root) {
        super(mgr, root);
        init(getIdv().getStationModelManager());
    }

    /**
     * init
     *
     * @param stationModelManager station model manager
     */
    private void init(StationModelManager stationModelManager) {
        this.stationModelManager = stationModelManager;
        Vector stationModels =
            new Vector(stationModelManager.getStationModels());
        stationModelBox = new JComboBox(stationModels);
        //Try to default to 
        for (int i = 0; i < stationModels.size(); i++) {
            if (stationModels.get(i).toString().equalsIgnoreCase(
                    getDefaultStationModel())) {
                stationModelBox.setSelectedItem(stationModels.get(i));
                break;
            }
        }

        dataTypes =
            GuiUtils.getEditableBox(Misc.toList(getDefaultDatasets()), null);

        dataTypes.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent a) {
                setState(STATE_UNCONNECTED);
                String currentType = dataTypes.getSelectedItem().toString();
                if (currentType.equals(SYNOPTIC)) {
                    setRelativeTimeIncrement(3);
                } else {
                    setRelativeTimeIncrement(1);
                }
            }
        });
        if (canDoLevels()) {
            levelBox = GuiUtils.getEditableBox(getLevels(), null);
        }
    }

    /**
     * Make the contents for this chooser
     *
     * @return  a panel with the UI
     */
    protected JComponent doMakeContents() {

        JComponent lastPanel = stationModelBox;
        if (canDoLevels()) {
            lastPanel = GuiUtils.hbox(Misc.newList(stationModelBox,
                    new JLabel("   Level: "), levelBox), GRID_SPACING);
        }

//        List allComps = new ArrayList();
        List allComps = processServerComponents();
        clearOnChange(dataTypes);
        
        JPanel timesComp = makeTimesPanel();
        allComps.add(GuiUtils.top(addServerComp(GuiUtils.rLabel(LABEL_DATATYPE))));
        allComps.add(GuiUtils.left(addServerComp(dataTypes)));
        allComps.add(addServerComp(GuiUtils.rLabel(LABEL_TIMES)));
        allComps.add(addServerComp(GuiUtils.left(timesComp)));
        allComps.add(addServerComp(GuiUtils.rLabel("Layout Model: ")));
        allComps.add(addServerComp(GuiUtils.left(lastPanel)));

        GuiUtils.tmpInsets = GRID_INSETS;
        JComponent top = GuiUtils.doLayout(allComps, 2, GuiUtils.WT_NN,
                                           GuiUtils.WT_N);

        return GuiUtils.topLeft(GuiUtils.centerBottom(top, getDefaultButtons()));
    }

    
    /**
     * Get the default display type
     *
     * @return the default control for automatic display
     */
    protected String getDefaultDisplayType() {
        return "stationmodelcontrol";
    }

    /**
     * Load in an ADDE point data set based on the
     * <code>PropertyChangeEvent<code>.
     *
     */
    public void doLoadInThread() {
        showWaitCursor();
        try {
            StationModel selectedStationModel = getSelectedStationModel();
            String       source               = getRequestUrl();
            // make properties Hashtable to hand the station name
            // to the AddeProfilerDataSource
            Hashtable ht = new Hashtable();
            getDataSourceProperties(ht);
            ht.put(AddePointDataSource.PROP_STATIONMODELNAME,
                   selectedStationModel.getName());
            ht.put(DATASET_NAME_KEY, getDatasetName());
            ht.put(DATA_NAME_KEY, getDataName());
            if (source.indexOf(AddeUtil.RELATIVE_TIME) >= 0) {
                ht.put(AddeUtil.NUM_RELATIVE_TIMES, getRelativeTimeIndices());
                ht.put(AddeUtil.RELATIVE_TIME_INCREMENT,
                       new Float(getRelativeTimeIncrement()));
            }
            if (source.indexOf(AddeUtil.LEVEL) >= 0) {
                ht.put(LEVELS, getLevels());
                ht.put(SELECTED_LEVEL, getSelectedLevel());
            }

            //System.out.println("makeDataSource: source=" + source);
            //System.out.println("    DATA_TYPE=" + DATA_TYPE);
            //System.out.println("    ht=" + ht);
            makeDataSource(source, DATA_TYPE, ht);
            saveServerState();
        } catch (Exception excp) {
            logException("Unable to open ADDE point dataset", excp);
        }
        showNormalCursor();
    }





    /**
     * Add the 00 & 12Z checkbox to the component.
     * @return superclass component with extra stuff
     */
    protected JPanel makeTimesPanel() {
        return super.makeTimesPanel(true);
    }

    /**
     * Get the extra time widget.  Subclasses can add their own time
     * widgets.
     *
     * @return a widget that can be selected for more options
     */
    protected JComponent getExtraTimeComponent() {
        ActionListener listener = new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                JComboBox box = (JComboBox) ae.getSource();
                if (GuiUtils.anySelected(box)) {
                    setRelativeTimeIncrement(getRelBoxValue());
                }
            }
        };
        String[] nums = new String[] {
            ".5", "1", "3", "6", "12", "24"
        };
        float[]  vals = new float[] {
            .5f, 1f, 3f, 6f, 12f, 24f
        };
        List     l    = new ArrayList();
        for (int i = 0; i < nums.length; i++) {
            l.add(new TwoFacedObject(nums[i], new Float(vals[i])));
        }
        relTimeIncBox = GuiUtils.getEditableBox(l,
                new Float(relativeTimeIncrement));
        relTimeIncBox.addActionListener(listener);
        relTimeIncBox.setToolTipText(
            "Set the increment between most recent times");
        //        Dimension prefSize = relTimeIncBox.getPreferredSize();
        //        if(prefSize!=null) {
        //            relTimeIncBox.setPreferredSize(new Dimension(20,prefSize.height));
        //        }
        relTimeIncComp =
            GuiUtils.vbox(new JLabel("Relative Time Increment:"),
                          GuiUtils.hbox(relTimeIncBox,
                                        GuiUtils.lLabel(" hours")));
        return relTimeIncComp;
    }

    /**
     * Get the value from the relative increment box
     *
     * @return the seleted value or a default
     */
    private float getRelBoxValue() {
        float value = relativeTimeIncrement;
        if (relTimeIncBox != null) {
            Object o = relTimeIncBox.getSelectedItem();
            if (o != null) {
                String val = TwoFacedObject.getIdString(o);
                value = (float) Misc.parseNumber(val);
            }
        }
        return value;
    }

    /**
     * Get the selected station model.
     *
     * @return StationModel to use by default.
     */
    public StationModel getSelectedStationModel() {
        return (StationModel) stationModelBox.getSelectedItem();
    }


    /**
     * Return the currently selected descriptor form the combobox
     *
     * @return the currently selected descriptor
     */
    protected String getDescriptor() {
        String dataset =
            TwoFacedObject.getIdString(dataTypes.getSelectedItem());
        int index = dataset.indexOf('/');
        if (index == -1) {
            throw new IllegalArgumentException("Bad dataset: \"" + dataset
                    + "\"");
        }
        return dataset.substring(index + 1);
    }

    /**
     * Return the currently selected group form the combobox
     *
     * @return the currently selected group
     */
    protected String getGroup() {
        String dataset =
            TwoFacedObject.getIdString(dataTypes.getSelectedItem());
        int index = dataset.indexOf('/');
        if (index == -1) {
            throw new IllegalArgumentException("Bad dataset: \"" + dataset
                    + "\"");
        }
        return dataset.substring(0, index);
    }



    /**
     * Get the request URL
     *
     * @return  the request URL
     */
    public String getRequestUrl() {
        StringBuffer request = getGroupUrl(REQ_POINTDATA, getGroup());
        appendKeyValue(request, PROP_USER, user);
        appendKeyValue(request, PROP_PROJ, proj);
        appendKeyValue(request, PROP_DESCR, getDescriptor());
        appendRequestSelectClause(request);
        appendKeyValue(request, PROP_NUM, "all");
        //appendKeyValue(request, PROP_DEBUG, "true");
        appendKeyValue(request, PROP_POS, getDoRelativeTimes()
                                          ? "ALL"
                                          : "0");
        return request.toString();
    }


    /**
     * Get the list of possible levels for this chooser.
     * @return list of levels;
     */
    public List getLevels() {
        return new ArrayList();
    }

    /**
     * Get the selected level
     * @return the selected level
     */
    public Object getSelectedLevel() {
        if (levelBox != null) {
            return levelBox.getSelectedItem();
        } else {
            return null;
        }
    }


    /**
     * Get the select clause for the adde request specific to this
     * type of data.
     *
     * @param buf The buffer to append to
     */
    protected void appendRequestSelectClause(StringBuffer buf) {
        StringBuffer selectValue = new StringBuffer();
        selectValue.append("'");
        selectValue.append(getDayTimeSelectString());
        if (getDescriptor().equalsIgnoreCase("SFCHOURLY")) {
            selectValue.append(";type 0");
        }
        selectValue.append(";");
        if (canDoLevels()) {
            selectValue.append(AddeUtil.LEVEL);
            selectValue.append(";");
        }
        selectValue.append(AddeUtil.LATLON_BOX);
        selectValue.append("'");
        appendKeyValue(buf, PROP_SELECT, selectValue.toString());
    }

    /**
     * Does this chooser support level selection
     *
     * @return true if levels are supported by this chooser
     */
    public boolean canDoLevels() {
        return false;
    }


    /**
     * Update the widget with the latest data.
     *
     * @throws Exception On badness
     */
    @Override public void handleUpdate() throws Exception {
//        readTimes();
//        saveServerState();
//        updateServerList();
        readTimes();
        saveServerState();
    }


    /**
     * Get the request string for times particular to this chooser
     *
     * @return request string
     */
    protected String getTimesRequest() {
        StringBuffer buf = getGroupUrl(REQ_POINTDATA, getGroup());
        //System.out.println(buf);
        appendKeyValue(buf, PROP_USER, user);
        appendKeyValue(buf, PROP_PROJ, proj);
        appendKeyValue(buf, PROP_DESCR, getDescriptor());
        // this is hokey, but take a smattering of stations.  
        //buf.append("&select='ID KDEN'");
        appendKeyValue(buf, PROP_SELECT, "'LAT 39.5 40.5;LON 104.5 105.5'");
        appendKeyValue(buf, PROP_POS, "0");  // set to 0 for now
        if (getDoAbsoluteTimes()) {
            appendKeyValue(buf, PROP_NUM, "all");
        }
        appendKeyValue(buf, PROP_PARAM, "day time");
        return buf.toString();
    }


    /**
     * This allows derived classes to provide their own name for labeling, etc.
     *
     * @return  the dataset name
     */
    public String getDataName() {
        return "Point Data";
    }


    /**
     * Set the list of available times.
     */
    protected void readTimes() {
        clearTimesList();
        SortedSet uniqueTimes =
            Collections.synchronizedSortedSet(new TreeSet());
        setState(STATE_CONNECTING);
        try {
                        //System.err.println("TIMES:" + getTimesRequest());
            AddePointDataReader apr =
                new AddePointDataReader(getTimesRequest());
            int[][]  data  = apr.getData();
            String[] units = apr.getUnits();
            if ( !units[0].equals("CYD") || !units[1].equals("HMS")) {
                throw new Exception("can't handle date/time units");
            }
            int numObs = data[0].length;
            //System.out.println("found " + numObs + " obs");
            // loop through and find all the unique times
            for (int i = 0; i < numObs; i++) {
                try {
                    DateTime dt =
                        new DateTime(McIDASUtil.mcDayTimeToSecs(data[0][i],
                            data[1][i]));
                    uniqueTimes.add(dt);
                } catch (Exception e) {}
            }
            setState(STATE_CONNECTED);
            //System.out.println(
            //      "found " + uniqueTimes.size() + " unique times");
        } catch (Exception excp) {
            //System.out.println("I am here excp=" + excp);
            handleConnectionError(excp);
            if (retry == false) return;
            try {
                handleUpdate();
            } catch (Exception e) {
            }
        }
        if (getDoAbsoluteTimes()) {
            if ( !uniqueTimes.isEmpty()) {
                setAbsoluteTimes(new ArrayList(uniqueTimes));
            }
            int selectedIndex = getAbsoluteTimes().size() - 1;
            setSelectedAbsoluteTime(selectedIndex);
        }
    }

    protected int getNumTimesToSelect() {
        return 1;
    }

    /**
     * Are there any times selected.
     *
     * @return Any times selected.
     */
    protected boolean haveTimeSelected() {
        return !getDoAbsoluteTimes() || getHaveAbsoluteTimesSelected();
    }

    /**
     * Create the date time selection string for the "select" clause
     * of the ADDE URL.
     *
     * @return the select day and time strings
     */
    protected String getDayTimeSelectString() {
        StringBuffer buf = new StringBuffer();
        if (getDoAbsoluteTimes()) {
            buf.append("time ");
            List times = getSelectedAbsoluteTimes();
            for (int i = 0; i < times.size(); i++) {
                DateTime dt = (DateTime) times.get(i);
                buf.append(UtcDate.getHMS(dt));
                if (i != times.size() - 1) {
                    buf.append(",");
                }
            }
        } else {
            buf.append(getRelativeTimeId());
        }
        return buf.toString();
    }

    /**
     * Get the identifier for relative time.  Subclasses can override.
     * @return the identifier
     */
    protected String getRelativeTimeId() {
        return AddeUtil.RELATIVE_TIME;
    }

    /**
     * Get the name of the dataset.
     *
     * @return descriptive name of the dataset.
     */
    public String getDatasetName() {
        return dataTypes.getSelectedItem().toString();
    }

    /**
     * Get the data type for this chooser
     *
     * @return  the type
     */
    public String getDataType() {
        return "POINT";
    }


    /**
     * Get the increment between times for relative time requests
     *
     * @return time increment (hours)
     */
    public float getRelativeTimeIncrement() {
        return relativeTimeIncrement;
    }

    /**
     * Set the increment between times for relative time requests
     *
     * @param increment time increment (hours)
     */
    public void setRelativeTimeIncrement(float increment) {
        relativeTimeIncrement = increment;
        if (relTimeIncBox != null) {
            relTimeIncBox.setSelectedItem(new Float(relativeTimeIncrement));
        }
    }

    /**
     * Update labels, enable widgets, etc.
     */
    protected void updateStatus() {
        super.updateStatus();
        enableWidgets();
    }

    /**
     * Enable or disable the GUI widgets based on what has been
     * selected.
     */
    protected void enableWidgets() {
        super.enableWidgets();
        if (relTimeIncComp != null) {
            GuiUtils.enableTree(relTimeIncComp, getDoRelativeTimes());
        }

    }

    /**
     * Get an array of {@link TwoFacedObject}-s for the datasets.  The
     * two faces are the descriptive name and the actual group/descriptor
     *
     * @return   the default data sets
     */
    protected TwoFacedObject[] getDefaultDatasets() {
        return new TwoFacedObject[] {
            new TwoFacedObject(METAR, "RTPTSRC/SFCHOURLY"),
            new TwoFacedObject(SYNOPTIC, "RTPTSRC/SYNOPTIC") };
    }


    /**
     * Get the default station model for this chooser.
     * @return name of default station model
     */
    public String getDefaultStationModel() {
        return "observations>metar";
    }

    /**
     * Show the given error to the user. If it was an Adde exception
     * that was a bad server error then print out a nice message.
     *
     * @param excp The exception
     */
    protected void handleConnectionError(Exception excp) {
        //System.out.println("handleConnectionError:");
        String aes = excp.toString();
        if ((aes.indexOf("Accounting data")) >= 0) {
            JTextField projFld   = null;
            JTextField userFld   = null;
            JComponent contents  = null;
            JLabel     label     = null;
            if (firstTime == false) {
                retry = false;
            } else {
                if (projFld == null) {
                    projFld            = new JTextField("", 10);
                    userFld            = new JTextField("", 10);
                    GuiUtils.tmpInsets = GuiUtils.INSETS_5;
                    contents = GuiUtils.doLayout(new Component[] {
                        GuiUtils.rLabel("User ID:"),
                        userFld, GuiUtils.rLabel("Project #:"), projFld, }, 2,
                            GuiUtils.WT_N, GuiUtils.WT_N);
                    label    = new JLabel(" ");
                    contents = GuiUtils.topCenter(label, contents);
                    contents = GuiUtils.inset(contents, 5);
                }
                String lbl = (firstTime
                              ? "The server: " + getAddeServer("AddePointDataChooser.handleConnectionError 1").getName()
                                + " requires a user ID & project number for access"
                              : "Authentication for server: " + getAddeServer("AddePointDataChooser.handleConnectionError 2").getName()
                                + " failed. Please try again");
                label.setText(lbl);
                firstTime = false;

                if ( !GuiUtils.showOkCancelDialog(null, "ADDE Project/User name",
                        contents, null)) {
                    setState(STATE_UNCONNECTED);
                    return;
                }
                user = userFld.getText().trim();
                proj  = projFld.getText().trim();
            }
            return;
        }
        String message = excp.getMessage().toLowerCase();
        if (message.indexOf("with position 0") >= 0) {
            LogUtil.userErrorMessage("Unable to handle archive dataset");
            retry = false;
            return;
        }
        //super.handleConnectionError(excp);
    }
    
    /**
     * Get the descriptor widget label.
     *
     * @return  label for the descriptor  widget
     */
    @Override public String getDescriptorLabel() { 
        return "Data Type"; 
    }

    /**
     * get the adde server grup type to use
     *
     * @return group type
     */
    @Override protected String getGroupType() {
        return AddeServer.TYPE_POINT;
    }
}
