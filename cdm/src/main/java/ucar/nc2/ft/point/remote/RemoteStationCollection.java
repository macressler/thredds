package ucar.nc2.ft.point.remote;

import ucar.ma2.StructureData;
import ucar.nc2.ft.point.*;
import ucar.nc2.ft.*;
import ucar.nc2.stream.CdmRemote;
import ucar.nc2.stream.NcStream;
import ucar.nc2.time.CalendarDateRange;
import ucar.nc2.units.DateUnit;
import ucar.unidata.geoloc.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Connect to remote Station Collection using cdmremote
 *
 * @author caron
 */
public class RemoteStationCollection extends StationTimeSeriesCollectionImpl {
  private String uri;
  protected LatLonRect boundingBoxSubset;
  protected CalendarDateRange dateRangeSubset;
  private boolean restrictedList = false;

  /**
   * Constructor. defer metadata
   *
   * @param uri cdmremote endpoint
   */
  public RemoteStationCollection(String uri, DateUnit timeUnit, String altUnits) {
    super(uri, timeUnit, altUnits);
    this.uri = uri;
  }

  /**
   * initialize the stationHelper.
   */
  @Override
  protected StationHelper initStationHelper() {
    // read in all the stations with the "stations" query
    stationHelper = new StationHelper();
    try (InputStream in = CdmRemote.sendQuery(uri, "req=stations")) {
      PointStream.MessageType mtype = PointStream.readMagic(in);
      if (mtype != PointStream.MessageType.StationList) {
        throw new RuntimeException("Station Request: bad response");
      }

      int len = NcStream.readVInt(in);
      byte[] b = new byte[len];
      NcStream.readFully(in, b);
      PointStreamProto.StationList stationsp = PointStreamProto.StationList.parseFrom(b);
      for (ucar.nc2.ft.point.remote.PointStreamProto.Station sp : stationsp.getStationsList()) {
        Station s = new StationImpl(sp.getId(), sp.getDesc(), sp.getWmoId(), sp.getLat(), sp.getLon(), sp.getAlt());
        stationHelper.addStation(new RemoteStationFeatureImpl(null, null));    // LOOK WRONG
      }
      return stationHelper;

    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }
  }

  /**
   * Constructor for a subset. defer station list.
   *
   * @param uri cdmremote endpoint
   * @param sh  station Helper subset or null.
   */
  protected RemoteStationCollection(String uri, DateUnit timeUnit, String altUnits, StationHelper sh) {
    super(uri, timeUnit, altUnits);
    this.uri = uri;
    this.stationHelper = sh;
    this.restrictedList = (sh != null);
  }

  // note this assumes that a PointFeature is-a StationPointFeature
  public Station getStation(PointFeature feature) throws IOException {
    StationPointFeature stationFeature = (StationPointFeature) feature; // LOOK probably will fail here
    return stationFeature.getStation();
  }


  // Must override default subsetting implementation for efficiency: eg to make a single call to server

  // StationTimeSeriesFeatureCollection

  @Override
  public StationTimeSeriesFeatureCollection subset(List<Station> stations) throws IOException {
    if (stations == null) return this;
    List<StationFeature> subset = stationHelper.getStationFeatures(stations);
    return new RemoteStationCollectionSubset(this, null, null, null); // LOOK WRONG
  }

  @Override
  public StationTimeSeriesFeatureCollection subset(ucar.unidata.geoloc.LatLonRect boundingBox) throws IOException {
    if (boundingBox == null) return this;
    return new RemoteStationCollectionSubset(this, null, boundingBox, null);
  }

  // NestedPointFeatureCollection
  @Override
  public PointFeatureCollection flatten(LatLonRect boundingBox, CalendarDateRange dateRange) throws IOException {
    QueryMaker queryMaker = restrictedList ? new QueryByStationList() : null;
    RemotePointCollection pfc = new RemotePointCollection(uri, getTimeUnit(), getAltUnits(), queryMaker);
    return pfc.subset(boundingBox, dateRange);
  }

  private class QueryByStationList implements QueryMaker {

