package ucar.unidata.data.imagery.mcidas;

import edu.wisc.ssec.mcidas.McIDASUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Hashtable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Date;

import visad.*;

import java.rmi.RemoteException;

import ucar.unidata.util.LogUtil;
import ucar.unidata.util.Misc;
import ucar.unidata.util.ColorTable;
import ucar.unidata.util.GuiUtils;
import ucar.unidata.util.Poller;
import ucar.unidata.util.PollingInfo;
import ucar.unidata.util.Trace;
import ucar.unidata.data.*;
import ucar.unidata.data.DirectDataChoice;
import ucar.unidata.ui.colortable.ColorTableManager;
import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.idv.DisplayControl;
import ucar.unidata.idv.MapViewManager;
import ucar.unidata.idv.control.DisplayControlImpl;
import ucar.unidata.idv.control.ImageSequenceControl;
import ucar.unidata.idv.control.mcidas.FrameComponentInfo;
import ucar.unidata.idv.control.mcidas.McIDASComponents;
import ucar.unidata.idv.control.mcidas.McIDASImageSequenceControl;

import visad.Set;
import visad.georef.MapProjection;
import visad.java3d.*;
import visad.util.*;
import visad.data.mcidas.*;
import visad.meteorology.*;

import java.io.IOException;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.print.*;
import javax.swing.*;
import java.util.*;
import java.io.*;


/**
 * Used to cache  a data choice and its data
 *
 * @author IDV development team
 * @version $Revision$
 */


public class McIDASDataSource extends DataSourceImpl  {

    /**
     *  If we are polling on the McIDAS-X dirty flag we keep the Poller around so
     *  on a doRemove we can tell the poller to stop running.
     */
    private List pollers;

    /** Holds information for polling */
    private  PollingInfo pollingInfo;

    /** Holds frame component information for polling */
    //protected FrameComponentInfo frameComponentInfo;

    protected  FrameDirtyInfo frameDirtyInfo;

    /** Has the initPolling been called yet */
    private boolean haveInitedPolling = false;

/* ???
    private FrmsubsImpl fsi = new FrmsubsImpl();
*/
    private static FrmsubsMM fsi;
    static {
      try {
        fsi = new FrmsubsMM();
        fsi.getMemoryMappedUC();
      } catch (Exception e) { }
    }

    /** list of frames to load */
    private List frameNumbers = new ArrayList();

    /** list of frames */
    protected List frameList;

    /** list of DateTimes of frames */
    protected List frameTimes;

    /** list of twoD categories */          
    private List twoDCategories;  
                    
    /** list of 2D time series categories */
    private List twoDTimeSeriesCategories;

    /** image data arrays */
    private double values[][] = new double[1][1];
    static byte pixels[];
    static int lastFrameNo = 0;
    static McIDASFrame lastFrm = null;

    /**
     * Default bean constructor; does nothing
     */
    public McIDASDataSource() {}


    /**
     * Create a McIDASDataSource
     *
     *
     * @param descriptor the datasource descriptor
     * @param name my name
     * @param properties my properties
     */
    public McIDASDataSource(DataSourceDescriptor descriptor, String name,
                            Hashtable properties) {
        super(descriptor, "McIDAS data", "McIDAS data", properties);

        if ((properties == null) || (properties.get("frame numbers") == null)) {
          List frames = new ArrayList();
          frames.add(new Integer(-1));
          properties.put("frame numbers", frames);
        }

        frameNumbers.clear();
        frameNumbers.add(properties.get("frame numbers"));

        List frames = new ArrayList();
        frames = (List)frameNumbers.get(0);
        setFrameList(makeFrameDescriptors(frames));
        this.frameTimes = getDateTimes();

        Integer frmInt = (Integer)frames.get(0);
        int frmNo = frmInt.intValue();

        frameDirtyInfo = initFrameDirtyInfo(frmNo);

        initPolling(frmNo);

    }


    /**
     * Make a list of frame descriptors
     *
     * @param frames  List of frame numbers
     *
     * @return ImageDataset
     */
    public List makeFrameDescriptors(List frames) {
        List descriptors = new ArrayList();
        Integer frmInt;
        int frmNo;
        for (int i = 0; i < frames.size(); i++) {
          frmInt = (Integer)frames.get(i);
          frmNo = frmInt.intValue();
          descriptors.add(new McIDASXFrameDescriptor(frmNo));
        }
        return descriptors;
    }


