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

package edu.wisc.ssec.mcidasv.chooser;

import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import org.w3c.dom.Element;

import edu.wisc.ssec.mcidasv.Constants;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.Position;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.TextColor;
import edu.wisc.ssec.mcidasv.util.McVGuiUtils.Width;

import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.chooser.IdvChooser;
import ucar.unidata.idv.chooser.IdvChooserManager;
import ucar.unidata.ui.ChooserPanel;
import ucar.unidata.util.FileManager;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.PatternFileFilter;
import ucar.unidata.util.TwoFacedObject;
import ucar.unidata.xml.XmlUtil;

/**
 * {@code FileChooser} is another {@literal "UI nicety"} extension. The main
 * difference is that this class allows {@code choosers.xml} to specify a
 * boolean attribute, {@code "selectdatasourceid"}. If disabled or not present,
 * a {@code FileChooser} will behave exactly like a standard 
 * {@link FileChooser}.
 * 
 * <p>If the attribute is present and enabled, the {@code FileChooser}'s 
 * data source type will automatically select the 
 * {@link ucar.unidata.data.DataSource} corresponding to the chooser's 
 * {@code "datasourceid"} attribute.
 */
public class FileChooser extends ucar.unidata.idv.chooser.FileChooser implements Constants {

    /** 
     * Chooser attribute that controls selecting the default data source.
     * @see #selectDefaultDataSource
     */
    public static final String ATTR_SELECT_DSID = "selectdatasourceid";

    /** Default data source ID for this chooser. Defaults to {@code null}. */
    private final String defaultDataSourceId;

    /** 
     * Whether or not to select the data source corresponding to 
     * {@link #defaultDataSourceId} within the {@link JComboBox} returned by
     * {@link #getDataSourcesComponent()}. Defaults to {@code false}.
     */
    private final boolean selectDefaultDataSource; 

    /**
     * If there is a default data source ID, get the combo box display value
     */
    private String defaultDataSourceName;
    
    /**
     * Get a handle on the actual file chooser
     */
    protected JFileChooser fileChooser;
    
    /**
     * Extending classes may need to manipulate the path
     */
    protected String path;
    
    /**
     * Get a handle on the IDV
     */
    private IntegratedDataViewer idv = getIdv();
    
    /**
     * Creates a {@code FileChooser} and bubbles up {@code mgr} and 
     * {@code root} to {@link FileChooser}.
     * 
     * @param mgr Global IDV chooser manager.
     * @param root XML representing this chooser.
     */
    public FileChooser(final IdvChooserManager mgr, final Element root) {
        super(mgr, root);

        String id = XmlUtil.getAttribute(root, ATTR_DATASOURCEID, (String)null);
        defaultDataSourceId = (id != null) ? id.toLowerCase() : id;

        selectDefaultDataSource =
            XmlUtil.getAttribute(root, ATTR_SELECT_DSID, false);
        
    }

    /**
     * Overridden so that McIDAS-V can attempt auto-selecting the default data
     * source type.
     */
    @Override protected JComboBox getDataSourcesComponent() {
        JComboBox comboBox = getDataSourcesComponent(true);
        if (selectDefaultDataSource && defaultDataSourceId != null) {
            Map<String, Integer> ids = comboBoxContents(comboBox);
            if (ids.containsKey(defaultDataSourceId)) {
                comboBox.setSelectedIndex(ids.get(defaultDataSourceId));
                defaultDataSourceName = comboBox.getSelectedItem().toString();
                comboBox.setVisible(false);
            }
        }
        return comboBox;
    }

    /**
     * Maps data source IDs to their index within {@code box}. This method is 
     * only applicable to {@link JComboBox}es created for {@link FileChooser}s.
     * 
     * @param box Combo box containing relevant data source IDs and indices. 
     * 
     * @return A mapping of data source IDs to their offset within {@code box}.
     */
    private static Map<String, Integer> comboBoxContents(final JComboBox box) {
        assert box != null;
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (int i = 0; i < box.getItemCount(); i++) {
            Object o = box.getItemAt(i);
            if (!(o instanceof TwoFacedObject))
                continue;
            TwoFacedObject tfo = (TwoFacedObject)o;
            map.put(TwoFacedObject.getIdString(tfo), i);
        }
        return map;
    }
    
