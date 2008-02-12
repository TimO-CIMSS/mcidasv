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


import org.w3c.dom.Element;


import ucar.unidata.idv.chooser.IdvChooserManager;

import ucar.unidata.idv.chooser.adde.*;

import ucar.unidata.ui.symbol.StationModel;
import ucar.unidata.ui.symbol.StationModelManager;

import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Misc;
import ucar.unidata.util.PreferenceList;
import ucar.unidata.util.TwoFacedObject;

import java.awt.*;
import java.awt.event.*;

import java.beans.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;


/**
 * Selection widget for ADDE point data
 *
 * @author MetApps Development Team
 * @version $Revision$ $Date$
 */
public class AddeRaobPointDataChooser extends AddePointDataChooser {


    /** list of levels */
    private static String[] levels = {
        "SFC", "1000", "925", "850", "700", "500", "400", "300", "250", "200",
        "150", "100", "70", "50", "30", "20", "10"
    };

    /** flag for selecting 00 and 12Z data only */
    private boolean zeroAndTwelveZOnly = true;


    /**
     * Create a new <code>AddeRaobPointDataChooser</code> with the preferred
     * list of ADDE servers.
     *
     *
     *
     * @param mgr The chooser manager
     * @param root The chooser.xml node
     */
    public AddeRaobPointDataChooser(IdvChooserManager mgr, Element root) {
        super(mgr, root);
    }




    /**
     * Get the extra time widget.  Subclasses can add their own time
     * widgets.
     *
     * @return a widget that can be selected for more options
     */
    protected JComponent getExtraTimeComponent() {
        JCheckBox timeSubset = GuiUtils.makeCheckbox("00 & 12Z only", this,
                                   "zeroAndTwelveZOnly");
        return timeSubset;
    }

    /**
     * Get the default station model for this chooser.
     * @return name of default station model
     */
    public String getDefaultStationModel() {
        return "observations>upper air";
    }

    /**
     * Does this chooser support level selection
     *
     * @return true
     */
    public boolean canDoLevels() {
        return true;
    }

    /**
     * This allows derived classes to provide their own name for labeling, etc.
     *
     * @return  the dataset name
     */
    public String getDataName() {
        return "RAOB Point Data";
    }

    /**
     * Get the request string for times particular to this chooser
     *
     * @return request string
     */
    protected String getTimesRequest() {
        StringBuffer buf = getGroupUrl(REQ_POINTDATA, getGroup());
        appendKeyValue(buf, PROP_DESCR, getDescriptor());
        // this is hokey, but take a smattering of stations.  
        //appendKeyValue(buf, PROP_SELECT, "'CO US'");
        if (getZeroAndTwelveZOnly()) {
            appendKeyValue(buf, PROP_SELECT, "'TIME 00,12'");
        }
        appendKeyValue(buf, PROP_USER, user);
        appendKeyValue(buf, PROP_PROJ, proj);
        appendKeyValue(buf, PROP_POS, "0");
        appendKeyValue(buf, PROP_NUM, "ALL");
        appendKeyValue(buf, PROP_PARAM, "DAY TIME");
        return buf.toString();
    }

    /**
     * Get the default datasets for the chooser.  The objects are
     * a descriptive name and the ADDE group/descriptor
     *
     * @return  default datasets.
     */
    protected TwoFacedObject[] getDefaultDatasets() {
        return new TwoFacedObject[] {
            new TwoFacedObject("Upper Air Data", "RTPTSRC/UPPERMAND") };
    }

    /**
     * Get the increment between times for relative time requests
     *
     * @return time increment (hours)
     */
    public float getRelativeTimeIncrement() {
        return getZeroAndTwelveZOnly()
               ? 12
               : 3;
    }

    /**
     * Get whether we should show 00 and 12Z times only.
     * @return true if only 00 and 12Z obs
     */
    public boolean getZeroAndTwelveZOnly() {
        return zeroAndTwelveZOnly;
    }

    /**
     * Set whether we should show 00 and 12Z times only.
     * @param value true if only 00 and 12Z obs
     */
    public void setZeroAndTwelveZOnly(boolean value) {
        zeroAndTwelveZOnly = value;
        if (getDoAbsoluteTimes()) {
            readTimes();
        }
    }

    /**
     * Get the list of possible levels for this chooser.
     * @return list of levels;
     */
    public List getLevels() {
        return Misc.toList(levels);
    }
}

