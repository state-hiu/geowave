package mil.nga.giat.geowave.accumulo.mapreduce;

import java.io.IOException;

import mil.nga.giat.geowave.accumulo.mapreduce.input.GeoWaveInputFormat;
import mil.nga.giat.geowave.accumulo.mapreduce.input.GeoWaveInputKey;
import mil.nga.giat.geowave.store.adapter.AdapterStore;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.ReduceContext;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.log4j.Logger;

public abstract class GeoWaveWritableOutputReducer<KEYIN, VALUEIN> extends
		Reducer<KEYIN, VALUEIN, GeoWaveInputKey, Writable>
{
	protected static final Logger LOGGER = Logger.getLogger(GeoWaveWritableOutputReducer.class);
	protected AdapterStore adapterStore;

	@Override
	protected void reduce(
			final KEYIN key,
			final Iterable<VALUEIN> values,
			final Reducer<KEYIN, VALUEIN, GeoWaveInputKey, Writable>.Context context )
			throws IOException,
			InterruptedException {
		reduceWritableValues(
				key,
				values,
				context);
	}

	protected void reduceWritableValues(
			final KEYIN key,
			final Iterable<VALUEIN> values,
			final Reducer<KEYIN, VALUEIN, GeoWaveInputKey, Writable>.Context context )
			throws IOException,
			InterruptedException {
		reduceNativeValues(
				key,
				values,
				new NativeReduceContext(
						context,
						adapterStore));
	}

	protected abstract void reduceNativeValues(
			final KEYIN key,
			final Iterable<VALUEIN> values,
			final ReduceContext<KEYIN, VALUEIN, GeoWaveInputKey, Object> context )
			throws IOException,
			InterruptedException;

	@Override
	protected void setup(
			final Reducer<KEYIN, VALUEIN, GeoWaveInputKey, Writable>.Context context )
			throws IOException,
			InterruptedException {
		try {
			adapterStore = new JobContextAdapterStore(
					context,
					GeoWaveInputFormat.getAccumuloOperations(context));
		}
		catch (AccumuloException | AccumuloSecurityException e) {
			LOGGER.warn(
					"Unable to get GeoWave adapter store from job context",
					e);
		}
	}
}