    /** Get a list of DateTimes for a frame sequence 
     */
    public List getDateTimes() {
      List selectedDateTimes = new ArrayList();
      for (int i=0; i<this.frameList.size(); i++) {
        selectedDateTimes.add(((McIDASXFrameDescriptor)(this.frameList.get(i))).getDateTime());
      }
      return selectedDateTimes;
    }

   
    /**
     * This is called after  this datasource has been fully created
     * and initialized after being unpersisted by the XmlEncoder.
     */
    public void initAfterUnpersistence() {
        super.initAfterUnpersistence();

        List frames = getFrame();
        setFrameList(makeFrameDescriptors(frames));
    }


    /**
     * Gets called after creation. Initialize the connection
     */
    public void initAfterCreation() {
        initConnection();
        // initPolling();
    }



    /**
     * Initialize the connection to McIDAS.
     * This gets called when the data source is newly created
     * or decoded form a bundle.
     */
    private void initConnection() {
/* ???
      int istat = fsi.getSharedMemory();
*/
      //fsi.getMemoryMappedUC();
      int istat = 0;

      //If something bad happens then call:
      //        setInError(true,"The error message");
      if (istat < 0)
        setInError(true,"Unable to attach McIDAS-X shared memory");
    }


    /**
     *
     * @param dataChoice        The data choice that identifies the requested
     *                          data.
     * @param category          The data category of the request.
     * @param dataSelection     Identifies any subsetting of the data.
     * @param requestProperties Hashtable that holds any detailed request
     *                          properties.
     *
     * @return The data
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */

    protected Data getDataInner(DataChoice dataChoice, DataCategory category,
                                DataSelection dataSelection, Hashtable requestProperties)
                                throws VisADException, RemoteException {


        FrameComponentInfo frameComponentInfo = new FrameComponentInfo();

        Boolean mc;
        mc = (Boolean)(requestProperties.get(McIDASComponents.IMAGE));
        if (mc == null)  mc=Boolean.TRUE; 
        if (mc.booleanValue()) {
          frameComponentInfo.isImage = true;
        } else {
          frameComponentInfo.isImage = false;
        }
        mc = (Boolean)(requestProperties.get(McIDASComponents.GRAPHICS));
        if (mc == null)  mc=Boolean.TRUE; 
        if (mc.booleanValue()) {
          frameComponentInfo.isGraphics = true;
        } else {
          frameComponentInfo.isGraphics = false;
        }
        mc = (Boolean)(requestProperties.get(McIDASComponents.COLORTABLE));
        if (mc == null)  mc=Boolean.TRUE; 
        if (mc.booleanValue()) {
          frameComponentInfo.isColorTable = true;
        } else {
          frameComponentInfo.isColorTable = false;
        }

        int frmNo;
        List frames = new ArrayList();
        List defList = null;
        frameNumbers.clear();
        frameNumbers.add((List)getProperty(ucar.unidata.ui.imagery.mcidas.FrameChooser.FRAME_NUMBERS_KEY, defList));
        frames = (List)frameNumbers.get(0);

        Data data=null;
        if (frames.size() < 2) {
          Integer frmInt = (Integer)frames.get(0);
          frmNo = frmInt.intValue();
          data = (Data) getMcIdasSequence(frmNo, frameComponentInfo);
        } else {
          String dc="";
          String fd="";
          for (int i=0; i<frames.size(); i++) {
            dc = dataChoice.toString();
            fd = (this.frameList.get(i)).toString();
            if (dc.compareTo(fd) == 0) {
              Integer frmInt = (Integer)frames.get(i);
              frmNo = frmInt.intValue();
              data = (Data) getMcIdasSequence(frmNo, frameComponentInfo);
            }
          }
        }
        return data;
    }

    /**
     * make a time series from selected McIDAS-X frames
     */
    private SingleBandedImage getMcIdasSequence(int frmNo, FrameComponentInfo frameComponentInfo)
            throws VisADException, RemoteException {

      SingleBandedImage image = getMcIdasFrame(frmNo, frameComponentInfo);
      if (image != null) {
        Integer fo = new Integer(frmNo);
        putCache(fo,image);
      }
      return image;
    }


    public FrameDirtyInfo getFrameDirtyInfo() {
      return frameDirtyInfo;
    }
      

