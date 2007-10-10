/*
 * $Id$
 *
 * Copyright  1997-2004 Unidata Program Center/University Corporation for
 * Atmospheric Research, P.O. Box 3000, Boulder, CO 80307,
 * support@unidata.ucar.edu.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or (at
 * your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */



package ucar.unidata.view.geoloc;


import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.ProjectionImpl;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.unidata.geoloc.projection.*;

import ucar.unidata.util.LogUtil;

import ucar.visad.Util;
import ucar.visad.display.*;

import visad.*;

import visad.data.mcidas.BaseMapAdapter;

import visad.georef.*;

import visad.java3d.*;

import java.awt.*;
import java.awt.event.*;

import java.awt.geom.Rectangle2D;

import java.beans.*;

import java.net.URL;

import java.rmi.RemoteException;

import javax.media.j3d.*;

import javax.swing.*;

import javax.vecmath.*;


/**
 * Provides a navigated globe for displaying meteorological data.
 * Any displayable data must be able to map to RealType.Latitude,
 * RealType.Longitude and/or RealType.Altitude.
 *
 * @author Don Murray
 * @version $Revision$ $Date$
 */
public class GlobeDisplay extends NavigatedDisplay {

    /** Bottom View name */
    public static String BOTTOM_VIEW_NAME = "Southern Hemisphere";

    /** North View name */
    public static String NORTH_VIEW_NAME = "Western Hemisphere";

    /** East View name */
    public static String EAST_VIEW_NAME = "Pacific Region";

    /** Top View name */
    public static String TOP_VIEW_NAME = "Northern Hemisphere";

    /** South View name */
    public static String SOUTH_VIEW_NAME = "Eastern Hemisphere";

    /** West View name */
    public static String WEST_VIEW_NAME = "Atlantic Region";

    /** latitude ScalarMap */
    private ScalarMap latitudeMap = null;

    /** longitude ScalarMap */
    private ScalarMap longitudeMap = null;

    /** altitude ScalarMap */
    private ScalarMap altitudeMap = null;

    /** minimum range for altitudeMap */
    private double altitudeMin = -16000;

    /** maximum range for altitudeMap */
    private double altitudeMax = 16000;

    /** flag for whether this has been initialized or not */
    private boolean init = false;

    /** display coordinate system */
    private CoordinateSystem coordinateSystem =
        Display.DisplaySphericalCoordSys;

    /** units for cs */
    private Unit[] csUnits = null;

    /** default vertical parameter */
    private RealType verticalParameter = RealType.Altitude;

    /** default surface value */
    private Real surface = new Real(RealType.Altitude, 0);

    /** default view */
    private int view = ProjectionControlJ3D.Z_PLUS;

    /** flag for stereo */
    private boolean canDoStereo = false;

    /**
     * Constructs a new GlobeDisplay.
     *
     * @throws  VisADException         Couldn't create necessary VisAD object
     * @throws  RemoteException        Couldn't create a remote object
     */
    public GlobeDisplay() throws VisADException, RemoteException {
        this(false, null, null);
    }

    /**
     * Constructs a new GlobeDisplay.
     *
     * @param offscreen  true for an offscreen display
     * @param dimension  size of the display
     * @param screen     screen device
     * @throws  VisADException         Couldn't create necessary VisAD object
     * @throws  RemoteException        Couldn't create a remote object
     */
    public GlobeDisplay(boolean offscreen, Dimension dimension,
                        GraphicsDevice screen)
            throws VisADException, RemoteException {
        if (offscreen) {
            if (dimension == null) {
                dimension = new Dimension(600, 400);
            }
            setOffscreenDimension(dimension);
        }
        DisplayImpl displayImpl = null;
        int         api         = (offscreen
                                   ? DisplayImplJ3D.OFFSCREEN
                                   : DisplayImplJ3D.JPANEL);
        boolean useStereo = System.getProperty("idv.enableStereo",
                                "false").equals("true");
        GraphicsConfiguration config = Util.getPreferredConfig(screen, true,
                                           useStereo);
        DisplayRendererJ3D renderer = new DefaultDisplayRendererJ3D();
        if (offscreen) {
            displayImpl = new DisplayImplJ3D("Globe Display", renderer,
                                             dimension.width,
                                             dimension.height);
        } else {
            if (config == null) {
                LogUtil.userErrorMessage(
                    "Could not create a graphics configuration.\nPlease contact McIDAS-V user support or see the FAQ");
                System.exit(1);
            }
            displayImpl = new DisplayImplJ3D("Globe Display", renderer, api,
                                             config);
        }
        super.init(displayImpl);
        /*
        super((DisplayImpl) new DisplayImplJ3D("Globe Display",
                defaultConfig));
        */
        setBoxVisible(false);
        setSpatialScalarMaps();
        initializeClass();
    }

