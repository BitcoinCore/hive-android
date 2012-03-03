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

package com.hivewallet.androidclient.wallet.ui;

import java.math.BigInteger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import com.hivewallet.androidclient.wallet.Constants;
import com.hivewallet.androidclient.wallet.ExchangeRatesProvider.ExchangeRate;
import com.hivewallet.androidclient.wallet.util.GenericUtils;
import com.hivewallet.androidclient.wallet.util.WalletUtils;
import com.hivewallet.androidclient.wallet.R;

/**
 * @author Jan Vornberger 
 * @author Andreas Schildbach
 */
public final class CurrencyPlusInfoTextView extends TextView
{
	private String prefix = null;
	private String suffix = null;
	private BigInteger amount = null;
	private String currencyCode = null;
	private ExchangeRate exchangeRate = null;
	private int precision = 0;
	private int shift = 0;
	private RelativeSizeSpan insignificantRelativeSizeSpan = null;
	private RelativeSizeSpan insignificantRelativeSizeSpanLocalValue = null;
	private boolean isValidExchangeRate = true;
	private boolean useBold = false;

	public CurrencyPlusInfoTextView(final Context context)
	{
		super(context);
	}

	public CurrencyPlusInfoTextView(final Context context, final AttributeSet attrs)
	{
		super(context, attrs);
	}

	public void setPrefix(@Nonnull final String prefix)
	{
		this.prefix = prefix;
		updateView();
	}
	
	public void setSuffix(@Nonnull final String suffix)
	{
		this.suffix = suffix;
		updateView();
	}
	

	public void setAmount(@Nonnull final BigInteger amount, @Nonnull final String currencyCode)
	{
		this.amount = amount;
		this.currencyCode = currencyCode;
		updateView();
	}

	public void setPrecision(final int precision, final int shift)
	{
		this.precision = precision;
		this.shift = shift;
		updateView();
	}
	
	public void setExchangeRate(@Nullable final ExchangeRate exchangeRate)
	{
		this.exchangeRate = exchangeRate;
		updateView();
	}
	
	public void setValidExchangeRate(boolean isValidExchangeRate)
	{
		this.isValidExchangeRate = isValidExchangeRate;
		updateView();
	}
	
	public void setUseBold(boolean useBold)
	{
		this.useBold = useBold;
		updateView();
	}

	public void setInsignificantRelativeSize(final float insignificantRelativeSize)
	{
		if (insignificantRelativeSize != 1)
		{
			this.insignificantRelativeSizeSpan = new RelativeSizeSpan(insignificantRelativeSize);
			this.insignificantRelativeSizeSpanLocalValue = new RelativeSizeSpan(insignificantRelativeSize);
		}
		else
		{
			this.insignificantRelativeSizeSpan = null;
			this.insignificantRelativeSizeSpanLocalValue = null;
		}
	}

	@Override
	protected void onFinishInflate()
	{
		super.onFinishInflate();

		setInsignificantRelativeSize(0.85f);
	}
	
	private Editable formatAmount(@Nonnull BigInteger myAmount, @Nonnull String mySuffix) {
		final String s;
		s = GenericUtils.formatValue(myAmount, precision, shift);
		
		final Editable text = new SpannableStringBuilder(s);
		WalletUtils.formatSignificant(text, insignificantRelativeSizeSpan, useBold);
		
		if (useBold)
			text.append(Html.fromHtml("<b>&nbsp;" + mySuffix + "</b>"));
		else
			text.append(" " + mySuffix);
		
		return text;
	}
	
	private Editable formatLocalValue(@Nonnull BigInteger myAmount, @Nonnull String mySuffix) {
		final String s;
		s = GenericUtils.formatValue(myAmount, Constants.LOCAL_PRECISION, 0);
		
		final Editable text = new SpannableStringBuilder(s);
		WalletUtils.formatSignificant(text, insignificantRelativeSizeSpanLocalValue, false);
		
		text.append(" " + mySuffix);
		
		return text;
	}	

	private void updateView()
	{
		// prepare amount part, if present
		Editable amountText = null;
		if (amount != null && currencyCode != null)
			amountText = formatAmount(amount, currencyCode);
		
		// prepare local value part, if present
		Editable localValueText = null;
		if (amount != null && exchangeRate != null) {
			final BigInteger localValue = WalletUtils.localValue(amount, exchangeRate.rate);
			String suffix = exchangeRate.currencyCode;
			if (!isValidExchangeRate)
				suffix = "test" + suffix;
			
			localValueText = formatLocalValue(localValue, suffix);
		}
		
		// compile everything, as long as we have at least the amount
		Editable text = null;
		if (amount != null) {
			text = new SpannableStringBuilder();
			
			if (prefix != null) {
				text.append(Html.fromHtml(prefix));
				text.append(" ");
			}
			
			text.append(amountText);
			
			if (localValueText != null) {
				text.append(" (");
				text.append(localValueText);
				text.append(")");
			}
			
			if (suffix != null) {
				text.append(" ");
				text.append(Html.fromHtml(suffix));
			}
		}
		
		setText(text);
	}
}
