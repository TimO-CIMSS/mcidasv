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

package edu.wisc.ssec.mcidasv.data;


import edu.wisc.ssec.mcidas.AreaDirectory;
import edu.wisc.ssec.mcidas.AreaDirectoryList;
import edu.wisc.ssec.mcidasv.Constants;

import ucar.unidata.data.*;
import ucar.unidata.data.imagery.*;


import ucar.unidata.util.CacheManager;
import ucar.unidata.util.FileManager;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.IOUtil;
import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.PollingInfo;
import ucar.unidata.util.Range;
import ucar.unidata.util.StringUtil;

import ucar.unidata.util.TwoFacedObject;

import visad.*;

import ucar.visad.data.AddeImageFlatField;
import visad.data.mcidas.AreaAdapter;

import visad.meteorology.ImageSequence;
import visad.meteorology.ImageSequenceManager;
import visad.meteorology.SingleBandedImage;

import java.awt.*;
import java.awt.event.*;

import java.io.File;
import java.io.IOException;

import java.rmi.RemoteException;

import java.util.ArrayList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Hashtable;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.swing.*;
import javax.swing.event.*;


/**
 * Abstract DataSource class for images files.
 *
 * @author IDV development team
 * @version $Revision$ $Date$
 */
public abstract class TestImageDataSource extends DataSourceImpl {

    /** Type of image, radar or satellite. Set by the chooser to disambiguate between types */
    public static final String PROP_IMAGETYPE = "prop.imagetype";

    /** radar type */
    public static final String TYPE_RADAR = "radar";

    /** satellite type */
    public static final String TYPE_SATELLITE = "satellite";

    /** satellite type */
    public static final String PROP_BANDINFO = "bandinfo";

    /** list of twod categories */
    private List twoDCategories;

    /** list of 2D time series categories */
    private List twoDTimeSeriesCategories;

    /** list of twod categories */
    private List bandCategories;

    /** list of 2D time series categories */
    private List bandTimeSeriesCategories;

    /** list of images */
    protected List imageList;

    /** list of image times */
    protected List imageTimes = new ArrayList();

    /** sequence manager for displaying data */
     private ImageSequenceManager sequenceManager;

    /** My composite */
    private CompositeDataChoice myCompositeDataChoice;

    /** children choices */
    private List myDataChoices = new ArrayList();

    /** _more_ */
    private AreaDirectory[][] currentDirs;

    /**
     *  The parameterless constructor for unpersisting.
     */
    public TestImageDataSource() {}


    /**
     * Create a new TestImageDataSource with a list of (String) images. These
     * can either be AREA files or ADDE URLs.
     *
     * @param descriptor       The descriptor for this data source.
     * @param images           Array of  file anmes or urls.
     * @param properties       The properties for this data source.
     */
    public TestImageDataSource(DataSourceDescriptor descriptor, String[] images,
                           Hashtable properties) {
        super(descriptor, "Image data set", "Image data source", properties);
        if ( !initDataFromPollingInfo()) {
            setImageList(makeImageDescriptors(images));
        }
        setDescription(getImageDataSourceName());
    }


    /**
     * Create a new TestImageDataSource with a list of (String) images. These
     * can either be AREA files or ADDE URLs.
     *
     * @param descriptor       The descriptor for this data source.
     * @param images           Array of  file anmes or urls.
     * @param properties       The properties for this data source.
     */
    public TestImageDataSource(DataSourceDescriptor descriptor, List images,
                           Hashtable properties) {
        this(descriptor, StringUtil.listToStringArray(images), properties);
    }




    /**
     * Create a new TestImageDataSource with the given {@link ImageDataset}.
     * The dataset may hold eight AREA file filepaths or ADDE URLs.
     *
     * @param descriptor    The descriptor for this data source.
     * @param ids           The dataset.
     * @param properties    The properties for this data source.
     */
    public TestImageDataSource(DataSourceDescriptor descriptor, ImageDataset ids,
                           Hashtable properties) {
        super(descriptor, ids.getDatasetName(), "Image data source",
              properties);
        setImageList(new ArrayList(ids.getImageDescriptors()));
        setDescription(getImageDataSourceName());
    }

    /**
     * _more_
     *
     * @param newObject _more_
     * @param newProperties _more_
     */
    public void updateState(Object newObject, Hashtable newProperties) {
        super.updateState(newObject, newProperties);
        if (newObject instanceof ImageDataset) {
            ImageDataset ids = (ImageDataset) newObject;
            setImageList(new ArrayList(ids.getImageDescriptors()));
            setDescription(getImageDataSourceName());
        } else if (newObject instanceof List) {
            setTmpPaths((List) newObject);
        } else if (newObject instanceof String) {
            setTmpPaths(Misc.newList(newObject));
        }
    }

    /**
     * Get the paths for saving data files
     *
     * @return data paths
     */
    public List getDataPaths() {
        List paths = new ArrayList();
        for (int i = 0; i < imageList.size(); i++) {
            AddeImageDescriptor aid = getDescriptor(imageList.get(i));
            paths.add(aid.getSource());
        }
        return paths;
    }


    /**
     * Override the init method for when this data source is unpersisted.
     * We simply check the imageList to see if this object came from a
     * legacy bundle.
     */
    public void initAfterUnpersistence() {
        super.initAfterUnpersistence();
        List tmp = getTmpPaths();
        if (tmp != null) {
            imageList = new ArrayList();
            for (int i = 0; i < tmp.size(); i++) {
                imageList.add(new AddeImageDescriptor(tmp.get(i).toString()));
            }
        }
        if ((imageList != null) && (imageList.size() > 0)
                && (imageList.get(0) instanceof String)) {
            List tmpList = imageList;
            imageList = new ArrayList();
            for (int i = 0; i < tmpList.size(); i++) {
                imageList.add(
                    new AddeImageDescriptor(tmpList.get(i).toString()));
            }
        }
        initDataFromPollingInfo();
    }