    /**
     * Creates, if needed, and returns the pollingInfo member.
     *
     * @return The pollingInfo
     */
    private PollingInfo initPollingInfo(int frmNo) {
        if (pollingInfo == null) {
            long interval = 500;
            boolean isActive = false;
            if (frmNo < 0) isActive = true;
            pollingInfo = new PollingInfo(interval, isActive);
        }
        return pollingInfo;
    }

    /**
     * Creates, if needed, and returns the frameDirtyInfo member.
     *
     * @return The frameDirtyInfo
     */
    private FrameDirtyInfo initFrameDirtyInfo(int frmNo) {
//        if (frmNo>0) {
//            frameDirtyInfo = new FrameDirtyInfo(true, true, true);
//        } else {
            frameDirtyInfo = new FrameDirtyInfo(true, true, true);
//        }
        return frameDirtyInfo;
    }

    /**
     * Poll the McIDAS-X dirty flag.
     *
     * @param milliSeconds Poll interval in milliseconds
     */
    private void startPolling() {
      final McIDASPoller mcidasPoller = new McIDASPoller(frameDirtyInfo, new ActionListener() {
         public void actionPerformed(ActionEvent e) {
           DisplayControlImpl dci = getDisplayControlImpl();
           MapProjection saveMapProjection;
           if (frameDirtyInfo.dirtyImage || dci==null) {
             saveMapProjection = null;
           } else {
             saveMapProjection = dci.getMapViewProjection();
           }
           reloadData();
           MapViewManager mvm = null;
           if (dci != null)
             mvm = dci.getMapViewManager();
           if (saveMapProjection != null) {
             mvm.setMapProjection(saveMapProjection, false);
           } else {
             if (dci != null) {
               MapProjection mp = dci.getDataProjection();
               mvm.setMapProjection( mp, true,
                dci.getDisplayConventions().getMapProjectionLabel(mp, dci), false);
             }
           }
         }
      }, pollingInfo);
      if (pollers ==null) {
        pollers = new ArrayList();
      }
      pollers.add(mcidasPoller);
    }

    /**
     * Get the data applicable to the DataChoice and selection criteria.
     *
     * @param dataChoice         choice that defines the data
     * @param category           the data category
     * @param incomingDataSelection   DataSelection for subsetting
     * @param requestProperties  extra request properties
     * @return  the associated data
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    public synchronized Data getData(
            DataChoice dataChoice, DataCategory category,
            DataSelection incomingDataSelection,
            Hashtable requestProperties) throws VisADException,
                RemoteException {

        int outstandingGetDataCalls = getOutstandingGetDataCalls();

        McIDASImageSequenceControl misc = (McIDASImageSequenceControl)getDisplayControlImpl();
        pollingInfo = misc.getPollingInfo();

        //start up polling if we have not done so already.
        initPolling(0);
        if (pollingInfo.getIsActive()) {
           startPolling();
        } else {
           stopPolling();
        }


        //Just call this in case it has not been called yet
        //because it can trigger a failure in some of
        //the derived DataSource classes.
        getDataChoices();
        getAllDateTimes();


        DataSelection selection = DataSelection.merge(incomingDataSelection,
                                      getDataSelection());
        List cacheKey = Misc.newList(createCacheKey(dataChoice, selection,
                            requestProperties));

        if (requestProperties != null) {
            Hashtable newProperties = (Hashtable) requestProperties.clone();
            newProperties.remove(DataChoice.PROP_REQUESTER);
            if (newProperties.size() > 0) {
                cacheKey.add(newProperties.toString());
            }
        }
        Data cachedData = (Data) getCache(cacheKey);
        if ((pollingInfo.getIsActive() == true) || (cachedData == null)) {
            outstandingGetDataCalls++;
            try {
                LogUtil.message("Data: " + toStringTruncated() + ": "
                                + dataChoice);
                cachedData = getDataInner(dataChoice, category, selection,
                                          requestProperties);
                LogUtil.message("");
            } finally {
                outstandingGetDataCalls--;
            }
            if ((cachedData != null) && shouldCache(cachedData)) {
                putCache(cacheKey, cachedData);
            }
        } else {}
        putOutstandingGetDataCalls(outstandingGetDataCalls);
        return cachedData;
    }


    private DisplayControlImpl getDisplayControlImpl() {
      DisplayControlImpl dci = null;
      List dcl = getDataChangeListeners();
      if (dcl != null) {
        for (int i=0; i< dcl.size(); i++) {
          if (dcl.get(i) instanceof McIDASImageSequenceControl) {
            dci= (DisplayControlImpl)(dcl.get(i));
            break;
          }
        }
      }
      return dci;
    }


    /**
     * Stop polling
     */
    private void stopPolling() {
        getPollingInfo().setIsActive(false);
        if (pollers != null) {
            for (int i = 0; i < pollers.size(); i++) {
                ((Poller) pollers.get(i)).stopRunning();
            }
            pollers = null;
        }
    }