    /**
     * Initialize the class.
     *
     * @throws RemoteException    Java RMI problem
     * @throws VisADException     VisAD problem
     */
    protected void initializeClass() throws VisADException, RemoteException {
        super.initializeClass();
        csUnits = coordinateSystem.getCoordinateSystemUnits();
        DisplayRendererJ3D rend =
            (DisplayRendererJ3D) getDisplay().getDisplayRenderer();
        canDoStereo = rend.getCanvas().getStereoAvailable();
        //System.err.println("GlobeDisplay:canDoStereo = " + canDoStereo);
        setPerspectiveView(canDoStereo);
        setEyePosition(0.004);

        KeyboardBehaviorJ3D behavior = new KeyboardBehaviorJ3D(rend);
        rend.addKeyboardBehavior(behavior);
        setKeyboardBehavior(behavior);


        // Create a RubberBandBox
        RubberBandBox rubberBandBox = new RubberBandBox(RealType.Latitude,
                                          RealType.Longitude,
                                          InputEvent.SHIFT_MASK);
        rubberBandBox.addAction(new ActionImpl("RBB Action") {
            public void doAction() throws VisADException, RemoteException {
                RubberBandBox box = getRubberBandBox();
                if ((box == null) || (box.getBounds() == null)) {
                    return;
                }
                float[][] samples = box.getBounds().getSamples();
                if (samples == null) {
                    //              System.err.println ("Samples == null");
                    return;
                }
                /*
                System.out.println("bounds = " +
                                     samples[1][0] + "," + samples[0][0] + "," +
                           samples[1][1] + "," + samples[0][1]);
                ProjectionRect rect =
                   new ProjectionRect(samples[1][0], samples[0][0],
                           samples[1][1], samples[0][1]);
                System.out.println("rect = " + rect);
                setMapArea(rect);
                */
            }
        });
        setRubberBandBox(rubberBandBox);
        enableRubberBanding(true);
        getDisplay().getGraphicsModeControl().setPolygonOffsetFactor(1);
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getTopViewName() {
        return TOP_VIEW_NAME;
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getBottomViewName() {
        return BOTTOM_VIEW_NAME;
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getNorthViewName() {
        return NORTH_VIEW_NAME;
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getEastViewName() {
        return EAST_VIEW_NAME;
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getSouthViewName() {
        return SOUTH_VIEW_NAME;
    }

    /**
     * Accessor method.
     * @return name for this view
     */
    public String getWestViewName() {
        return WEST_VIEW_NAME;
    }


    //    private List keyboardBehaviors;

    /**
     * Add a keyboard behavior for this display
     *
     * @param behavior  behavior to add
     */
    public void addKeyboardBehavior(KeyboardBehavior behavior) {
        DisplayRendererJ3D rend =
            (DisplayRendererJ3D) getDisplay().getDisplayRenderer();
        KeyboardBehaviorWrapper3D beh = new KeyboardBehaviorWrapper3D(rend,
                                            behavior);
        rend.addKeyboardBehavior(beh);
    }


    /**
     * Class KeyboardBehaviorWrapper3D
     *
     * @author Unidata development team
     */
    static class KeyboardBehaviorWrapper3D extends KeyboardBehaviorJ3D {

        /** behavior */
        KeyboardBehavior behavior;

        /**
         * Create a wrapper for a KeyboardBehaviorJ3D.
         *
         * @param rend       display renderer
         * @param behavior   behavior to wrap
         *
         */
        public KeyboardBehaviorWrapper3D(DisplayRendererJ3D rend,
                                         KeyboardBehavior behavior) {
            super(rend);
            this.behavior = behavior;
        }

        /**
         * Wrapper for behavior mapKeyToFunction
         *
         * @param function   function to map
         * @param keycode    key for function
         * @param modifiers  key modifiers
         */
        public void mapKeyToFunction(int function, int keycode,
                                     int modifiers) {
            //This method does not work because it is called by the super class's ctor
            //before we have a chance to set the behavior
            if (behavior != null) {
                behavior.mapKeyToFunction(function, keycode, modifiers);
            }
        }

        /**
         * Wrapper around KeyboardBehavior.processKeyEvent
         *
         * @param event  event to process
         */
        public void processKeyEvent(java.awt.event.KeyEvent event) {
            behavior.processKeyEvent(event);
        }

        /**
         * Wrapper around KeyboardBehavior.execFuntion
         *
         * @param function  function to execute
         */
        public void execFunction(int function) {
            behavior.execFunction(function);
        }
    }


    /**
     * Handles a change to the cursor position.
     *
     * @throws VisADException   VisAD failure.
     * @throws RemoteException  Java RMI failure.
     */
    protected void cursorMoved() throws VisADException, RemoteException {
        updateLocation(
            getEarthLocation(getDisplay().getDisplayRenderer().getCursor()));
    }

    /**
     * Handles a change in the position of the mouse-pointer.  For
     * this implementation, it will only list the
     *
     * @param x    x mouse location
     * @param y    y mouse location
     *
     * @throws RemoteException    Java RMI problem
     * @throws UnitException      Unit conversion problem
     * @throws VisADException     VisAD problem
     */
    protected void pointerMoved(int x, int y)
            throws UnitException, VisADException, RemoteException {

        /*
         * Convert from (pixel, line) Java Component coordinates to (latitude,
         * longitude)
         */
        VisADRay ray =
            getDisplay().getDisplayRenderer().getMouseBehavior().findRay(x,
                y);
        double           x1 = ray.position[0];
        double           x2 = ray.position[1];
        double           x3 = ray.position[2];
        java.util.Vector v  = getDisplay().getRenderers();
        if ( !v.isEmpty()) {
            DataRenderer rend      = (DataRenderer) v.get(0);
            double[]     origin    = ray.position;
            double[]     direction = ray.vector;
            float r = rend.findRayManifoldIntersection(true, origin,
                          direction, Display.DisplaySpatialSphericalTuple, 2,
                          1);
            if (r != r) {
                x1 = Double.NaN;
                x2 = Double.NaN;
                x3 = Double.NaN;
            } else {
                float[][] xx = {
                    { (float) (origin[0] + r * direction[0]) },
                    { (float) (origin[1] + r * direction[1]) },
                    { (float) (origin[2] + r * direction[2]) }
                };
                x1 = xx[0][0];
                x2 = xx[1][0];
                x3 = xx[2][0];
            }
        }

        EarthLocation el = getEarthLocation(new double[] { x1, x2, x3 });

        setCursorLatitude(el.getLatitude());
        setCursorLongitude(el.getLongitude());
        setCursorAltitude(surface);  // always use surface for this renderer
    }

    /**
     * Define the set of spatial scalar maps that this display will
     * use.  Every time a new projection is set, a new set of DisplayTypes
     * is created with a coordinate system for transposing between
     * projection space and xyz space.  The mappings are:
     * <UL>
     * <LI>RealType.Latitude  -> getDisplayLatitudeType()
     * <LI>RealType.Longitude -> getDisplayLongitudeType()
     * <LI>RealType.Altitude  -> getDisplayAltitudeType()
     * </UL>
     * This is called on construction of the display or with every rebuild.
     *
     * @throws  VisADException         Couldn't create necessary VisAD object
     * @throws  RemoteException        Couldn't create a remote object
     */
    private void setSpatialScalarMaps()
            throws VisADException, RemoteException {
        if ( !init) {
            setDisplayInactive();
            ScalarMapSet mapSet = new ScalarMapSet();

            latitudeMap = new ScalarMap(RealType.Latitude, Display.Latitude);
            mapSet.add(latitudeMap);
            latitudeMap.setRange(-90, 90);
            latitudeMap.setScaleEnable(false);

            longitudeMap = new ScalarMap(RealType.Longitude,
                                         Display.Longitude);
            mapSet.add(longitudeMap);
            longitudeMap.setRange(-180, 180);
            longitudeMap.setScaleEnable(false);

            altitudeMap = new ScalarMap(RealType.Altitude, Display.Radius);
            setVerticalRange(altitudeMin, altitudeMax);
            mapSet.add(altitudeMap);
            altitudeMap.setScaleEnable(false);

            ScalarMap xMap = new ScalarMap(RealType.XAxis, Display.XAxis);
            xMap.setRange(-1.0, 1.0);
            xMap.setScaleEnable(false);
            mapSet.add(xMap);

            ScalarMap yMap = new ScalarMap(RealType.YAxis, Display.YAxis);
            yMap.setRange(-1.0, 1.0);
            yMap.setScaleEnable(false);
            mapSet.add(yMap);

            ScalarMap zMap = new ScalarMap(RealType.ZAxis, Display.ZAxis);
            zMap.setRange(-1.0, 1.0);
            zMap.setScaleEnable(false);
            mapSet.add(zMap);
            init = true;

            addScalarMaps(mapSet);
            setDisplayActive();
        }

    }

    /**
     * Set the map area to be displayed in the box.  Does nothing at
     * this point.
     *
     * @param mapArea  ProjectionRect describing the map area to be displayed
     * @throws  VisADException         invalid navigation or VisAD error
     * @throws  RemoteException        Couldn't create a remote object
     */
    public void setMapArea(ProjectionRect mapArea)
            throws VisADException, RemoteException {}

    /**
     * Define the map projection using a MapProjection type CoordinateSystem.
     * Implementation will be subclass dependent.
     *
     * @param  mapProjection   map projection coordinate system
     *
     * @throws  VisADException         Couldn't create necessary VisAD object
     * @throws  RemoteException        Couldn't create a remote object
     */
    public void setMapProjection(MapProjection mapProjection)
            throws VisADException, RemoteException {}

    /**
     * Accessor method for the DisplayLatitudeType
     *
     * @return DisplayRealType for Latitude mapping
     */
    public DisplayRealType getDisplayLatitudeType() {
        return Display.Latitude;
    }

    /**
     * Accessor method for the DisplayLongitudeType
     * @return DisplayRealType for Longitude mapping
     */
    public DisplayRealType getDisplayLongitudeType() {
        return Display.Longitude;
    }

    /**
     * Accessor method for the DisplayAltitudeType
     * @return DisplayRealType for Altitude mapping
     */
    public DisplayRealType getDisplayAltitudeType() {
        return Display.Radius;
    }

    /**
     * Accessor method for the DisplayTupleType.
     * @return the tuple of DisplayRealTypes
     */
    public DisplayTupleType getDisplayTupleType() {
        return Display.DisplaySpatialSphericalTuple;
    }

    /**
     * Accessor method for the ScalarMap for Altitude
     * @return the altitude ScalarMap
     */
    protected ScalarMap getAltitudeMap() {
        return altitudeMap;
    }

    /**
     * Handles a change to the cursor position.
     *
     * @throws VisADException   VisAD failure.
     * @throws RemoteException  Java RMI failure.
     */
    protected void cursorChange() throws VisADException, RemoteException {
        setCursorLatitude(getCursorValue(RealType.Latitude, 0));
        setCursorLongitude(getCursorValue(RealType.Longitude, 1));
        Real fakeAltitude = getCursorValue(RealType.Radius, 2);
        double realValue = fakeAltitude.getValue()
                           * (altitudeMax - altitudeMin) / 2 + altitudeMin;
        setCursorAltitude(new Real(RealType.Altitude, realValue));
    }

    /**
     * Set the view for 3D.  The views are based on the original display
     * as follows:
     * <pre>
     *                        NORTH
     *                      _________
     *                    W |       | E
     *                    E |  TOP  | A
     *                    S | MOTTOB| S
     *                    T |_______| T
     *                        SOUTH
     * </pre>
     * @param  view  one of the static view fields (NORTH_VIEW, SOUTH_VIEW, ..
     *               etc).  In this display, NORTH is the Western Hemisphere,
     *               SOUTH is the Eastern Hemisphere, EAST is the Pacific
     *               region and WEST is the Atlantic Region
     */
    public void setView(int view) {
        try {
            ProjectionControlJ3D projControl =
                (ProjectionControlJ3D) getDisplay().getProjectionControl();

            switch (view) {

              case BOTTOM_VIEW :  // Bottom
                  projControl.setOrthoView(ProjectionControlJ3D.Z_MINUS);
                  break;

              case NORTH_VIEW :   // North
                  projControl.setOrthoView(ProjectionControlJ3D.Y_PLUS);
                  break;

              case EAST_VIEW :    // East
                  projControl.setOrthoView(ProjectionControlJ3D.X_PLUS);
                  break;

              case TOP_VIEW :     // Top
                  projControl.setOrthoView(ProjectionControlJ3D.Z_PLUS);
                  break;

              case SOUTH_VIEW :   // South
                  projControl.setOrthoView(ProjectionControlJ3D.Y_MINUS);
                  break;

              case WEST_VIEW :    // West
                  projControl.setOrthoView(ProjectionControlJ3D.X_MINUS);
                  break;

              default :           // no-op - unknown projection
                  projControl.setOrthoView(15);
                  break;
            }
            this.view = view;
        } catch (VisADException e) {
            ;
        } catch (RemoteException re) {
            ;
        }
    }

    /**
     * Enable clipping of data at the box edges
     *
     * @param  clip  true to turn clipping on, otherwise off
     */
    public void enableClipping(boolean clip) {
        DisplayRendererJ3D dr =
            (DisplayRendererJ3D) getDisplay().getDisplayRenderer();
        try {
            dr.setClip(0, clip, 1.0f, 0.0f, 0.0f, -1.01f);
            dr.setClip(1, clip, -1.0f, 0.0f, 0.0f, -1.01f);
            dr.setClip(2, clip, 0.0f, 1.0f, 0.0f, -1.01f);
            dr.setClip(3, clip, 0.0f, -1.0f, 0.0f, -1.01f);
            dr.setClip(4, clip, 0.0f, 0.0f, 1.0f, -1.01f);
            dr.setClip(5, clip, 0.0f, 0.0f, -1.0f, -1.01f);
        } catch (VisADException ve) {
            System.err.println("Couldn't set clipping " + ve);
        }
        super.enableClipping(clip);
    }

    /**
     * Set the view to perspective or parallel if this is a 3D display.
     *
     * @param perspective  true for perspective view
     */
    public void setPerspectiveView(boolean perspective) {
        if (perspective == isPerspectiveView()) {
            return;
        }
        try {
            getDisplay().getGraphicsModeControl().setProjectionPolicy(
                (perspective == true)
                ? DisplayImplJ3D.PERSPECTIVE_PROJECTION
                : DisplayImplJ3D.PARALLEL_PROJECTION);

        } catch (Exception e) {
            ;
        }
        super.setPerspectiveView(perspective);
    }

    /**
     * Get the EarthLocation of a point in XYZ space
     *
     * @param  x  x coord.
     * @param  y  y coord.
     * @param  z  z coord.
     * @param  setZToZeroIfOverhead If in the overhead view then set Z to 0
     *
     * @return point in lat/lon/alt space.
     */
    public EarthLocation getEarthLocation(double x, double y, double z,
                                          boolean setZToZeroIfOverhead) {
        EarthLocationTuple value = null;
        try {
            float[][] numbers = visad.Set.doubleToFloat(
                                    coordinateSystem.fromReference(
                                        new double[][] {
                new double[] { x }, new double[] { y }, new double[] { z }
            }));
            Real lat = new Real(RealType.Latitude,
                                getScaledValue(latitudeMap, numbers[0][0]),
                                csUnits[0]);
            Real lon = new Real(RealType.Longitude,
                                getScaledValue(longitudeMap, numbers[1][0]),
                                csUnits[1]);
            Real alt = new Real(RealType.Altitude,
                                getScaledValue(altitudeMap, numbers[2][0]));
            value = new EarthLocationTuple(lat, lon, alt);
        } catch (VisADException e) {
            e.printStackTrace();
        }  // can't happen
                catch (RemoteException e) {
            e.printStackTrace();
        }  // can't happen
        return value;
    }

    /**
     * Returns the spatial (XYZ) coordinates of the particular EarthLocation
     *
     * @param el    earth location (lat/lon/alt) to translate
     *
     * @return  RealTuple of display coordinates.
     */
    public RealTuple getSpatialCoordinates(EarthLocation el) {
        if (el == null) {
            throw new NullPointerException(
                "MapProjectionDisplay.getSpatialCoorindate():  "
                + "null input EarthLocation");
        }
        RealTuple spatialLoc = null;
        try {
            float[][] temp = coordinateSystem.toReference(new float[][] {
                latitudeMap.scaleValues(new double[] {
                    el.getLatitude().getValue(CommonUnit.degree) }),
                longitudeMap.scaleValues(new double[] {
                    el.getLongitude().getValue(CommonUnit.degree) }),
                altitudeMap.scaleValues(new double[] {
                    el.getAltitude().getValue(CommonUnit.meter) })
            });
            double[] xyz = new double[3];
            xyz[0] = temp[0][0];
            xyz[1] = temp[1][0];
            xyz[2] = temp[2][0];
            spatialLoc = new RealTuple(RealTupleType.SpatialCartesian3DTuple,
                                       xyz);

        } catch (VisADException e) {
            e.printStackTrace();
        }  // can't happen
                catch (RemoteException e) {
            e.printStackTrace();
        }  // can't happen
        return spatialLoc;
    }



    /**
     * Returns the spatial (XYZ) coordinates of the particular EarthLocation
     *
     * @param el    earth location (lat/lon/alt) to translate
     * @param xyz Where to put the value
     *
     * @return  The xyz array
     *
     * @throws RemoteException On badness
     * @throws VisADException On badness
     */
    public double[] getSpatialCoordinates(EarthLocation el, double[] xyz)
            throws VisADException, RemoteException {
        float[][] temp = coordinateSystem.toReference(new float[][] {
            latitudeMap.scaleValues(new double[] {
                el.getLatitude().getValue(CommonUnit.degree) }),
            longitudeMap.scaleValues(new double[] {
                el.getLongitude().getValue(CommonUnit.degree) }),
            altitudeMap.scaleValues(new double[] {
                el.getAltitude().getValue(CommonUnit.meter) })
        });
        if (xyz == null) {
            xyz = new double[3];
        }
        xyz[0] = temp[0][0];
        xyz[1] = temp[1][0];
        xyz[2] = temp[2][0];
        return xyz;
    }




    /**
     * Returns the value of the cursor as a particular Real.
     *
     * @param realType          The type to be returned.
     * @param index             The index of the cursor array to access.
     *
     * @return cursor value for the particular index.
     */
    private Real getCursorValue(RealType realType, int index) {
        double[] cursor = getDisplay().getDisplayRenderer().getCursor();
        Real     value  = null;
        try {
            value = new Real(realType,
                             coordinateSystem.fromReference(new double[][] {
                new double[] { cursor[0] }, new double[] { cursor[1] },
                new double[] { cursor[2] }
            })[index][0], csUnits[index]);
        } catch (VisADException e) {
            e.printStackTrace();
        }  // can't happen
        return value;
    }

    /**
     * Determine if this MapDisplay can do stereo.
     *
     * @return true if the graphics device can do stereo
     */
    public boolean getStereoAvailable() {
        return canDoStereo;
    }


    /** defaultConfiguration */
    private static GraphicsConfiguration defaultConfig = makeConfig();

    /**
     * Create the default configuration
     * @return the default graphic configuration
     */
    private static GraphicsConfiguration makeConfig() {
        GraphicsEnvironment e =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice           d        = e.getDefaultScreenDevice();
        GraphicsConfigTemplate3D template = new GraphicsConfigTemplate3D();
        if (System.getProperty("idv.enableStereo", "false").equals("true")) {
            template.setStereo(GraphicsConfigTemplate3D.PREFERRED);
        }
        GraphicsConfiguration c = d.getBestConfiguration(template);

        return c;
    }

    /**
     * Method for setting the eye position for a 3D stereo view.
     *
     * @param position  x position of each eye (left negative, right positive).
     */
    public void setEyePosition(double position) {
        DisplayRendererJ3D rend =
            (DisplayRendererJ3D) getDisplay().getDisplayRenderer();
        // From Dan Bramer
        PhysicalBody myBody = rend.getView().getPhysicalBody();
        myBody.setLeftEyePosition(new Point3d(-position, 0.0, 0.0));
        // default is(-0.033, 0.0, 0.0)
        myBody.setRightEyePosition(new Point3d(+position, 0.0, 0.0));
    }

    /**
     * Get the latlon box of the displayed area
     *
     * @return lat lon box  or null if it can't be determined
     */
    public Rectangle2D.Double getLatLonBox() {
        return new Rectangle2D.Double(-180, -90, 360, 180);
    }

    /**
     * Get the display coordinate system that turns lat/lon/alt to
     * x/y/z
     *
     * @return  the coordinate system (may be null)
     */
    public CoordinateSystem getDisplayCoordinateSystem() {
        return coordinateSystem;
    }

    /**
     * test by running java ucar.unidata.view.geoloc.NavigatedDisplay
     *
     * @param args   not used
     *
     * @throws Exception  problem creating the display
     */
    public static void main(String[] args) throws Exception {

        JFrame frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        final GlobeDisplay navDisplay = new GlobeDisplay();
        //navDisplay.setBackground(Color.white);
        //navDisplay.setForeground(Color.black);
        if (args.length == 0) {
            MapLines mapLines  = new MapLines("maplines");
            URL      mapSource =
            //new URL("ftp://www.ssec.wisc.edu/pub/visad-2.0/OUTLSUPW");
            navDisplay.getClass().getResource("/auxdata/maps/OUTLSUPW");
            try {
                BaseMapAdapter mapAdapter = new BaseMapAdapter(mapSource);
                mapLines.setMapLines(mapAdapter.getData());
                mapLines.setColor(java.awt.Color.black);
                mapLines.addConstantMap(new ConstantMap(1.005,
                        Display.Radius));
                navDisplay.addDisplayable(mapLines);
            } catch (Exception excp) {
                System.out.println("Can't open map file " + mapSource);
                System.out.println(excp);
            }
            Grid2DDisplayable sphere = new Grid2DDisplayable("sphere", false);
            FlatField sff = FlatField.makeField1(
                                new FunctionType(
                                    RealTupleType.SpatialEarth2DTuple,
                                    RealType.getRealType("value")), 0, 360,
                                        360, -90, 90, 180);
            sphere.setData(sff);
            navDisplay.addDisplayable(sphere);
        } else {  // args.length != 0
            navDisplay.enableRubberBanding(false);
            navDisplay.setBoxVisible(true);
        }

        JPanel  panel  = new JPanel(new GridLayout(1, 0));
        JButton pushme = new JButton("Map Projection Manager");
        panel.add(pushme);
        frame.getContentPane().add(panel, BorderLayout.NORTH);

        ViewpointControl vpc = new ViewpointControl(navDisplay);
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(navDisplay.getComponent(), BorderLayout.CENTER);
        panel.add((navDisplay.getDisplayMode() == navDisplay.MODE_3D)
                  ? (Component) ucar.unidata.util.GuiUtils.leftRight(
                      new NavigatedDisplayToolBar(navDisplay),
                      vpc.getToolBar())
                  : (Component) new NavigatedDisplayToolBar(
                      navDisplay), BorderLayout.NORTH);
        panel.add(new NavigatedDisplayCursorReadout(navDisplay),
                  BorderLayout.SOUTH);
        navDisplay.draw();
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        if (navDisplay.getDisplayMode() == navDisplay.MODE_3D) {
            JMenuBar mb = new JMenuBar();
            mb.add(vpc.getMenu());
            frame.setJMenuBar(mb);
        }

        final ProjectionManager pm = new ProjectionManager();
        pm.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                if (e.getPropertyName().equals("ProjectionImpl")) {
                    try {
                        navDisplay.setMapProjection(
                            (ProjectionImpl) e.getNewValue());
                    } catch (Exception exp) {
                        System.out.println(exp);
                    }
                }
            }
        });
        pushme.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                pm.show();
            }
        });
        navDisplay.getDisplay().getGraphicsModeControl().setScaleEnable(true);
        // Rotate checkbox
        JCheckBox rotate = new JCheckBox("Rotate Display", false);
        rotate.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                navDisplay.setAutoRotate(
                    ((JCheckBox) e.getSource()).isSelected());
            }
        });
        frame.getContentPane().add("South", rotate);
        frame.pack();
        frame.setVisible(true);

        /*
        JFrame frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e)
            {
                System.exit(0);
            }
        });
        GlobeDisplay globeDisplay = new GlobeDisplay();
        frame.getContentPane().add(
            globeDisplay.getComponent(),BorderLayout.CENTER);
        MapLines mapLines = new MapLines("maplines");
        URL mapSource =
            new URL("ftp://ftp.ssec.wisc.edu/pub/visad-2.0/OUTLSUPW");
        try
        {
            BaseMapAdapter mapAdapter = new BaseMapAdapter(mapSource);
            mapLines.setMapLines(mapAdapter.getData());
            //mapLines.setColor(java.awt.Color.red);
            globeDisplay.addDisplayable(mapLines);
            globeDisplay.draw();
        }
        catch (Exception excp)
        {
           System.out.println("Can't open map file " + mapSource);
           System.out.println(excp);
        }
        frame.pack();
        frame.setVisible(true);
        globeDisplay.setBoxVisible(false);
        */
    }
}

