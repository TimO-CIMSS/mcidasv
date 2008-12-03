package edu.wisc.ssec.mcidasv.data.hydra;

import visad.FlatField;
import visad.SampledSet;
import visad.RealTuple;
import visad.SetType;
import visad.RealType;
import visad.RealTupleType;
import visad.VisADException;
import visad.CoordinateSystem;
import visad.FunctionType;
import visad.Real;
import visad.Set;
import visad.Linear1DSet;
import visad.Linear2DSet;
import visad.Gridded1DSet;
import visad.Gridded2DSet;
import visad.QuickSort;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.ArrayList;
import java.awt.geom.Rectangle2D;

import visad.georef.MapProjection;
import visad.CachingCoordinateSystem;
import ucar.visad.ProjectionCoordinateSystem;

public class MultiSpectralAggr extends MultiSpectralData {

  Gridded1DSet aggrDomain = null;

  MultiSpectralData[] adapters = null;

  int[] sort_indexes = null;

  float[] aggrValues = null;

  float[] aggrSamples = null;

  int numAdapters;

  int numBands;

  int[] offset;

  public MultiSpectralAggr(MultiSpectralData[] adapters)
         throws Exception {
    super(adapters[0].swathAdapter, null);
    this.adapters = adapters;
    paramName = adapters[0].getParameter();

    numAdapters = adapters.length;
    int[] numBandsAdapter = new int[numAdapters];
    offset = new int[numAdapters];
    Gridded1DSet[] spectrumDomains = new Gridded1DSet[numAdapters];

    if (adapters[0].spectrumAdapter.hasBandNames()) {
      hasBandNames = true;
      bandNameList = new ArrayList<String>();
      bandNameMap = new HashMap<String, Float>();
      for (int k=0; k<numAdapters; k++) {
        bandNameList.addAll(adapters[k].spectrumAdapter.getBandNames());
        bandNameMap.putAll(adapters[k].spectrumAdapter.getBandNameMap());
      }
    }

    numBands = 0;
    for (int k=0; k<numAdapters; k++) {
      Gridded1DSet set = adapters[k].spectrumAdapter.getDomainSet();
      spectrumDomains[k] = set;
      numBandsAdapter[k] = set.getLength();
      offset[k] = numBands;
      numBands += numBandsAdapter[k];
    }
   
    aggrSamples = new float[numBands];
    aggrValues  = new float[numBands];

    for (int k=0; k<numAdapters; k++) {
      float[][] samples = spectrumDomains[k].getSamples(false);
      System.arraycopy(samples[0], 0, aggrSamples, offset[k], samples[0].length);
    }

    sort_indexes = QuickSort.sort(aggrSamples);
    SpectrumAdapter specAdapt = adapters[0].spectrumAdapter;
    aggrDomain = new Gridded1DSet(specAdapt.getDomainSet().getType(), 
                        new float[][] {aggrSamples}, aggrSamples.length); 
  }

  public FlatField getSpectrum(int[] coords) throws Exception {
    FlatField spectrum = null;
    for (int k=0; k<numAdapters; k++) {
      spectrum = adapters[k].getSpectrum(coords);
      float[][] values = spectrum.getFloats(false);
      System.arraycopy(values[0], 0, aggrValues, offset[k], values[0].length);
    }

    for (int t=0; t<numBands; t++) {
      aggrValues[t] = aggrValues[sort_indexes[t]];
    }

    spectrum = new FlatField((FunctionType)spectrum.getType(), aggrDomain);
    spectrum.setSamples(new float[][] {aggrValues});

    return spectrum;
  }

  public FlatField getSpectrum(RealTuple location) throws Exception {
    FlatField spectrum = null;
    for (int k=0; k<numAdapters; k++) {
      spectrum = adapters[k].getSpectrum(location);
      float[][] values = spectrum.getFloats(false);
      System.arraycopy(values[0], 0, aggrValues, offset[k], values[0].length);
    }

    for (int t=0; t<numBands; t++) {
      aggrValues[t] = aggrValues[sort_indexes[t]];
    }

    spectrum = new FlatField((FunctionType)spectrum.getType(), aggrDomain);
    spectrum.setSamples(new float[][] {aggrValues});

    return spectrum;
  }

  public FlatField getImage(HashMap subset) throws Exception {
    int channelIndex = (int) ((double[])subset.get(SpectrumAdapter.channelIndex_name))[0];
    
    int idx = sort_indexes[channelIndex];
    
    int swathAdapterIndex = numAdapters-1;
    for (int k=0; k<numAdapters-1;k++) {
      if (idx >= offset[k] && idx < offset[k+1]) swathAdapterIndex = k;
    }
    float channel = aggrSamples[channelIndex];
    FlatField image = adapters[swathAdapterIndex].getImage(channel, subset);
    cs = ((RealTupleType) ((FunctionType)image.getType()).getDomain()).getCoordinateSystem();
    for (int k=0; k<numAdapters;k++) {
      if (k != swathAdapterIndex) adapters[k].setCoordinateSystem(cs);
    }
    return image;
  }

  public FlatField getImage(float channel, HashMap subset) throws Exception {
    int channelIndex = aggrDomain.valueToIndex(new float[][] {{channel}})[0];

    int idx = sort_indexes[channelIndex];

    int swathAdapterIndex = numAdapters-1;
    for (int k=0; k<numAdapters-1;k++) {
      if (idx >= offset[k] && idx < offset[k+1]) swathAdapterIndex = k;
    }
    channel = aggrSamples[channelIndex];
    FlatField image = adapters[swathAdapterIndex].getImage(channel, subset);
    cs = ((RealTupleType) ((FunctionType)image.getType()).getDomain()).getCoordinateSystem();
    for (int k=0; k<numAdapters;k++) {
      if (k != swathAdapterIndex) adapters[k].setCoordinateSystem(cs);
    }
    return image;
  }

  public int getChannelIndexFromWavenumber(float channel) throws VisADException, RemoteException {
    int idx = (aggrDomain.valueToIndex(new float[][] {{channel}}))[0];
    return idx;
  }

}