    /**
     * Called after created or unpersisted to start up polling if need be.
     */
    private void initPolling(int frmNo) {
        if (haveInitedPolling) {
            return;
        }
        haveInitedPolling = true;
        pollingInfo = initPollingInfo(frmNo);

        if (pollingInfo.getIsActive()) {
            startPolling();
        }
    }


    /**
     * Get the location where we poll.
     * 
     * @return Directory to poll on.
     */
    protected File getLocationForPolling() {
      return null;
    }


    /**
     * Set the list of {@link AddeImageDescriptor}s that define this data
     * source.
     *
     * @param l The list of image descriptors.
     */
    public void setFrameList(List l) {
        this.frameList = l;
    }

    /**
     * Get frame numbers
     *
     * @return frame numbers 
     */
    public List getFrame() {

        List defList = null;
        List frameNumbers =
            (List)getProperty(ucar.unidata.ui.imagery.mcidas.FrameChooser.FRAME_NUMBERS_KEY, defList);
        return frameNumbers;
    }

    public List getFrameNumbers() {
        return frameNumbers;
    }

    /**
     * Get the name for the main data object
     *
     * @return name of main data object
     */
    public String getDataName() {

        String dataName =
            (String) getProperty(ucar.unidata.ui.imagery.mcidas.FrameChooser.DATA_NAME_KEY,
                                 "Frame Sequence");
        if (dataName.equals("")) {
            dataName = "Frame Sequence";
        }
        return dataName;
    }

