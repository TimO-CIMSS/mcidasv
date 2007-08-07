package edu.wisc.ssec.mcidasv;

import edu.wisc.ssec.mcidasv.ui.McIdasColorTableManager;

import java.rmi.RemoteException;
import java.util.List;

import ucar.unidata.idv.IdvPreferenceManager;
import ucar.unidata.idv.IdvResourceManager;
import ucar.unidata.idv.IntegratedDataViewer;
import ucar.unidata.ui.colortable.ColorTableManager;
import ucar.unidata.idv.ui.IdvUIManager;
import ucar.unidata.util.LogUtil;
import visad.VisADException;
import edu.wisc.ssec.mcidasv.ui.TabbedUIManager;
import edu.wisc.ssec.mcidasv.ui.UIManager;

public class McIDASV extends IntegratedDataViewer {

    /** Points to the adde image defaults. */
    public static final IdvResourceManager.XmlIdvResource RSC_FRAMEDEFAULTS =
        new IdvResourceManager.XmlIdvResource("idv.resource.framedefaults",
                           "McIDAS-X Frame Defaults");

    /** Points to the server definitions. */
    public static final IdvResourceManager.XmlIdvResource RSC_SERVERS =
        new IdvResourceManager.XmlIdvResource("idv.resource.servers",
                           "Servers", "servers\\.xml$");
	
    /**
     * Create the McIdasV with the given command line arguments.
     * This constructor calls {@link IntegratedDataViewer#init()}
     *
     * @param args Command line arguments
     * @exception VisADException  from construction of VisAd objects
     * @exception RemoteException from construction of VisAD objects
     */
    public McIDASV(String[] args) throws VisADException, RemoteException {
        super(args);
        this.init();
    }
    
    /**
     * Load the McV properties. All other property files are disregarded.
     * 
     * @see ucar.unidata.idv.IntegratedDataViewer#initPropertyFiles(java.util.List)
     */
    @SuppressWarnings("unchecked")
	public void initPropertyFiles(List files) {
        files.clear();
        files.add(Constants.PROPERTIES_FILE);
    }

    /**
     * Factory method to create the {@link IdvUIManager}. 
     * Here we create our own ui manager so it can do McV 
     * specific things.
     *
     * @return The UI manager indicated by the startup
     * 		properties.
     */
    protected IdvUIManager doMakeIdvUIManager() {
    	
    	if (getIdv().getProperty(Constants.PROP_TABBED_UI, false)) {
			return new TabbedUIManager(getIdv());
		}
    	
        return new UIManager(getIdv());
    }

    /* (non-Javadoc)
     * @see ucar.unidata.idv.IdvBase#doMakePreferenceManager()
     */
    protected IdvPreferenceManager doMakePreferenceManager() {
        return new McIdasPreferenceManager(getIdv());
    }

    /* (non-Javadoc)
     * @see ucar.unidata.idv.IdvBase#getIdv()
     */
    public IntegratedDataViewer getIdv() {
    	return this;
    }
    
    /* (non-Javadoc)
     * @see ucar.unidata.idv.IdvBase#doMakeStateManager()
     */
    protected StateManager doMakeStateManager() {
    	return new StateManager(getIdv());
    }
    
    /* (non-Javadoc)
     * @see ucar.unidata.idv.IdvBase#doMakeResourceManager()
     */
    protected IdvResourceManager doMakeResourceManager() {
    	return new ResourceManager(getIdv());
    }

    /* (non-Javadoc)
     * @see ucar.unidata.idv.IdvBase#doMakeColorTableManager()
     */
    protected ColorTableManager doMakeColorTableManager() {
        return new McIdasColorTableManager();
    }
    
    /**
     * The main. Configure the logging and create the McIdasV
     *
     * @param args Command line arguments
     *
     * @throws Exception When something untoward happens
     */
    public static void main(String[] args) throws Exception {
        LogUtil.configure();
        new McIDASV(args);
    }


}