    /**
     * Can this data source cache its
     *
     * @return can cache data to disk
     */
    public boolean canCacheDataToDisk() {
        return true;
    }




    /**
     * Is this data source capable of saving its data to local disk
     *
     * @return Can save to local disk
     */
    public boolean canSaveDataToLocalDisk() {
        if (isFileBased()) {
            return false;
        }
        List<BandInfo> bandInfos =
            (List<BandInfo>) getProperty(PROP_BANDINFO, (Object) null);
        if ((bandInfos == null) || (bandInfos.size() == 0)) {
            return true;
        }
        if (bandInfos.size() > 1) {
            return false;
        }
        List l = bandInfos.get(0).getCalibrationUnits();
        if (l.size() > 1) {
            return false;
        }
        return true;
    }

    /**
     * Save files to local disk
     *
     * @param prefix destination dir and file prefix
     * @param loadId For JobManager
     * @param changeLinks Change internal file references
     *
     * @return Files copied
     *
     * @throws Exception On badness
     */
    protected List saveDataToLocalDisk(String prefix, Object loadId,
                                       boolean changeLinks)
            throws Exception {
        List urls = new ArrayList();
        for (int i = 0; i < imageList.size(); i++) {
            AddeImageDescriptor aid = getDescriptor(imageList.get(i));
            urls.add(aid.getSource());
        }
        List newFiles = IOUtil.writeTo(urls, prefix, "area", loadId);
        if (newFiles == null) {
            return null;
        }
        if (changeLinks) {
            imageList = newFiles;
        }
        return newFiles;
    }






    /**
     * Method for intializing the data.
     *
     *
     * @return result
     */
    protected boolean initDataFromPollingInfo() {
        PollingInfo pollingInfo = getPollingInfo();
        if ( !pollingInfo.getForFiles()
                || !pollingInfo.doILookForNewFiles()) {
            return false;
        }
        imageList = new ArrayList();
        List files = pollingInfo.getFiles();
        for (int i = 0; i < files.size(); i++) {
            imageList.add(new AddeImageDescriptor(files.get(i).toString()));
        }
        return true;
    }


    /**
     * The user changed the properties. Update me.
     */
    protected void propertiesChanged() {
        PollingInfo pollingInfo = getPollingInfo();
        if (pollingInfo.doILookForNewFiles()) {
            List newSources = pollingInfo.getFiles();
            if (newSources.size() != imageList.size()) {
                initDataFromPollingInfo();
                dataChoices = null;
                notifyDataChange();
            }
        }
        super.propertiesChanged();
    }



    /**
     * Make an ImageDataset from an array of ADDE URLs or AREA file names
     *
     * @param addeURLs  array of ADDE URLs
     *
     * @return ImageDataset
     */
    public static ImageDataset makeImageDataset(String[] addeURLs) {
        AddeImageDescriptor[] aids = new AddeImageDescriptor[addeURLs.length];
        for (int i = 0; i < addeURLs.length; i++) {
            aids[i] = new AddeImageDescriptor(addeURLs[i]);
        }
        return new ImageDataset("Image data set", Arrays.asList(aids));
    }


    /**
     * Make a list of image descriptors
     *
     * @param addeURLs  array of ADDE URLs
     *
     * @return ImageDataset
     */
    public static List makeImageDescriptors(String[] addeURLs) {
        List descriptors = new ArrayList();
        for (int i = 0; i < addeURLs.length; i++) {
            descriptors.add(new AddeImageDescriptor(addeURLs[i]));
        }
        return descriptors;
    }



    /**
     * Get the location where we poll.
     *
     * @return Directory to poll on.
     */
    protected List getLocationsForPolling() {
        if ( !isFileBased()) {
            return null;
        }
        List files = new ArrayList();
        for (int i = 0; i < imageList.size(); i++) {
            AddeImageDescriptor aid = getDescriptor(imageList.get(i));
            files.add(aid.getSource());
        }
        return files;
    }


    /**
     * Are we getting images from a file or from adde
     *
     * @return is the data from files
     */
    protected boolean isFileBased() {
        if ((imageList == null) || (imageList.size() == 0)) {
            return false;
        }
        AddeImageDescriptor aid = getDescriptor(imageList.get(0));
        return isFromFile(aid);
    }


    /**
     * Does this represent a file
     *
     * @return Is it a file
     */
    protected boolean isFromFile(AddeImageDescriptor aid) {
        String source = aid.getSource();
        return new File(source).exists();
    }

    /**
     * A utility method that helps us deal with legacy bundles that used to
     * have String file names as the id of a data choice.
     *
     * @param object     May be an AddeImageDescriptor (for new bundles) or a
     *                   String that is converted to an image descriptor.
     * @return The image descriptor.
     */
    private AddeImageDescriptor getDescriptor(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof DataChoice) {
            object = ((DataChoice) object).getId();
        }
        if (object instanceof ImageDataInfo) {
            int index = ((ImageDataInfo) object).getIndex();
            if (index < myDataChoices.size()) {
                DataChoice dc        = (DataChoice) myDataChoices.get(index);
                Object     tmpObject = dc.getId();
                if (tmpObject instanceof ImageDataInfo) {
                    return ((ImageDataInfo) tmpObject).getAid();
                }
            }
            return null;
            //            return ((ImageDataInfo) object).getAid();
        }