    public String makeQuery() {
      StringBuilder query = new StringBuilder("stns=");
      for (Station s : stationHelper.getStations()) {
        query.append(s.getName());
        query.append(",");
      }
      return PointDatasetRemote.makeQuery(query.toString(), boundingBoxSubset, dateRangeSubset);
    }
  }

  //////////////////////////////////////////////////////////////////////////////


  private class RemoteStationCollectionSubset extends RemoteStationCollection {
    RemoteStationCollection from;

    RemoteStationCollectionSubset(RemoteStationCollection from, StationHelper sh, LatLonRect filter_bb, CalendarDateRange filter_date) throws IOException {
      super(from.uri, from.getTimeUnit(), from.getAltUnits(), sh);
      this.from = from;

      if (filter_bb == null)
        this.boundingBoxSubset = from.getBoundingBox();
      else
        this.boundingBoxSubset = (from.getBoundingBox() == null) ? filter_bb : from.getBoundingBox().intersect(filter_bb);

      if (filter_date == null) {
        this.dateRangeSubset = from.dateRangeSubset;
      } else {
        this.dateRangeSubset = (from.dateRangeSubset == null) ? filter_date : from.dateRangeSubset.intersect(filter_date);
      }
    }

    @Override
    protected StationHelper initStationHelper() {
      from.initStationHelper();
      this.stationHelper = new StationHelper();
      try {
        this.stationHelper.setStations(this.stationHelper.getStationFeatures(boundingBoxSubset));
        return stationHelper;

      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }


    @Override
    public Station getStation(PointFeature feature) throws IOException {
      return from.getStation(feature);
    }
  }

  ///////////////////////////////////////////////////////////////////////////////

  private class RemoteStationFeatureImpl extends StationTimeSeriesFeatureImpl {
    StationTimeSeriesFeature stnFeature;
    RemotePointFeatureIterator riter;
    CalendarDateRange dateRange;

    RemoteStationFeatureImpl(StationTimeSeriesFeature s, CalendarDateRange dateRange) {
      super(s, RemoteStationCollection.this.getTimeUnit(), RemoteStationCollection.this.getAltUnits(), -1);
      this.stnFeature = s;
      this.dateRange = dateRange;
    }

    // Must override default subsetting implementation to make a single call to server

    // StationTimeSeriesFeature

    @Override
    public StationTimeSeriesFeature subset(CalendarDateRange dateRange) throws IOException {
      if (dateRange == null) return this;
      return new RemoteStationFeatureImpl(stnFeature, dateRange);
    }

    @Override
    public StructureData getFeatureData() throws IOException {
      return stnFeature.getFeatureData();
    }

    // PointCollection
    @Override
    public PointFeatureCollection subset(LatLonRect boundingBox, CalendarDateRange dateRange) throws IOException {
      if (boundingBox != null) {
        if (!boundingBox.contains(s.getLatLon())) return null;
        if (dateRange == null) return this;
      }
      return subset(dateRange);
    }

    // an iterator over the observations for this station
    public PointFeatureIterator getPointFeatureIterator(int bufferSize) throws IOException {
      String query = PointDatasetRemote.makeQuery("stn=" + s.getName(), null, dateRange);

      InputStream in = null;
      try {
        in = CdmRemote.sendQuery(uri, query);

        PointStream.MessageType mtype = PointStream.readMagic(in);
        if (mtype == PointStream.MessageType.End) {  // no obs were found
          in.close();
          return new PointIteratorEmpty(); // return empty iterator
        }

        if (mtype != PointStream.MessageType.PointFeatureCollection) {
          throw new RuntimeException("Station Request: bad response = " + mtype);
        }

        int len = NcStream.readVInt(in);
        byte[] b = new byte[len];
        NcStream.readFully(in, b);
        PointStreamProto.PointFeatureCollection pfc = PointStreamProto.PointFeatureCollection.parseFrom(b);

        riter = new RemotePointFeatureIterator(in, new PointStream.ProtobufPointFeatureMaker(pfc));
        riter.setCalculateBounds(this);
        return riter;

      } catch (Throwable t) {
        if (in != null) in.close();
        throw new IOException(t.getMessage(), t);
      }
    }
  }

}