    /**
     * Initialize the {@link ucar.unidata.data.DataCategory} objects that
     * this data source uses.
     */
    private void makeCategories() {
        twoDTimeSeriesCategories =
            DataCategory.parseCategories("MCIDAS-IMAGE-2D-TIME;", false);
        twoDCategories = DataCategory.parseCategories("MCIDAS-IMAGE-2D;", false);
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
     * Create the set of {@link ucar.unidata.data.DataChoice} that represent
     * the data held by this data source.  We create one top-level
     * {@link ucar.unidata.data.CompositeDataChoice} that represents
     * all of the image time steps. We create a set of children
     * {@link ucar.unidata.data.DirectDataChoice}, one for each time step.
     */
    public void doMakeDataChoices() {
        CompositeDataChoice composite = new CompositeDataChoice(this,
                                            getFrame(), getName(),
                                            getDataName(),
                                            (this.frameList.size() > 1)
                                            ? getTwoDTimeSeriesCategories()
                                            : getTwoDCategories()) {
            public List getSelectedDateTimes() {
                return dataSource.getSelectedDateTimes();
            }
        };
        addDataChoice(composite);
        doMakeDataChoices(composite);
    }

    /**
     * Make the data choices and add them to the given composite
     *
     * @param composite The parent data choice to add to
     */
    private void doMakeDataChoices(CompositeDataChoice composite) {
        int cnt = 0;
        frameTimes = new ArrayList();
        List timeChoices = new ArrayList();

        for (Iterator iter = frameList.iterator(); iter.hasNext(); ) {
            Object              object     = iter.next();
            McIDASXFrameDescriptor fd        = getDescriptor(object);
            String              name       = fd.toString();
            DataSelection       timeSelect = null;
            DateTime frameTime = fd.getDateTime();
            if (frameTime != null) {
              frameTimes.add(frameTime);
              //We will create the  data choice with an index, not with the actual time.
               timeSelect =
                   new DataSelection(Misc.newList(new Integer(cnt)));
            }
            timeSelect = null;
            DataChoice choice =
                new DirectDataChoice(this, new FrameDataInfo(cnt, fd),
                                     composite.getName(), name,
                                     getTwoDCategories(), timeSelect);
            cnt++;
            timeChoices.add(choice);
        }

        //Sort the data choices.
        composite.replaceDataChoices(sortChoices(timeChoices));
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
                McIDASXFrameDescriptor fd1 = getDescriptor(o1);
                McIDASXFrameDescriptor fd2 = getDescriptor(o2);
                return fd1.getDateTime().compareTo(fd2.getDateTime());
            }
        };
        Arrays.sort(choicesArray, comp);
        return new ArrayList(Arrays.asList(choicesArray));

    }

    /**
     * A utility method that helps us deal with legacy bundles that used to
     * have String file names as the id of a data choice.
     *
     * @param object     May be an AddeImageDescriptor (for new bundles) or a
     *                   String that is converted to an image descriptor.
     * @return The image descriptor.
     */
    private McIDASXFrameDescriptor getDescriptor(Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof DataChoice) {
            object = ((DataChoice) object).getId();
        }
        if (object instanceof FrameDataInfo) {
            int index = ((FrameDataInfo) object).getIndex();
            List                choices = getDataChoices();
            CompositeDataChoice cdc = (CompositeDataChoice) choices.get(0);
            if (index < cdc.getDataChoices().size()) {
                DataChoice dc = (DataChoice) cdc.getDataChoices().get(index);
                Object     tmpObject = dc.getId();
                if (tmpObject instanceof FrameDataInfo) {
                    return ((FrameDataInfo) tmpObject).getFd();
                }
            }
            return ((FrameDataInfo) object).getFd();
        }

        if (object instanceof McIDASXFrameDescriptor) {
            return (McIDASXFrameDescriptor) object;
        }
        return new McIDASXFrameDescriptor();
    }

    /**
     * Class FrameDataInfo Holds an index and an McIDASXFrameDescriptor
     */
    public class FrameDataInfo {

        /** The index */
        private int index;

        /** The FD */
        private McIDASXFrameDescriptor fd;



        /**
         * Ctor for xml encoding
         */
        public FrameDataInfo() {}

        /**
         * CTOR
         *
         * @param index The index
         * @param fd The fd
         */
        public FrameDataInfo(int index, McIDASXFrameDescriptor fd) {
            this.index = index;
            this.fd   = fd;
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
        public McIDASXFrameDescriptor getFd() {
            return fd;
        }

        /**
         * Set the descriptor
         *
         * @param v The descriptor
         */
        public void setFd(McIDASXFrameDescriptor v) {
            fd = v;
        }

        /**
         * toString
         *
         * @return toString
         */
        public String toString() {
            return "index:" + index + " " + fd;
        }

    }


   public SingleBandedImage getMcIdasFrame(int frameNumber, FrameComponentInfo frameComponentInfo)
          throws VisADException, RemoteException {

     FlatField image_data = null;
     SingleBandedImage field = null;

     McIDASFrame frm;
     int frameNo;
     if (frameNumber > 0) {
        frameNo = frameNumber;
        frameDirtyInfo = initFrameDirtyInfo(frameNumber);
     } else {
        frameNo = fsi.getCurrentFrame();
     }

     if (frameDirtyInfo.dirtyImage) {
       frm = new McIDASFrame(frameNumber);
       if (frameDirtyInfo == null) {
         frameDirtyInfo = initFrameDirtyInfo(frameNumber);
       }
       lastFrameNo = frameNo;
       lastFrm = frm;
     } else {
       frm = lastFrm;
     }

     if (frameComponentInfo.isColorTable && frameDirtyInfo.getDirtyColorTable()) {
       frameDirtyInfo.setDirtyColorTable(false);
       if (frm.getFrameData(false,frameComponentInfo.isColorTable) < 0) {
          System.out.println("McIDASDataSource: error getting ColorTable");
          return field;
       }
       ColorTable mcidasXColorTable = new ColorTable("MCIDAS-X",ColorTable.CATEGORY_BASIC,frm.myEnhTable);
       DataContext dataContext = getDataContext();
       ColorTableManager colorTableManager = ((IntegratedDataViewer)dataContext).getColorTableManager();
       colorTableManager.addUsers(mcidasXColorTable);
       List dcl = ((IntegratedDataViewer)dataContext).getDisplayControls();

       for (int i=dcl.size()-1; i>=0; i--) {
         DisplayControlImpl dc = (DisplayControlImpl)dcl.get(i);
         if (dc instanceof ImageSequenceControl) {
           dc.setColorTable("default", mcidasXColorTable);
           break;
         }
       }
     }

     if (frameDirtyInfo.getDirtyImage()) {
       if (frm.getFrameData(true,false) < 0) {
          System.out.println("McIDASDataSource: error getting image data");
          return field;
       }
       values = new double[1][frm.lines*frm.elems];
       pixels = new byte[frm.lines*frm.elems];

       for (int i=0; i<frm.lines; i++) {
         for (int j=0; j<frm.elems; j++) {
           pixels[i*frm.elems + j] = frm.myImg[(frm.lines-i-1)*frm.elems + j];
         }
       }
       frameDirtyInfo.setDirtyImage(false);
     }

     if (frameComponentInfo.isImage) {
       for (int i=0; i<frm.lines*frm.elems; i++) {
         values[0][i] = (double)pixels[i];
         if (values[0][i] < 0.0 ) values[0][i] += 256.0;
       }
     } else {
       for (int i=0; i<frm.lines*frm.elems; i++) {
         values[0][i] = (double)0.0;
       }
     }

     if (frameDirtyInfo.getDirtyGraphics()) {
       if (frm.getGraphicsData() < 0) {
         System.out.println("problem in getGraphicsData");
       }
       frameDirtyInfo.setDirtyGraphics(false);
     }

     if (frameComponentInfo.isGraphics) {
       int gpts = frm.myGraphics.length;
       int lin;
       for (int i=0; i<gpts; i++) {
         if (frm.myGraphics[i] > 0) {
           lin  = frm.lines - 1 -frm.myGraLocs[0][i];
           values[0][lin*frm.elems + frm.myGraLocs[1][i]] = (double)frm.myGraphics[i];
         }
       }
     }

     FrameDirectory fd = frm.myFrameDir;
     Date nominal_time = fd.getNominalTime();

  // fake an area directory
     int[] adir = new int[64];
     adir[5] = fd.uLLine;
     adir[6] = fd.uLEle;
     adir[8] = frm.lines;
     adir[9] = frm.elems;
     adir[11] = fd.lineRes;
     adir[12] = fd.eleRes;

     AREACoordinateSystem  cs = new AREACoordinateSystem( adir, fd.nav, fd.aux);
                                                                                          
     double[][] linele = new double[2][4];
     double[][] latlon = new double[2][4];
     // LR
     linele[0][0] = (double)(frm.elems-1);
     linele[1][0] = 0.0;
     // UL
     linele[0][1] = 0.0;
     linele[1][1] = (double)(frm.lines-1);
     // LL
     linele[0][2] = 0.0;
     linele[1][2] = 0.0;
     // UR
     linele[0][3] = (double)(frm.elems-1);
     linele[1][3] = (double)(frm.lines-1);
                                                                                              
     latlon = cs.toReference(linele);
     // System.out.println("LR: " +  latlon[0][0] + " " + latlon[1][0]);
     // System.out.println("UL: " +  latlon[0][1] + " " + latlon[1][1]);
     // System.out.println("LL: " +  latlon[0][2] + " " + latlon[1][2]);
     // System.out.println("UR: " +  latlon[0][3] + " " + latlon[1][3]);
                                                                                              
     RealType[] domain_components = {RealType.getRealType("ImageElement", null, null),
            RealType.getRealType("ImageLine", null, null)};
     RealTupleType image_domain =
                 new RealTupleType(domain_components, cs, null);
                                                                                              
//  Image numbering is usually the first line is at the "top"
//  whereas in VisAD, it is at the bottom.  So define the
//  domain set of the FlatField to map the Y axis accordingly
 
     Linear2DSet domain_set = new Linear2DSet(image_domain,
                                 0, (frm.elems - 1), frm.elems,
                                 (frm.lines - 1), 0, frm.lines );
     RealType range = RealType.getRealType("brightness");
                                                                                              
     FunctionType image_func = new FunctionType(image_domain, range);
                                                                                              
// now, define the Data objects
     image_data = new FlatField(image_func, domain_set);
     DateTime date = new DateTime(nominal_time);
     image_data = new NavigatedImage(image_data, date, "McIDAS Image");
                                                                                              
// put the data values into the FlatField image_data
     image_data.setSamples(values,false);
     field = (SingleBandedImage) image_data;

     return field;
   }
}