        if (object instanceof AddeImageDescriptor) {
            return (AddeImageDescriptor) object;
        }
        return new AddeImageDescriptor(object.toString());
    }

    /**
     * This is used when we are unbundled and we may have different times than when we were saved.
     * Use the current set of data choices.
     *
     * @param compositeDataChoice The composite
     * @param dataChoices Its choices
     *
     * @return The  current choices
     */
    public List getCompositeDataChoices(
            CompositeDataChoice compositeDataChoice, List dataChoices) {
        //Force  creation of data choices
        getDataChoices();
        return !(hasBandInfo(compositeDataChoice))
               ? myDataChoices
               : dataChoices;
    }



    /**
     * A hook for the derived classes to return their specific name (eg,
     * ADDE data source, McIDAS data source.
     *
     * @return The name of this data source.
     */
    public abstract String getImageDataSourceName();

    /**
     * Return the list of {@link AddeImageDescriptor}s that define this
     * data source.
     *
     * @return The list of image descriptors.
     */
    public List getImageList() {
        return imageList;
    }

    /**
     * Set the list of {@link AddeImageDescriptor}s that define this data
     * source.
     *
     * @param l The list of image descriptors.
     */
    public void setImageList(List l) {
        imageList = l;
    }


    /**
     * Override the base class method to return the list of times we created.
     *
     * @return The list of times held by this data source.
     */
    public List doMakeDateTimes() {
        imageTimes = new ArrayList();
        for (Iterator iter = imageList.iterator(); iter.hasNext(); ) {
            Object              object = iter.next();
            AddeImageDescriptor aid    = getDescriptor(object);
            if ( !aid.getIsRelative()) {
                DateTime imageTime = aid.getImageTime();
                if (imageTime != null) {
                    imageTimes.add(imageTime);
                }
            } else {
                imageTimes.add(getRelativeTimeObject(aid));
            }
        }
        return imageTimes;
    }


    /**
     * Initialize the {@link ucar.unidata.data.DataCategory} objects that
     * this data source uses.
     */
    private void makeCategories() {
        twoDTimeSeriesCategories =
            DataCategory.parseCategories("IMAGE-2D-TIME;", false);
        twoDCategories = DataCategory.parseCategories("IMAGE-2D;", false);
        bandCategories = DataCategory.parseCategories("IMAGE-BAND;", false);
        bandTimeSeriesCategories =
            DataCategory.parseCategories("IMAGE-BAND-TIME;", false);

    }

    /**
     * Return the list of {@link ucar.unidata.data.DataCategory} used for
     * single time step data.
     *
     * @return A list of categories.
     */
    public List getTwoDCategories() {
        if (twoDCategories == null) {
            makeCategories();
        }
        return twoDCategories;
    }

    /**
     * Return the list of {@link ucar.unidata.data.DataCategory} used for
     * multiple time step data.
     *
     * @return A list of categories.
     */

    public List getTwoDTimeSeriesCategories() {
        if (twoDCategories == null) {
            makeCategories();
        }
        return twoDTimeSeriesCategories;
    }



    /**
     * Return the list of {@link ucar.unidata.data.DataCategory} used for
     * single time step data with band information.
     *
     * @return A list of categories.
     */
    public List getBandCategories() {
        if (bandCategories == null) {
            makeCategories();
        }
        return bandCategories;
    }

    /**
     * Return the list of {@link ucar.unidata.data.DataCategory} used for
     * multiple time step data with band information.
     *
     * @return A list of categories.
     */

    public List getBandTimeSeriesCategories() {
        if (bandTimeSeriesCategories == null) {
            makeCategories();
        }
        return bandTimeSeriesCategories;
    }




    /**
     * Create the set of {@link ucar.unidata.data.DataChoice} that represent
     * the data held by this data source.  We create one top-level
     * {@link ucar.unidata.data.CompositeDataChoice} that represents
     * all of the image time steps. We create a set of children
     * {@link ucar.unidata.data.DirectDataChoice}, one for each time step.
     */
    public void doMakeDataChoices() {
        String type = (String) getProperty(PROP_IMAGETYPE, TYPE_SATELLITE);
        List<BandInfo> bandInfos =
            (List<BandInfo>) getProperty(PROP_BANDINFO, (Object) null);
        Hashtable props = Misc.newHashtable(DataChoice.PROP_ICON,
                                            (type.equals(TYPE_RADAR)
                                             ? "/auxdata/ui/icons/Radar.gif"
                                             : "/auxdata/ui/icons/Satellite.gif"));
        List categories = (imageList.size() > 1)
                          ? getTwoDTimeSeriesCategories()
                          : getTwoDCategories();

        // This is historical an is not added into the list of choices
        // for selection by the users.
        myCompositeDataChoice = new CompositeDataChoice(this, imageList,
                getName(), getDataName(), categories, props);
        myCompositeDataChoice.setUseDataSourceToFindTimes(true);
        doMakeDataChoices(myCompositeDataChoice);

        if ((bandInfos != null) && !bandInfos.isEmpty()) {
            List biCategories = (imageList.size() > 1)
                                ? getBandTimeSeriesCategories()
                                : getBandCategories();
            /*
            if (bandInfos.size() == 1) {
                BandInfo test  = (BandInfo) bandInfos.get(0);
                List     units = test.getCalibrationUnits();
                if ((units == null) || units.isEmpty()
                        || (units.size() == 1)) {
                    return;
                }
            }
            */
            for (Iterator<BandInfo> i = bandInfos.iterator(); i.hasNext(); ) {
                BandInfo bi = i.next();
                String name    = makeBandParam(bi);
                String catName = bi.getBandDescription();
                List biSubCategories = Misc.newList(new DataCategory(catName,
                                        true));
                biSubCategories.addAll(biCategories);
                List l = bi.getCalibrationUnits();
                if (l.isEmpty() || (l.size() == 1)) {
                    DataChoice choice = new DirectDataChoice(this, bi, name,
                                            bi.getBandDescription(),
                                            biCategories, props);
                    addDataChoice(choice);
                } else {
                    for (int j = 0; j < l.size(); j++) {
                        Object   o           = l.get(j);
                        BandInfo bi2         = new BandInfo(bi);
                        String   calUnit     = o.toString();
                        String   calibration = TwoFacedObject.getIdString(o);
                        bi2.setPreferredUnit(calibration);
                        name = makeBandParam(bi2);
                        DataChoice subChoice = new DirectDataChoice(this,
                                                   bi2, name, calUnit,
                                                   biSubCategories, props);
                        addDataChoice(subChoice);
                    }
                }
            }
        }else {
            addDataChoice(myCompositeDataChoice);
        }
    }

    /**
     * Make a parmeter name for the BandInfo
     *
     * @param bi    the BandInfo in question
     *
     * @return  a name for the parameter
     */
    private String makeBandParam(BandInfo bi) {
        StringBuffer buf = new StringBuffer();
        buf.append(bi.getSensor());
        buf.append("_Band");
        buf.append(bi.getBandNumber());
        buf.append("_");
        buf.append(bi.getPreferredUnit());
        return buf.toString();
    }

    /**
     * Make the data choices and add them to the given composite
     *
     * @param composite The parent data choice to add to
     */
    private void doMakeDataChoices(CompositeDataChoice composite) {
        int cnt = 0;
        imageTimes = new ArrayList();
        List timeChoices = new ArrayList();
        myDataChoices = new ArrayList();
        String type = (String) getProperty(PROP_IMAGETYPE, TYPE_SATELLITE);
        Hashtable props = Misc.newHashtable(DataChoice.PROP_ICON,
                                            (type.equals(TYPE_RADAR)
                                             ? "/auxdata/ui/icons/clock.gif"
                                             : "/auxdata/ui/icons/clock.gif"));



        for (Iterator iter = imageList.iterator(); iter.hasNext(); ) {
            Object              object     = iter.next();
            AddeImageDescriptor aid        = getDescriptor(object);
            String              name       = aid.toString();
            DataSelection       timeSelect = null;
            if ( !aid.getIsRelative()) {
                DateTime imageTime = aid.getImageTime();
                if (imageTime != null) {
                    imageTimes.add(imageTime);
                    //timeSelect = new DataSelection (Misc.newList (imageTime));
                    //We will create the  data choice with an index, not with the actual time.
                    timeSelect =
                        new DataSelection(Misc.newList(new Integer(cnt)));
                }
            } else {
                imageTimes.add(getRelativeTimeObject(aid));
            }
            timeSelect = null;
            DataChoice choice = new DirectDataChoice(this,
                                    new ImageDataInfo(cnt, aid),
                                    composite.getName(), name,
                                    getTwoDCategories(), timeSelect, props);
            myDataChoices.add(choice);
            cnt++;
            timeChoices.add(choice);
        }
        //Sort the data choices.
        composite.replaceDataChoices(sortChoices(timeChoices));
    }



    /**
     * Class ImageDataInfo Holds an index and an AddeImageDescriptor
     *
     *
     * @author IDV Development Team
     * @version $Revision$
     */
    public static class ImageDataInfo {

        /** The index */
        private int index;

        /** The AID */
        private AddeImageDescriptor aid;



        /**
         * Ctor for xml encoding
         */
        public ImageDataInfo() {}

        /**
         * CTOR
         *
         * @param index The index
         * @param aid The aid
         */
        public ImageDataInfo(int index, AddeImageDescriptor aid) {
            this.index = index;
            this.aid   = aid;
        }

        /**
         * Get the index
         *
         * @return The index
         */
        public int getIndex() {
            return index;
        }

        /**
         * Set the index
         *
         * @param v The index
         */
        public void setIndex(int v) {
            index = v;
        }

        /**
         * Get the descriptor
         *
         * @return The descriptor
         */
        public AddeImageDescriptor getAid() {
            return aid;
        }

        /**
         * Set the descriptor
         *
         * @param v The descriptor
         */
        public void setAid(AddeImageDescriptor v) {
            aid = v;
        }

        /**
         * toString
         *
         * @return toString
         */
        public String toString() {
            return "index:" + index + " " + aid;
        }

    }


    /** _more_ */
    private Range[] sampleRanges = null;

    /**
     * Create the actual data represented by the given
     * {@link ucar.unidata.data.DataChoice}.
     *
     * @param dataChoice        Either the
     *                          {@link ucar.unidata.data.CompositeDataChoice}
     *                          representing all time steps or a
     *                          {@link ucar.unidata.data.DirectDataChoice}
     *                          representing a single time step.
     * @param category          Not really used.
     * @param dataSelection     Defines any time subsets.
     * @param requestProperties extra request properties
     *
     * @return The image or image sequence data.
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    protected Data getDataInner(DataChoice dataChoice, DataCategory category,
                                DataSelection dataSelection,
                                Hashtable requestProperties)
            throws VisADException, RemoteException {
        sampleRanges = null;
        //if ((dataChoice instanceof CompositeDataChoice)
        //        && !(hasBandInfo(dataChoice))) {
        if (dataChoice instanceof CompositeDataChoice) {
            return makeImageSequence(myCompositeDataChoice, dataSelection);
        } else if (hasBandInfo(dataChoice)) {
            //List descriptors = getDescriptors(dataChoice, dataSelection);
            //if ((descriptors != null) && (descriptors.size() == 1)) {
            //    return (Data) makeImage(
            //        (AddeImageDescriptor) descriptors.get(0));
            //} else {
            return makeImageSequence(dataChoice, dataSelection);
            //}
        }


        return (Data) makeImage(dataChoice, dataSelection);
    }


    /**
     * Override the base class method for the non composite choices.
     *
     * @param dataChoice          Either the
     *                            {@link ucar.unidata.data.CompositeDataChoice}
     *                            representing all time steps or a
     *                            {@link ucar.unidata.data.DirectDataChoice}
     *                            representing a single time step.
     *  @return The list of times represented by the given dataChoice.
     */
    public List getAllDateTimes(DataChoice dataChoice) {
        if ((dataChoice instanceof CompositeDataChoice)
                || hasBandInfo(dataChoice)) {
            return super.getAllDateTimes(dataChoice);
        }
        Object dttmObject = getDateTime(dataChoice);
        if (dttmObject != null) {
            return Misc.newList(dttmObject);
        }
        return new ArrayList();
    }

    /**
     * Override the base class method for the non-composite choices.
     *
     * @param dataChoice         Either the
     *                           {@link ucar.unidata.data.CompositeDataChoice}
     *                           representing all time steps or a
     *                           {@link ucar.unidata.data.DirectDataChoice}
     *                           representing a single time step.
     * @return The list of times represented by the given dataChoice.
     */
    public List getSelectedDateTimes(DataChoice dataChoice) {
        if ((dataChoice instanceof CompositeDataChoice)
                || hasBandInfo(dataChoice)) {
            return super.getSelectedDateTimes();
        }
        Object dttmObject = getDateTime(dataChoice);
        if (dttmObject != null) {
            return Misc.newList(dttmObject);
        }
        return new ArrayList();
    }


    /**
     * Utility method to get the time associated with the given dataChoice.
     *
     * @param dataChoice     choice for selection
     * @return  the associated time
     */
    private Object getDateTime(DataChoice dataChoice) {
        Object              id  = dataChoice.getId();
        AddeImageDescriptor aid = getDescriptor(id);
        if (aid.getIsRelative()) {
            return getRelativeTimeObject(aid);
        } else {
            return aid.getImageTime();
        }
    }


    /**
     * Get the object that we use to display relative time. Relative time is defined
     * using an integer index, 0...n. We don't want to show the actual integer.
     * Rather we want to show "Third most recent", "Fourth most recent", etc.
     *
     * @param aid The image descriptor
     * @return The object that represents the relative time index of the aid
     */
    private Object getRelativeTimeObject(AddeImageDescriptor aid) {
        return new TwoFacedObject(aid.toString(),
                                  new Integer(aid.getRelativeIndex()));
    }


    /**
     * Create the single image defined by the given dataChoice.
     *
     * @param dataChoice      The choice.
     * @param subset          any time subsets.
     *
     * @return The data.
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    protected final SingleBandedImage makeImage(DataChoice dataChoice,
            DataSelection subset)
            throws VisADException, RemoteException {

        AddeImageDescriptor aid = getDescriptor(dataChoice.getId());
        if (aid == null) {
            return null;
        }

        String   source = aid.getSource();
        
    	/**
    	 * TODO: Find the proper way to integrate local/remote data sources with different port numbers
    	 * DAVEP added this to support 8112 when using AddeLocalImageChooser and localhost...
    	 *       hacky and temporary for Alpha10
    	 */
    	if (source.substring(7,16).equals("localhost")) {
    		source = source.replace("PORT=112", "PORT=" + Constants.LOCAL_ADDE_PORT);
    		aid.setSource(source);
    	}
        
        DateTime dttm   = aid.getImageTime();
        if ((subset != null) && (dttm != null)) {
            List times = getTimesFromDataSelection(subset, dataChoice);
            if ((times != null) && (times.indexOf(dttm) == -1)) {
                return null;
            }
        }

        SingleBandedImage result = (SingleBandedImage) getCache(source);
        if (result != null) {
            return result;
        }

        try {
            AreaAdapter aa = new AreaAdapter(aid.getSource(), false);  // don't pack
            result = aa.getImage();
            putCache(source, result);
//            return result;
        } catch (java.io.IOException ioe) {
//            throw new VisADException("Creating AreaAdapter - " + ioe);
            result = makeImage(aid, false, "");
        }
        return result;

    }

    /**
     * Create the single image defined by the given dataChoice.
     *
     * @param aid AddeImageDescriptor
     *
     * @return The data.
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    private SingleBandedImage makeImage(AddeImageDescriptor aid,
                                        boolean fromSequence,
                                        String readLabel)
            throws VisADException, RemoteException {

        if (aid == null) {
            return null;
        }
        String            source = aid.getSource();
        
    	/**
    	 * TODO: Find the proper way to integrate local/remote data sources with different port numbers
    	 * DAVEP added this to support 8112 when using AddeLocalImageChooser and localhost...
    	 *       hacky and temporary for Alpha10
    	 */
    	if (source.substring(7,16).equals("localhost")) {
    		source = source.replace("PORT=112", "PORT=" + Constants.LOCAL_ADDE_PORT);
    		aid.setSource(source);
    	}
        
        SingleBandedImage result = (SingleBandedImage) getCache(source);
        if (result != null) {
            return result;
        }

        try {
            /*            if(!getCacheDataToDisk()) {
                //If not caching then just load the data
                AreaAdapter aa = new AreaAdapter(aid.getSource(), false);
                result = aa.getImage();
                putCache(source, result);
                return result;
                }*/

            /* Lets not do this right now so we can cache area files
            if (!source.startsWith("adde:")) {
                AreaAdapter aa = new AreaAdapter(source, false);
                result = aa.getImage();
                putCache(source, result);
                return result;
                }*/

            AddeImageInfo aii     = aid.getImageInfo();
            AreaDirectory areaDir = null;
            try {
                if (aii != null) {
                    //if (getCacheDataToDisk()) {
                    if (currentDirs != null) {
                        int    pos        =
                            Math.abs(aii.getDatasetPosition());
                        int    band       = 0;
                        String bandString = aii.getBand();
                        if ((bandString != null)
                                && !bandString.equals(aii.ALL)) {
                            band = new Integer(bandString).intValue();
                        }
                        //TODO: even though the band is non-zero we might only get back one band
                        band = 0;
                        areaDir =
                            currentDirs[currentDirs.length - pos - 1][band];
                        //                            System.err.println("pos:" + aii.getDatasetPosition() + " date:" + areaDir.getStartTime() + " " + band);
                    } else {
                        //If its absolute time then just use the AD from the descriptor
                        if ((aii.getStartDate() != null)
                                || (aii.getEndDate() != null)) {
                            areaDir = aid.getDirectory();
                            //                                System.err.println("absolute time:" + areaDir.getStartTime());
                            //System.err.println(" from aii:" +aii.getStartDate());
                        } else {
                            //                            System.err.println(
                            //                                "relative time without currentDirs "
                            //                                + fromSequence);
                        }
                    }
                    //                    }
                }
            } catch (Exception exc) {
                LogUtil.printMessage("error looking up area dir");
                exc.printStackTrace();
                return null;
            }


            if (areaDir == null) {
                areaDir = aid.getDirectory();
            }

            if ( !getCacheDataToDisk()) {
                areaDir = null;
            }
            if ( !fromSequence
                    || (aid.getIsRelative() && (currentDirs == null))) {
                areaDir = null;
            }

            if (areaDir != null) {
                int hash = ((aii != null)
                            ? aii.makeAddeUrl().hashCode()
                            : areaDir.hashCode());
                String filename = IOUtil.joinDir(getDataCachePath(),
                                      "image_" + hash + "_" + ((aii != null)
                        ? aii.getBand()
                        : 0) + "_" + ((areaDir.getStartTime() != null)
                                      ? "" + areaDir.getStartTime().getTime()
                                      : "") + ".dat");

                AddeImageFlatField aiff = AddeImageFlatField.create(aid,
                                              areaDir, getCacheDataToDisk(),
                                              filename, getCacheClearDelay(),
                                              readLabel);
                result = aiff;
                if (sampleRanges == null) {
                    sampleRanges = aiff.getRanges(true);
                    if ((sampleRanges != null) && (sampleRanges.length > 0)) {
                        for (int rangeIdx = 0; rangeIdx < sampleRanges.length;
                                rangeIdx++) {
                            Range r = sampleRanges[rangeIdx];
                            if (Double.isInfinite(r.getMin())
                                    || Double.isInfinite(r.getMax())) {
                                sampleRanges = null;
                                break;
                            }
                        }
                    }
                } else {
                    aiff.setSampleRanges(sampleRanges);
                }
            } else {
                AreaAdapter aa = new AreaAdapter(aid.getSource(), false);  // don't pack
                result = aa.getImage();
            }
            putCache(source, result);
            return result;
        } catch (java.io.IOException ioe) {
            throw new VisADException("Creating AreaAdapter - " + ioe);
        }

    }



    /**
     * _more_
     */
    public void reloadData() {
        currentDirs = null;
        super.reloadData();
    }


    /**
     * Create the  image sequence defined by the given dataChoice.
     *
     * @param dataChoice     The choice.
     * @param subset     any time subsets.
     * @return The image sequence.
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    protected ImageSequence makeImageSequence(DataChoice dataChoice,
            DataSelection subset)
            throws VisADException, RemoteException {

        try {
            List descriptorsToUse = new ArrayList();
            if (hasBandInfo(dataChoice)) {
                descriptorsToUse = getDescriptors(dataChoice, subset);
            } else {
                List choices = (dataChoice instanceof CompositeDataChoice)
                               ? getChoicesFromSubset(
                                   (CompositeDataChoice) dataChoice, subset)
                               : Arrays.asList(new DataChoice[] {
                                   dataChoice });
                for (Iterator iter = choices.iterator(); iter.hasNext(); ) {
                    DataChoice          subChoice = (DataChoice) iter.next();
                    AddeImageDescriptor aid =
                        getDescriptor(subChoice.getId());
                    if (aid == null) {
                        continue;
                    }
                    DateTime dttm = aid.getImageTime();
                    if ((subset != null) && (dttm != null)) {
                        List times = getTimesFromDataSelection(subset,
                                         dataChoice);
                        if ((times != null) && (times.indexOf(dttm) == -1)) {
                            continue;
                        }
                    }
                    descriptorsToUse.add(aid);
                }
            }

            if (descriptorsToUse.size() == 0) {
                return null;
            }
            AddeImageInfo biggestPosition = null;
            int           pos             = 0;
            boolean       anyRelative     = false;
            //Find the descriptor with the largets position
            String biggestSource = null;
            for (Iterator iter =
                    descriptorsToUse.iterator(); iter.hasNext(); ) {
                AddeImageDescriptor aid = (AddeImageDescriptor) iter.next();
                if (aid.getIsRelative()) {
                    anyRelative = true;
                }
                AddeImageInfo aii = aid.getImageInfo();

                //Are we dealing with area files here?
                if (aii == null) {
                    break;
                }

                //                System.err.println (" aii:" +aii.makeAddeUrl());
                //                System.err.println (" aid:" + aid.getSource());
                //Check if this is absolute time
                if ((aii.getStartDate() != null)
                        || (aii.getEndDate() != null)) {
                    biggestPosition = null;
                    break;
                }
                if ((biggestPosition == null)
                        || (Math.abs(aii.getDatasetPosition()) > pos)) {
                    pos             = Math.abs(aii.getDatasetPosition());
                    biggestPosition = aii;
                    biggestSource   = aid.getSource();
                }
            }

            //            System.err.println(getCacheDataToDisk() + " " + biggestPosition);

            //If any of them are relative then read in the list of AreaDirectorys so we can get the correct absolute times

            if (getCacheDataToDisk() && anyRelative
                    && (biggestPosition != null)) {
                biggestPosition.setRequestType(AddeImageInfo.REQ_IMAGEDIR);
                System.err.println(biggestPosition.makeAddeUrl()
                                   + "\nfrom aid:" + biggestSource);
                //                AreaDirectoryList adl =
                //                    new AreaDirectoryList(biggestPosition.makeAddeUrl());
                AreaDirectoryList adl = new AreaDirectoryList(biggestSource);
                biggestPosition.setRequestType(AddeImageInfo.REQ_IMAGEDATA);
                currentDirs = adl.getSortedDirs();
            } else {
                currentDirs = null;
            }

            ImageSequenceManager sequenceManager = new ImageSequenceManager();
            ImageSequence        sequence        = null;
            int                  cnt             = 1;
            DataChoice           parent          = dataChoice.getParent();
            for (Iterator iter =
                    descriptorsToUse.iterator(); iter.hasNext(); ) {
                AddeImageDescriptor aid = (AddeImageDescriptor) iter.next();
                if (currentDirs != null) {
                    int idx =
                        Math.abs(aid.getImageInfo().getDatasetPosition());
                    if (idx >= currentDirs.length) {
                        //                        System.err.println("skipping index:" + idx);
                        continue;
                    }
                }

                String label = "";
                if (parent != null) {
                    label = label + parent.toString() + " ";
                } else {
                    DataCategory displayCategory =
                        dataChoice.getDisplayCategory();
                    if (displayCategory != null) {
                        label = label + displayCategory + " ";
                    }
                }
                label = label + dataChoice.toString();
                String readLabel = "Time: " + (cnt++) + "/"
                                   + descriptorsToUse.size() + "  " + label;

                try {
                    SingleBandedImage image = makeImage(aid, true, readLabel);
                    if (image != null) {
                        sequence = sequenceManager.addImageToSequence(image);
                    }
                } catch (VisADException ve) {
                    LogUtil.printMessage(ve.toString());
                }
            }
            return sequence;
        } catch (Exception exc) {
            throw new ucar.unidata.util.WrapperException(exc);
        }

    }



    /**
     * Get a list of descriptors from the choice and subset
     *
     * @param dataChoice  Data choice
     * @param subset  subsetting info
     *
     * @return  list of descriptors matching the selection
     */
    private List getDescriptors(DataChoice dataChoice, DataSelection subset) {
        List times = getTimesFromDataSelection(subset, dataChoice);
        if ((times == null) || times.isEmpty()) {
            times = imageTimes;
        }
        List descriptors = new ArrayList();
        for (Iterator iter = times.iterator(); iter.hasNext(); ) {
            Object              time  = iter.next();
            AddeImageDescriptor found = null;
            for (Iterator iter2 = imageList.iterator(); iter2.hasNext(); ) {
                AddeImageDescriptor aid = getDescriptor(iter2.next());
                if (aid != null) {
                    if (aid.getIsRelative()) {
                        Object id = (time instanceof TwoFacedObject)
                                    ? ((TwoFacedObject) time).getId()
                                    : time;
                        if ((id instanceof Integer)
                                && ((Integer) id).intValue()
                                   == aid.getRelativeIndex()) {
                            found = aid;
                            break;
                        }

                    } else {
                        if (aid.getImageTime().equals(time)) {
                            found = aid;
                            break;
                        }
                    }

                }
            }
            if (found != null) {
                try {
                    AddeImageDescriptor desc = new AddeImageDescriptor(found);
                    //Sometimes we might have a null imageinfo
                    if (desc.getImageInfo() != null) {
                        AddeImageInfo aii =
                            (AddeImageInfo) desc.getImageInfo().clone();
                        BandInfo bi = (BandInfo) dataChoice.getId();
                        List<BandInfo> bandInfos =
                            (List<BandInfo>) getProperty(PROP_BANDINFO,
                                (Object) null);
                        boolean hasBand = true;
                        //If this data source has been changed after we have create a display 
                        //then the possibility exists that the bandinfo contained by the incoming
                        //data choice might not be valid. If it isn't then default to the first 
                        //one in the list
                        if (bandInfos != null) {
                            hasBand = bandInfos.contains(bi);
                            if ( !hasBand) {
                                //                                System.err.println("has band = " + bandInfos.contains(bi));
                            }
                            if ( !hasBand && (bandInfos.size() > 0)) {
                                bi = bandInfos.get(0);
                            } else {
                                //Not sure what to do here.
                            }
                        }
                        aii.setBand("" + bi.getBandNumber());
                        aii.setUnit(bi.getPreferredUnit());
                        desc.setImageInfo(aii);
                        desc.setSource(aii.getURLString());
                    }
                    descriptors.add(desc);
                } catch (CloneNotSupportedException cnse) {}
            }
        }
        return descriptors;
    }

    /**
     * Get the subset of the composite based on the selection
     *
     * @param choice  composite choice
     * @param subset  time selection
     *
     * @return subset list
     */
    private List getChoicesFromSubset(CompositeDataChoice choice,
                                      DataSelection subset) {
        List choices = choice.getDataChoices();
        if (subset == null) {
            return choices;
        }
        List times = subset.getTimes();
        if (times == null) {
            return choices;
        }
        times = TwoFacedObject.getIdList(times);
        List   subChoices = new ArrayList();
        Object firstTime  = times.get(0);
        if (firstTime instanceof Integer) {
            for (Iterator iter = times.iterator(); iter.hasNext(); ) {
                subChoices.add(
                    choices.get(((Integer) iter.next()).intValue()));
            }
        } else {  // TODO: what if they are DateTimes?
            subChoices.addAll(choices);
        }
        return subChoices;
    }


    /**
     * Check to see if this ImageDataSource is equal to the object
     * in question.
     *
     * @param o  object in question
     *
     * @return true if they are the same or equivalent objects
     */
    public boolean equals(Object o) {
        if ( !super.equals(o)) {
            return false;
        }
        if ( !getClass().equals(o.getClass())) {
            return false;
        }
        TestImageDataSource that = (TestImageDataSource) o;
        return ((this == that) || Misc.equals(imageList, that.imageList));
    }

    /**
     * Override the hashCode method. Use name and imageList.
     *
     * @return The hashcode.
     */
    public int hashCode() {
        int hashCode = getName().hashCode();
        hashCode ^= imageList.hashCode();
        return hashCode;
    }



    /**
     * Called when Datasource is removed.
     */
    public void doRemove() {
        super.doRemove();
        if (sequenceManager != null) {
            sequenceManager.clearSequence();
        }
        sequenceManager = null;
    }






    /**
     * Get the name for the main data object
     *
     * @return name of main data object
     */
    public String getDataName() {
        return "Image Sequence";
    }

    /**
     * Get an expanded description for the details display.  Override
     * base class implementation to add more info.
     *
     * @return full description of this data source
     */
    public String getFullDescription() {
        StringBuffer buf = new StringBuffer(super.getFullDescription());
        buf.append("<p>");
        List images = getImageList();
        if (images != null) {
            for (int i = 0; i < images.size(); i++) {
                Object o = images.get(i);
                if (o instanceof AddeImageDescriptor) {
                    AreaDirectory ad =
                        ((AddeImageDescriptor) o).getDirectory();
                    if (i == 0) {
                        buf.append(
                            "<table border=\"1\" width=\"100%\"><tr valign=\"bottom\"><td><b>Location</b></td><td><b>Date</b></td><td><b>Size (Lines X Elements) </b></td><td><b>Band</b></td></tr>");
                    }
                    buf.append("<tr valign=\"top\"><td width=\"300\">");
                    String path = ((AddeImageDescriptor) o).getSource();
                    if (path.length() > 50) {
                        String tmp = path;
                        path = "";
                        while (tmp.length() > 50) {
                            if (path.length() > 0) {
                                path = path + "<br>";
                            }
                            path = path + tmp.substring(0, 49);
                            tmp  = tmp.substring(49);
                        }
                        path = path + "<br>" + tmp;
                    }
                    buf.append(path);
                    buf.append("</td>");
                    buf.append("<td width=\"15%\">");
                    buf.append("" + ad.getStartTime());
                    buf.append("</td>");
                    buf.append("<td width=\"15%\">");
                    buf.append(ad.getLines());
                    buf.append(" X ");
                    buf.append(ad.getElements());
                    buf.append("</td>");
                    buf.append("<td width=\"15%\">");
                    buf.append("Band ");
                    buf.append(ad.getBands()[0]);
                    buf.append("</td></tr>");
                } else {
                    if (i == 0) {
                        buf.append(
                            "<table><tr><td><b>Location</b></td></tr>");
                    }
                    buf.append("<tr valign=\"top\"><td>");
                    buf.append(o.toString());
                    buf.append("</td></tr>");
                }
            }
            buf.append("</table>");
        }
        return buf.toString();
    }




    /**
     * If we are polling some directory this method gets called when
     * there is a new file. We set the file name, clear our state,
     * reload the metadata and tell listeners of the change.
     *
     * @param  file  new File to use.
     */
    public void newFileFromPolling(File file) {
        // System.err.println("new file from polling");
        initDataFromPollingInfo();
        dataChoices = null;
        getDataChoices();
        getDataContext().dataSourceChanged(this);
        Hashtable cache = CacheManager.findOrCreate(dataCacheKey);
        flushCache();
        //Should be only one here
        CompositeDataChoice cdc = myCompositeDataChoice;
        //    (CompositeDataChoice) getDataChoices().get(0);
        cdc.removeAllDataChoices();
        doMakeDataChoices(cdc);
        for (int i = 0; i < imageList.size(); i++) {
            AddeImageDescriptor aid        = getDescriptor(imageList.get(i));
            String              source     = aid.getSource();
            Object              cachedData = cache.get(source);
            if (cachedData != null) {
                //System.err.println("keeping the cache");
                putCache(source, cachedData);
            }
        }
        notifyDataChange();
    }

    /**
     * Sort the list of data choices on their time
     *
     * @param choices The data choices
     *
     * @return The data choices sorted
     */
    private List sortChoices(List choices) {
        Object[]   choicesArray = choices.toArray();
        Comparator comp         = new Comparator() {
            public int compare(Object o1, Object o2) {
                AddeImageDescriptor aid1 = getDescriptor(o1);
                AddeImageDescriptor aid2 = getDescriptor(o2);
                if ((aid1 == null) || (aid2 == null)) {
                    return -1;
                }
                if (aid1.getIsRelative()) {
                    if (aid1.getRelativeIndex() < aid2.getRelativeIndex()) {
                        return 0;
                    } else if (aid1.getRelativeIndex()
                               == aid2.getRelativeIndex()) {
                        return 1;
                    }
                    return -1;
                }
                return aid1.getImageTime().compareTo(aid2.getImageTime());
            }
        };
        Arrays.sort(choicesArray, comp);
        return new ArrayList(Arrays.asList(choicesArray));

    }


    /**
     * Check if the DataChoice has a BandInfo for it's Id
     *
     * @param dataChoice  choice to check
     *
     * @return true if the choice ID is a BandInfo
     */
    private boolean hasBandInfo(DataChoice dataChoice) {
        return dataChoice.getId() instanceof BandInfo;
    }
}

