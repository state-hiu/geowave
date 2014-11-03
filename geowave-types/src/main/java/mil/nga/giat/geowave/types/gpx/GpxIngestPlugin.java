package mil.nga.giat.geowave.types.gpx;

import com.google.common.collect.Iterators;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.stream.XMLStreamException;

import mil.nga.giat.geowave.index.ByteArrayId;
import mil.nga.giat.geowave.ingest.GeoWaveData;
import mil.nga.giat.geowave.ingest.hdfs.StageToHdfsPlugin;
import mil.nga.giat.geowave.ingest.hdfs.mapreduce.IngestFromHdfsPlugin;
import mil.nga.giat.geowave.ingest.hdfs.mapreduce.IngestWithMapper;
import mil.nga.giat.geowave.ingest.hdfs.mapreduce.IngestWithReducer;
import mil.nga.giat.geowave.ingest.local.LocalFileIngestPlugin;
import mil.nga.giat.geowave.store.CloseableIterator;
import mil.nga.giat.geowave.store.adapter.WritableDataAdapter;
import mil.nga.giat.geowave.store.data.field.FieldVisibilityHandler;
import mil.nga.giat.geowave.store.data.visibility.GlobalVisibilityHandler;
import mil.nga.giat.geowave.store.index.Index;
import mil.nga.giat.geowave.store.index.IndexType;
import mil.nga.giat.geowave.vector.adapter.FeatureDataAdapter;

import org.apache.avro.Schema;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.opengis.feature.simple.SimpleFeature;
import org.xml.sax.SAXException;

/**
 * This plugin is used for ingesting any GPX formatted data from a local file
 * system into GeoWave as GeoTools' SimpleFeatures. It supports the default
 * configuration of spatial and spatial-temporal indices and it will support
 * wither directly ingesting GPX data from a local file system to GeoWave or to
 * stage the data in an intermediate format in HDFS and then to ingest it into
 * GeoWave using a map-reduce job. It supports OSM metadata.xml files if the
 * file is directly in the root base directory that is passed in command-line to
 * the ingest framework.
 */
