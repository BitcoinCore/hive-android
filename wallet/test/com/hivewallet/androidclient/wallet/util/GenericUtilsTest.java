/*
 * Copyright 2013-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.hivewallet.androidclient.wallet.util;

import static net.java.quickcheck.generator.PrimitiveGenerators.fixedValues;
import static net.java.quickcheck.generator.PrimitiveGenerators.longs;
import static net.java.quickcheck.generator.PrimitiveGeneratorSamples.anyInteger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import net.java.quickcheck.generator.iterable.Iterables;

import org.junit.Test;

import com.google.bitcoin.core.NetworkParameters;
import com.hivewallet.androidclient.wallet.util.GenericUtils.BitmapSize;

/**
 * @author Andreas Schildbach
 */
public class GenericUtilsTest
{
	@Test
	public void formatValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("1.00", GenericUtils.formatValue(coin, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(coin, 6, 0));
		assertEquals("1.00", GenericUtils.formatValue(coin, 8, 0));

		final BigInteger justNot = new BigInteger("99999999");
		assertEquals("1.00", GenericUtils.formatValue(justNot, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(justNot, 6, 0));
		assertEquals("0.99999999", GenericUtils.formatValue(justNot, 8, 0));

		final BigInteger slightlyMore = new BigInteger("100000001");
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 4, 0));
		assertEquals("1.00", GenericUtils.formatValue(slightlyMore, 6, 0));
		assertEquals("1.00000001", GenericUtils.formatValue(slightlyMore, 8, 0));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("11223344.5567", GenericUtils.formatValue(value, 4, 0));
		assertEquals("11223344.556678", GenericUtils.formatValue(value, 6, 0));
		assertEquals("11223344.55667788", GenericUtils.formatValue(value, 8, 0));

		assertEquals("21000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 8, 0));
	}

	@Test
	public void formatMbtcValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("1000.00", GenericUtils.formatValue(coin, 2, 3));
		assertEquals("1000.00", GenericUtils.formatValue(coin, 4, 3));

		final BigInteger justNot = new BigInteger("99999990");
		assertEquals("1000.00", GenericUtils.formatValue(justNot, 2, 3));
		assertEquals("999.9999", GenericUtils.formatValue(justNot, 4, 3));

		final BigInteger slightlyMore = new BigInteger("100000010");
		assertEquals("1000.00", GenericUtils.formatValue(slightlyMore, 2, 3));
		assertEquals("1000.0001", GenericUtils.formatValue(slightlyMore, 4, 3));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("11223344556.68", GenericUtils.formatValue(value, 2, 3));
		assertEquals("11223344556.6779", GenericUtils.formatValue(value, 4, 3));

		assertEquals("21000000000.00", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 5, 3));
	}

	@Test
	public void formatUbtcValue() throws Exception
	{
		final BigInteger coin = new BigInteger("100000000");
		assertEquals("1000000", GenericUtils.formatValue(coin, 0, 6));
		assertEquals("1000000", GenericUtils.formatValue(coin, 2, 6));

		final BigInteger justNot = new BigInteger("99999999");
		assertEquals("1000000", GenericUtils.formatValue(justNot, 0, 6));
		assertEquals("999999.99", GenericUtils.formatValue(justNot, 2, 6));

		final BigInteger slightlyMore = new BigInteger("100000001");
		assertEquals("1000000", GenericUtils.formatValue(slightlyMore, 0, 6));
		assertEquals("1000000.01", GenericUtils.formatValue(slightlyMore, 2, 6));

		final BigInteger value = new BigInteger("1122334455667788");
		assertEquals("11223344556678", GenericUtils.formatValue(value, 0, 6));
		assertEquals("11223344556677.88", GenericUtils.formatValue(value, 2, 6));

		assertEquals("21000000000000", GenericUtils.formatValue(NetworkParameters.MAX_MONEY, 2, 6));
	}
	
	@Test
	public void formatAndParseShouldAgree()
	{
		int shift = fixedValues(0, 3, 6).next();
		int precision;
		
		switch (shift) {
			case 3:
				precision = 5;
				break;
			case 6:
				precision = 2;
				break;
			default:
				precision = 8;
		}
		
		for (long value : Iterables.toIterable(longs(0L, NetworkParameters.MAX_MONEY.longValue())))
		{
			String valueStr = GenericUtils.formatValue(BigInteger.valueOf(value), precision, shift);
			long valueParsed = GenericUtils.parseValue(valueStr, shift).longValue();
			assertEquals("Shift: " + shift + "; precision: " + precision
					+ "; intermediate state was: " + valueStr, value, valueParsed);
		}
	}

	@Test
	public void scaledBitmapShouldMaintainRatio()
	{
		int minSize = 50;
		int maxSize = 1000;
		
		int widthBefore = anyInteger(minSize, maxSize);
		int heightBefore = anyInteger(minSize, maxSize);
		int longestSide = anyInteger(minSize, maxSize);
		double ratioBefore = (double)widthBefore / (double)heightBefore;
		
		BitmapSize size = GenericUtils.calculateReasonableSize(widthBefore, heightBefore, longestSide);
		int widthAfter = size.getWidth();
		int heightAfter = size.getHeight();
		double ratioAfter = (double)widthAfter / (double)heightAfter;
		double maxRatioDelta = ratioBefore * 0.2;
		
		assertTrue("Bitmap should have a reasonable size, but: " +
				widthAfter + "x" + heightAfter + " > " + longestSide + "x" + longestSide,
				widthAfter <= longestSide && heightAfter <= longestSide);
		assertEquals("Ratios differ too much. Size before: " + widthBefore + "x" + heightBefore +
				"; size after:" + widthAfter + "x" + heightAfter,
				ratioBefore, ratioAfter, maxRatioDelta);
	}
}