    /**
     * Get the checkbox for allowing directory selection
     *
     * @return  the checkbox
     */
    protected JCheckBox getAllowDirectorySelectionCbx() {
        if (allowDirectorySelectionCbx == null) {
            allowDirectorySelectionCbx =
                new JCheckBox("Allow Directory Selection");
            allowDirectorySelectionCbx.setToolTipText(
                "<html><p>Select this if you want</p><p>be able to choose a directory.</p></html>");
            allowDirectorySelectionCbx.addActionListener(
                new ActionListener() {
                public void actionPerformed(ActionEvent ae) {
                    if (allowDirectorySelectionCbx.isSelected()) {
                        fileChooser.setFileSelectionMode(
                            JFileChooser.FILES_AND_DIRECTORIES);
                    } else {
                        fileChooser.setFileSelectionMode(
                            JFileChooser.FILES_ONLY);
                    }
                }
            });
        }
        return allowDirectorySelectionCbx;

    }
    
    /**
     * Get the accessory component
     *
     * @return the component
     */
    protected JComponent getAccessory() {
        return GuiUtils.left(
            GuiUtils.inset(
                FileManager.makeDirectoryHistoryComponent(
                    fileChooser, false), new Insets(13, 0, 0, 0)));
    }

    /**
     * Override the base class method to catch the do load
     */
    public void doLoadInThread() {
        selectFiles(fileChooser.getSelectedFiles(),
                    fileChooser.getCurrentDirectory());
    }

    /**
     * Override the base class method to catch the do update
     */
    public void doUpdate() {
        fileChooser.rescanCurrentDirectory();
    }
    
    /**
     * Allow multiple file selection.  Override if necessary.
     */
    protected boolean getAllowMultiple() {
    	return true;
    }
    
    /**
     * Get the top components for the chooser
     *
     * @param comps  the top component
     */
    protected void getTopComponents(List comps) {
       	Element chooserNode = getXmlNode();

       	// Force ATTR_DSCOMP to be false before calling super.getTopComponents
       	// We call getDataSourcesComponent later on
       	boolean dscomp = XmlUtil.getAttribute(chooserNode, ATTR_DSCOMP, true);
    	XmlUtil.setAttributes(chooserNode, new String[] { ATTR_DSCOMP, "false" });
    	super.getTopComponents(comps);
    	if (dscomp) XmlUtil.setAttributes(chooserNode, new String[] { ATTR_DSCOMP, "true" });
    }
    
    /**
     * Get the top panel for the chooser
     * @return the top panel
     */
    protected JPanel getTopPanel() {
        List   topComps  = new ArrayList();
        getTopComponents(topComps);
        if (topComps.size() == 0) return null;
        JPanel topPanel = GuiUtils.left(GuiUtils.doLayout(topComps, 0, GuiUtils.WT_N, GuiUtils.WT_N));
        topPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        
        return McVGuiUtils.makeLabeledComponent("Options:", topPanel);
    }
    
    /**
     * Get the bottom panel for the chooser
     * @return the bottom panel
     */
    protected JPanel getBottomPanel() {
    	return null;
    }
    
    private JLabel statusLabel = new JLabel("Status");

    @Override
    public void setStatus(String statusString, String foo) {
    	if (statusString == null)
    		statusString = "";
    	statusLabel.setText(statusString);
    }
    