public class GpxIngestPlugin implements
        LocalFileIngestPlugin<SimpleFeature>,
        IngestFromHdfsPlugin<GpxTrack, SimpleFeature>,
        StageToHdfsPlugin<GpxTrack> {

    private final static Logger LOGGER = Logger.getLogger(GpxIngestPlugin.class);

    private final static String TAG_SEPARATOR = " ||| ";

    private Map<Long, GpxTrack> metadata;
    private static long currentFreeTrackId = 0;

    private final Index[] supportedIndices;

    public GpxIngestPlugin() {

        supportedIndices = new Index[]{
            IndexType.SPATIAL_VECTOR.createDefaultIndex(),
            IndexType.SPATIAL_TEMPORAL_VECTOR.createDefaultIndex()
        };

    }

    @Override
    public String[] getFileExtensionFilters() {
        return new String[]{
            "xml",
            "gpx"
        };
    }

    @Override
    public void init(
            final File baseDirectory) {
        final File f = new File(
                baseDirectory,
                "metadata.xml");
        if (!f.exists()) {
            LOGGER.warn("No metadata file found - looked at: " + f.getAbsolutePath());
            LOGGER.warn("No metadata will be loaded");
        } else {
            try {
                long time = System.currentTimeMillis();
                metadata = GpxUtils.parseOsmMetadata(f);
                time = System.currentTimeMillis() - time;
                final String timespan = String.format(
                        "%d min, %d sec",
                        TimeUnit.MILLISECONDS.toMinutes(time),
                        TimeUnit.MILLISECONDS.toSeconds(time) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time)));
                LOGGER.info("Metadata parsed in in " + timespan + " for " + metadata.size() + " tracks");
            } catch (final XMLStreamException | FileNotFoundException e) {
                LOGGER.warn(
                        "Unable to read OSM metadata file: " + f.getAbsolutePath(),
                        e);
            }
        }

    }

    @Override
    public boolean supportsFile(
            final File file) {
        // if its a gpx extension assume it is supported
        if (file.getName().toLowerCase().endsWith(
                "gpx")) {
            return true;
        }
        // otherwise take a quick peek at the file to ensure it matches the GPX
        // schema
        try {
            return GpxUtils.validateGpx(file);
        } catch (SAXException | IOException e) {
            LOGGER.warn(
                    "Unable to read file:" + file.getAbsolutePath(),
                    e);
        }
        return false;
    }

    @Override
    public Index[] getSupportedIndices() {
        return supportedIndices;
    }

    @Override
    public WritableDataAdapter<SimpleFeature>[] getDataAdapters(
            final String globalVisibility) {
        final FieldVisibilityHandler<SimpleFeature, Object> fieldVisiblityHandler = ((globalVisibility != null) && !globalVisibility.isEmpty()) ? new GlobalVisibilityHandler<SimpleFeature, Object>(
                globalVisibility) : null;
        return new WritableDataAdapter[]{
            new FeatureDataAdapter(
            GPXConsumer.pointType,
            fieldVisiblityHandler),
            new FeatureDataAdapter(
            GPXConsumer.waypointType,
            fieldVisiblityHandler),
            new FeatureDataAdapter(
            GPXConsumer.trackType,
            fieldVisiblityHandler)
        };
    }

    @Override
    public CloseableIterator<GeoWaveData<SimpleFeature>> toGeoWaveData(
            final File input,
            final ByteArrayId primaryIndexId,
            final String globalVisibility) {
        final GpxTrack[] gpxTracks = toHdfsObjects(input);
        final List<CloseableIterator<GeoWaveData<SimpleFeature>>> allData = new ArrayList<CloseableIterator<GeoWaveData<SimpleFeature>>>();
        for (final GpxTrack track : gpxTracks) {
            final CloseableIterator<GeoWaveData<SimpleFeature>> geowaveData = toGeoWaveDataInternal(
                    track,
                    primaryIndexId,
                    globalVisibility);
            allData.add(geowaveData);
        }
        return new CloseableIterator.Wrapper<GeoWaveData<SimpleFeature>>(
                Iterators.concat(allData.iterator()));
    }

    @Override
    public Schema getAvroSchemaForHdfsType() {
        return GpxTrack.getClassSchema();
    }

    @Override
    public GpxTrack[] toHdfsObjects(
            final File input) {
        GpxTrack track = null;
        if (metadata != null) {
            try {
                final long id = Long.parseLong(FilenameUtils.removeExtension(input.getName()));
                track = metadata.remove(id);
            } catch (final NumberFormatException e) {
                LOGGER.info(
                        "OSM metadata found, but track file name is not a numeric ID",
                        e);
            }
        }
        if (track == null) {
            track = new GpxTrack();
            track.setTrackid(currentFreeTrackId++);
        }

        try {
            track.setGpxfile(ByteBuffer.wrap(Files.readAllBytes(input.toPath())));
        } catch (final IOException e) {
            LOGGER.warn(
                    "Unable to read GPX file: " + input.getAbsolutePath(),
                    e);
        }

        return new GpxTrack[]{
            track
        };
    }

    @Override
    public boolean isUseReducerPreferred() {
        return false;
    }

    @Override
    public IngestWithMapper<GpxTrack, SimpleFeature> ingestWithMapper() {
        return new IngestGpxTrackFromHdfs(
                this);
    }

    @Override
    public IngestWithReducer<GpxTrack, ?, ?, SimpleFeature> ingestWithReducer() {
        // unsupported right now
        throw new UnsupportedOperationException(
                "GPX tracks cannot be ingested with a reducer");
    }

    private CloseableIterator<GeoWaveData<SimpleFeature>> toGeoWaveDataInternal(
            final GpxTrack gpxTrack,
            final ByteArrayId primaryIndexId,
            final String globalVisibility) {
        final InputStream in = new ByteArrayInputStream(
                gpxTrack.getGpxfile().array());
        return new GPXConsumer(
                in,
                primaryIndexId,
                gpxTrack.getTrackid().toString(),
                getAdditionalData(gpxTrack),
                globalVisibility);
    }

    @Override
    public Index[] getRequiredIndices() {
        return new Index[]{};
    }

    private Map<String, Map<String, String>> getAdditionalData(final GpxTrack gpxTrack) {
        Map<String, Map<String, String>> pathDataSet = new HashMap<String, Map<String, String>>();
        Map<String, String> dataSet = new HashMap<String, String>();
        pathDataSet.put("gpx.trk", dataSet);

        dataSet.put(
                "TrackId",
                gpxTrack.getTrackid().toString());
        dataSet.put(
                "UserId",
                gpxTrack.getUserid().toString());
        dataSet.put(
                "User",
                gpxTrack.getUser().toString());
        dataSet.put(
                "Description",
                gpxTrack.getDescription().toString());

        if ((gpxTrack.getTags() != null) && (gpxTrack.getTags().size() > 0)) {
            final String tags = org.apache.commons.lang.StringUtils.join(
                    gpxTrack.getTags(),
                    TAG_SEPARATOR);
            dataSet.put(
                    "Tags",
                    tags);
        } else {
            dataSet.put(
                    "Tags",
                    null);
        }
        return pathDataSet;
    }

    public static class IngestGpxTrackFromHdfs implements
            IngestWithMapper<GpxTrack, SimpleFeature> {

        private final GpxIngestPlugin parentPlugin;

        public IngestGpxTrackFromHdfs() {
            this(
                    new GpxIngestPlugin());
            // this constructor will be used when deserialized
        }

        public IngestGpxTrackFromHdfs(
                final GpxIngestPlugin parentPlugin) {
            this.parentPlugin = parentPlugin;
        }

        @Override
        public WritableDataAdapter<SimpleFeature>[] getDataAdapters(
                final String globalVisibility) {
            return parentPlugin.getDataAdapters(globalVisibility);
        }

        @Override
        public CloseableIterator<GeoWaveData<SimpleFeature>> toGeoWaveData(
                final GpxTrack input,
                final ByteArrayId primaryIndexId,
                final String globalVisibility) {
            return parentPlugin.toGeoWaveDataInternal(
                    input,
                    primaryIndexId,
                    globalVisibility);
        }

        @Override
        public byte[] toBinary() {
            return new byte[]{};
        }

        @Override
        public void fromBinary(
                final byte[] bytes) {
        }
    }

}
