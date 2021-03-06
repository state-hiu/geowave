package mil.nga.giat.geowave.index.dimension;

import mil.nga.giat.geowave.index.dimension.bin.BinRange;
import mil.nga.giat.geowave.index.sfc.data.NumericRange;

import org.junit.Assert;
import org.junit.Test;

public class BasicDimensionDefinitionTest
{
	
	private double MINIMUM = 20;
	private double MAXIMUM = 100;
	private double DELTA = 1e-15;

	@Test
	public void testNormalizeMidValue() {

		final double midValue = 60;
		final double normalizedValue = 0.5;

		Assert.assertEquals(
				normalizedValue,
				getNormalizedValueUsingBounds(
						MINIMUM,
						MAXIMUM,
						midValue),
				DELTA);

	};

	@Test
	public void testNormalizeUpperValue() {

		final double lowerValue = 20;
		final double normalizedValue = 0.0;

		Assert.assertEquals(
				normalizedValue,
				getNormalizedValueUsingBounds(
						MINIMUM,
						MAXIMUM,
						lowerValue),
				DELTA);

	};

	@Test
	public void testNormalizeLowerValue() {

		final double upperValue = 100;
		final double normalizedValue = 1.0;

		Assert.assertEquals(
				normalizedValue,
				getNormalizedValueUsingBounds(
						MINIMUM,
						MAXIMUM,
						upperValue),
				DELTA);

	};

	@Test
	public void testNormalizeClampOutOfBoundsValue() {

		final double value = 1;
		final double normalizedValue = 0.0;

		Assert.assertEquals(
				normalizedValue,
				getNormalizedValueUsingBounds(
						MINIMUM,
						MAXIMUM,
						value),
				DELTA);

	};

	@Test
	public void testNormalizeRangesBinRangeCount() {

		final double minRange = 40;
		final double maxRange = 50;
		final int binCount = 1;

		BinRange[] binRange = getNormalizedRangesUsingBounds(
				minRange,
				maxRange);

		Assert.assertEquals(
				binCount,
				binRange.length);

	};

	@Test
	public void testNormalizeClampOutOfBoundsRanges() {

		final double minRange = 1;
		final double maxRange = 150;

		BinRange[] binRange = getNormalizedRangesUsingBounds(
				minRange,
				maxRange);

		Assert.assertEquals(
				MINIMUM,
				binRange[0].getNormalizedMin(),
				DELTA);
		Assert.assertEquals(
				MAXIMUM,
				binRange[0].getNormalizedMax(),
				DELTA);

	};

	private double getNormalizedValueUsingBounds(
			final double min,
			final double max,
			final double value ) {
		return new BasicDimensionDefinition(
				min,
				max).normalize(value);
	}

	private BinRange[] getNormalizedRangesUsingBounds(
			final double minRange,
			final double maxRange ) {

		return new BasicDimensionDefinition(
				MINIMUM,
				MAXIMUM).getNormalizedRanges(new NumericRange(
				minRange,
				maxRange));

	}

}