    /**
     * Create a more McIDAS-V-like GUI layout
     */
    protected JComponent doMakeContents() {
    	// Run super.doMakeContents()
    	// It does some initialization on private components that we can't get at
    	JComponent parentContents = super.doMakeContents();
       	Element chooserNode = getXmlNode();
    	
        path = (String) idv.getPreference(PREF_DEFAULTDIR + getId());
        if (path == null) {
            path = XmlUtil.getAttribute(chooserNode, ATTR_PATH, (String) null);
        }

        fileChooser = doMakeFileChooser(path);
        fileChooser.setPreferredSize(new Dimension(300, 300));
        fileChooser.setMultiSelectionEnabled(getAllowMultiple());
        fileChooser.setApproveButtonText(ChooserPanel.CMD_LOAD);

        List filters = new ArrayList();
        String filterString = XmlUtil.getAttribute(chooserNode, ATTR_FILTERS, (String) null);

        filters.addAll(getDataManager().getFileFilters());
        if (filterString != null) {
            filters.addAll(PatternFileFilter.createFilters(filterString));
        }

        if ( !filters.isEmpty()) {
            for (int i = 0; i < filters.size(); i++) {
                fileChooser.addChoosableFileFilter((FileFilter) filters.get(i));
            }
            fileChooser.setFileFilter(fileChooser.getAcceptAllFileFilter());
        }
        
        JComponent typeComponent = new JPanel();
        if (XmlUtil.getAttribute(chooserNode, ATTR_DSCOMP, true)) {
        	typeComponent = getDataSourcesComponent();
        }
        if (defaultDataSourceName != null) {
        	typeComponent = new JLabel(defaultDataSourceName);
        	McVGuiUtils.setLabelBold((JLabel)typeComponent, true);
        	McVGuiUtils.setComponentHeight(typeComponent, new JComboBox());
        }
                
        JComponent chooserPanel;
        JComponent accessory = getAccessory();
        if (accessory == null) {
            chooserPanel = fileChooser;
        } else {
            chooserPanel = GuiUtils.centerRight(fileChooser, GuiUtils.top(accessory));
        }
        chooserPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        JPanel innerPanel = McVGuiUtils.makeLabeledComponent("Files:", chooserPanel);
        
        // If we have any top components, add them
        List   topComps  = new ArrayList();
        getTopComponents(topComps);
        JPanel topPanel = getTopPanel();
        JPanel bottomPanel = getBottomPanel();
        
        if (topPanel!=null && bottomPanel!=null)
        	innerPanel = McVGuiUtils.topCenterBottom(topPanel, innerPanel, bottomPanel);
        else if (topPanel!=null) 
        	innerPanel = McVGuiUtils.topBottom(topPanel, innerPanel, McVGuiUtils.Prefer.BOTTOM);
        else if (bottomPanel!=null)
        	innerPanel = McVGuiUtils.topBottom(innerPanel, bottomPanel, McVGuiUtils.Prefer.TOP);
        
        // Start building the whole thing here
    	JPanel outerPanel = new JPanel();

        JLabel typeLabel = McVGuiUtils.makeLabelRight("Data Type:");
        JLabel directoryLabel = McVGuiUtils.makeLabelRight("Directory:");    	    	
        JLabel fileLabel = McVGuiUtils.makeLabelRight("File List:");    	    	
            	
        JLabel statusLabelLabel = McVGuiUtils.makeLabelRight("");
        
        if (getAllowMultiple())
        	statusLabel.setText("Select one or more files");
        else
        	statusLabel.setText("Select a file");
        McVGuiUtils.setLabelPosition(statusLabel, Position.RIGHT);
        McVGuiUtils.setComponentColor(statusLabel, TextColor.STATUS);
        
        JButton helpButton = McVGuiUtils.makeImageButton("/edu/wisc/ssec/mcidasv/resources/icons/toolbar/show-help22.png", "Show help");
        helpButton.setActionCommand(GuiUtils.CMD_HELP);
        helpButton.addActionListener(this);
        
        JButton refreshButton = McVGuiUtils.makeImageButton("/edu/wisc/ssec/mcidasv/resources/icons/toolbar/view-refresh22.png", "Refresh");
        refreshButton.setActionCommand(GuiUtils.CMD_UPDATE);
        refreshButton.addActionListener(this);
        
        McVGuiUtils.setComponentWidth(loadButton, Width.DOUBLE);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(outerPanel);
        outerPanel.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.TRAILING)
                    .add(layout.createSequentialGroup()
                        .addContainerGap()
                        .add(helpButton)
                        .add(GAP_RELATED)
                        .add(refreshButton)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(loadButton))
                        .add(org.jdesktop.layout.GroupLayout.LEADING, layout.createSequentialGroup()
                        .addContainerGap()
                        .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                            .add(innerPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .add(layout.createSequentialGroup()
                                .add(typeLabel)
                                .add(GAP_RELATED)
                                .add(typeComponent))
                            .add(layout.createSequentialGroup()
                                .add(statusLabelLabel)
                                .add(GAP_RELATED)
                                .add(statusLabel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
            	.addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(typeLabel)
                    .add(typeComponent))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(innerPanel, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(statusLabelLabel)
                    .add(statusLabel))
                .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
                    .add(loadButton)
                    .add(refreshButton)
                    .add(helpButton))
                .addContainerGap())
        );
    
        return outerPanel;

    }
}