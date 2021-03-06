package mil.nga.giat.geowave.vector.adapter;

import java.util.Arrays;

import mil.nga.giat.geowave.index.ByteArrayId;
import mil.nga.giat.geowave.index.ByteArrayUtils;
import mil.nga.giat.geowave.index.sfc.data.NumericData;
import mil.nga.giat.geowave.store.TimeUtils;
import mil.nga.giat.geowave.store.adapter.IndexFieldHandler;
import mil.nga.giat.geowave.store.data.PersistentValue;
import mil.nga.giat.geowave.store.data.field.FieldVisibilityHandler;
import mil.nga.giat.geowave.store.dimension.Time;
import mil.nga.giat.geowave.store.dimension.Time.TimeRange;

import org.opengis.feature.simple.SimpleFeature;

/**
 * This class handles the internal responsibility of persisting time ranges
 * based on a start time attribute and end time attribute to and from a GeoWave
 * common index field for SimpleFeature data.
 * 
 */
public class FeatureTimeRangeHandler implements
		IndexFieldHandler<SimpleFeature, Time, Object>
{
	private final FeatureAttributeHandler nativeStartTimeHandler;
	private final FeatureAttributeHandler nativeEndTimeHandler;
	private final FieldVisibilityHandler<SimpleFeature, Object> visibilityHandler;

	public FeatureTimeRangeHandler(
			final FeatureAttributeHandler nativeStartTimeHandler,
			final FeatureAttributeHandler nativeEndTimeHandler ) {
		this(
				nativeStartTimeHandler,
				nativeEndTimeHandler,
				null);
	}

	public FeatureTimeRangeHandler(
			final FeatureAttributeHandler nativeStartTimeHandler,
			final FeatureAttributeHandler nativeEndTimeHandler,
			final FieldVisibilityHandler<SimpleFeature, Object> visibilityHandler ) {
		this.nativeStartTimeHandler = nativeStartTimeHandler;
		this.nativeEndTimeHandler = nativeEndTimeHandler;
		this.visibilityHandler = visibilityHandler;
	}

	@Override
	public ByteArrayId[] getNativeFieldIds() {
		return new ByteArrayId[] {
			nativeStartTimeHandler.getFieldId(),
			nativeEndTimeHandler.getFieldId()
		};
	}

	@Override
	public Time toIndexValue(
			final SimpleFeature row ) {
		final Object startObj = nativeStartTimeHandler.getFieldValue(row);
		final Object endObj = nativeEndTimeHandler.getFieldValue(row);
		byte[] visibility;
		if (visibilityHandler != null) {
			final byte[] startVisibility = visibilityHandler.getVisibility(
					row,
					nativeStartTimeHandler.getFieldId(),
					startObj);
			final byte[] endVisibility = visibilityHandler.getVisibility(
					row,
					nativeEndTimeHandler.getFieldId(),
					endObj);
			if (Arrays.equals(
					startVisibility,
					endVisibility)) {
				// its easy if they both have the same visibility
				visibility = startVisibility;
			}
			else {
				// otherwise the assumption is that we combine the two
				// visibilities
				// TODO make sure this is how we should handle this case
				visibility = ByteArrayUtils.combineArrays(
						startVisibility,
						endVisibility);
			}
		}
		else {
			visibility = new byte[] {};
		}
		return new TimeRange(
				TimeUtils.getTimeMillis(startObj),
				TimeUtils.getTimeMillis(endObj),
				visibility);
	}

	@SuppressWarnings("unchecked")
	@Override
	public PersistentValue<Object>[] toNativeValues(
			final Time indexValue ) {
		final NumericData value = indexValue.toNumericData();
		final Class<?> startBindingClass = nativeStartTimeHandler.attrDesc.getType().getBinding();
		final Object startObj = TimeUtils.getTimeValue(
				startBindingClass,
				(long) value.getMin());
		final Class<?> endBindingClass = nativeEndTimeHandler.attrDesc.getType().getBinding();
		final Object endObj = TimeUtils.getTimeValue(
				endBindingClass,
				(long) value.getMax());
		return new PersistentValue[] {
			new PersistentValue<Object>(
					nativeStartTimeHandler.getFieldId(),
					startObj),
			new PersistentValue<Object>(
					nativeEndTimeHandler.getFieldId(),
					endObj),
		};
	}

}
