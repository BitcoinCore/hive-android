/*
 * Copyright 2014 the original author or authors.
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

package com.hivewallet.androidclient.wallet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.text.format.DateUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.hivewallet.androidclient.wallet.ExchangeRatesProvider.ExchangeRate;

/**
 * @author Andreas Schildbach
 */
public class Configuration
{
	public final int lastVersionCode;

	private final SharedPreferences prefs;

	public static final String PREFS_KEY_BTC_PRECISION = "btc_precision";
	public static final String PREFS_KEY_CONNECTIVITY_NOTIFICATION = "connectivity_notification";
	public static final String PREFS_KEY_EXCHANGE_CURRENCY = "exchange_currency";
	public static final String PREFS_KEY_TRUSTED_PEER = "trusted_peer";
	public static final String PREFS_KEY_TRUSTED_PEER_ONLY = "trusted_peer_only";
	public static final String PREFS_KEY_DISCLAIMER = "disclaimer";
	public static final String PREFS_KEY_SELECTED_ADDRESS = "selected_address";
	private static final String PREFS_KEY_LABS_QR_PAYMENT_REQUEST = "labs_qr_payment_request";

	private static final String PREFS_KEY_LAST_VERSION = "last_version";
	private static final String PREFS_KEY_LAST_USED = "last_used";
	private static final String PREFS_KEY_BEST_CHAIN_HEIGHT_EVER = "best_chain_height_ever";
	private static final String PREFS_KEY_CACHED_EXCHANGE_CURRENCY = "cached_exchange_currency";
	private static final String PREFS_KEY_CACHED_EXCHANGE_RATE = "cached_exchange_rate";
	private static final String PREFS_KEY_CACHED_EXCHANGE_CURRENCIES = "cached_exchange_currencies";
	private static final String PREFS_KEY_LAST_EXCHANGE_DIRECTION = "last_exchange_direction";
	private static final String PREFS_KEY_CHANGE_LOG_VERSION = "change_log_version";
	public static final String PREFS_KEY_REMIND_BACKUP = "remind_backup";

	private static final int PREFS_DEFAULT_BTC_SHIFT = 3;
	private static final int PREFS_DEFAULT_BTC_PRECISION = 2;
	
	private static final String PREFS_KEY_FIND_NEARBY_USER_PHOTO = "find_nearby_user_photo";
	private static final String PREFS_KEY_FIND_NEARBY_USER_NAME = "find_nearby_user_name";

	private static final Logger log = LoggerFactory.getLogger(Configuration.class);

	public Configuration(@Nonnull final SharedPreferences prefs)
	{
		this.prefs = prefs;

		this.lastVersionCode = prefs.getInt(PREFS_KEY_LAST_VERSION, 0);
	}

	public boolean hasBtcPrecision()
	{
		return prefs.contains(PREFS_KEY_BTC_PRECISION);
	}

	public int getBtcPrecision()
	{
		final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, null);
		if (precision != null)
			return precision.charAt(0) - '0';
		else
			return PREFS_DEFAULT_BTC_PRECISION;
	}

	public int getBtcMaxPrecision()
	{
		final int btcShift = getBtcShift();

		if (btcShift == 0)
			return Constants.BTC_MAX_PRECISION;
		else if (btcShift == 3)
			return Constants.MBTC_MAX_PRECISION;
		else
			return Constants.UBTC_MAX_PRECISION;
	}

	public int getBtcShift()
	{
		final String precision = prefs.getString(PREFS_KEY_BTC_PRECISION, null);
		if (precision != null)
			return precision.length() == 3 ? precision.charAt(2) - '0' : 0;
		else
			return PREFS_DEFAULT_BTC_SHIFT;
	}

	public String getBtcPrefix()
	{
		final int btcShift = getBtcShift();

		if (btcShift == 0)
			return Constants.CURRENCY_CODE_BTC;
		else if (btcShift == 3)
			return Constants.CURRENCY_CODE_MBTC;
		else
			return Constants.CURRENCY_CODE_UBTC;
	}

	public boolean getConnectivityNotificationEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_CONNECTIVITY_NOTIFICATION, false);
	}

	public String getTrustedPeerHost()
	{
		return prefs.getString(PREFS_KEY_TRUSTED_PEER, "").trim();
	}

	public boolean getTrustedPeerOnly()
	{
		return prefs.getBoolean(PREFS_KEY_TRUSTED_PEER_ONLY, false);
	}

	public boolean remindBackup()
	{
		return prefs.getBoolean(PREFS_KEY_REMIND_BACKUP, true);
	}

	public void armBackupReminder()
	{
		prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, true).commit();
	}

	public void disarmBackupReminder()
	{
		prefs.edit().putBoolean(PREFS_KEY_REMIND_BACKUP, false).commit();
	}

	public boolean getDisclaimerEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_DISCLAIMER, true);
	}

	public String getSelectedAddress()
	{
		return prefs.getString(PREFS_KEY_SELECTED_ADDRESS, null);
	}

	public void setSelectedAddress(final String address)
	{
		prefs.edit().putString(PREFS_KEY_SELECTED_ADDRESS, address).commit();
	}

	public String getExchangeCurrencyCode()
	{
		return prefs.getString(PREFS_KEY_EXCHANGE_CURRENCY, null);
	}

	public void setExchangeCurrencyCode(final String exchangeCurrencyCode)
	{
		prefs.edit().putString(PREFS_KEY_EXCHANGE_CURRENCY, exchangeCurrencyCode).commit();
	}

	public boolean getQrPaymentRequestEnabled()
	{
		return prefs.getBoolean(PREFS_KEY_LABS_QR_PAYMENT_REQUEST, false);
	}

	public boolean versionCodeCrossed(final int currentVersionCode, final int triggeringVersionCode)
	{
		final boolean wasBelow = lastVersionCode < triggeringVersionCode;
		final boolean wasUsedBefore = lastVersionCode > 0;
		final boolean isNowAbove = currentVersionCode >= triggeringVersionCode;

		return wasUsedBefore && wasBelow && isNowAbove;
	}

	public void updateLastVersionCode(final int currentVersionCode)
	{
		prefs.edit().putInt(PREFS_KEY_LAST_VERSION, currentVersionCode).commit();

		if (currentVersionCode > lastVersionCode)
			log.info("detected app upgrade: " + lastVersionCode + " -> " + currentVersionCode);
		else if (currentVersionCode < lastVersionCode)
			log.warn("detected app downgrade: " + lastVersionCode + " -> " + currentVersionCode);
	}

	public long getLastUsedAgo()
	{
		final long now = System.currentTimeMillis();

		return now - prefs.getLong(PREFS_KEY_LAST_USED, 0);
	}

	public void touchLastUsed()
	{
		final long prefsLastUsed = prefs.getLong(PREFS_KEY_LAST_USED, 0);
		final long now = System.currentTimeMillis();
		prefs.edit().putLong(PREFS_KEY_LAST_USED, now).commit();

		log.info("just being used - last used {} minutes ago", (now - prefsLastUsed) / DateUtils.MINUTE_IN_MILLIS);
	}

	public int getBestChainHeightEver()
	{
		return prefs.getInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, 0);
	}

	public void setBestChainHeightEver(final int bestChainHeightEver)
	{
		prefs.edit().putInt(PREFS_KEY_BEST_CHAIN_HEIGHT_EVER, bestChainHeightEver).commit();
	}

	public ExchangeRate getCachedExchangeRate()
	{
		if (prefs.contains(PREFS_KEY_CACHED_EXCHANGE_CURRENCY) && prefs.contains(PREFS_KEY_CACHED_EXCHANGE_RATE))
		{
			final String cachedExchangeCurrency = prefs.getString(PREFS_KEY_CACHED_EXCHANGE_CURRENCY, null);
			final BigInteger cachedExchangeRate = BigInteger.valueOf(prefs.getLong(PREFS_KEY_CACHED_EXCHANGE_RATE, 0));
			return new ExchangeRate(cachedExchangeCurrency, cachedExchangeRate, null);
		}
		else
		{
			return null;
		}
	}

	public void setCachedExchangeRate(final ExchangeRate cachedExchangeRate)
	{
		final Editor edit = prefs.edit();
		edit.putString(PREFS_KEY_CACHED_EXCHANGE_CURRENCY, cachedExchangeRate.currencyCode);
		edit.putLong(PREFS_KEY_CACHED_EXCHANGE_RATE, cachedExchangeRate.rate.longValue());
		edit.commit();
	}
	
	public List<String> getCachedExchangeCurrencies()
	{
		List<String> currencies = new ArrayList<String>();
		final String currenciesStr = prefs.getString(PREFS_KEY_CACHED_EXCHANGE_CURRENCIES, "");
		
		for (String currency : Splitter.on(',').split(currenciesStr))
		{
			if (!currency.isEmpty())
				currencies.add(currency);
		}
		
		return currencies;
	}
	
	public void setCachedExchangeCurrencies(Iterable<String> currencies)
	{
		final String currenciesStr = Joiner.on(',').join(currencies);
		prefs.edit().putString(PREFS_KEY_CACHED_EXCHANGE_CURRENCIES, currenciesStr).commit();
	}

	public boolean getLastExchangeDirection()
	{
		return prefs.getBoolean(PREFS_KEY_LAST_EXCHANGE_DIRECTION, true);
	}

	public void setLastExchangeDirection(final boolean exchangeDirection)
	{
		prefs.edit().putBoolean(PREFS_KEY_LAST_EXCHANGE_DIRECTION, exchangeDirection).commit();
	}

	public boolean changeLogVersionCodeCrossed(final int currentVersionCode, final int triggeringVersionCode)
	{
		final int changeLogVersion = prefs.getInt(PREFS_KEY_CHANGE_LOG_VERSION, 0);

		final boolean wasBelow = changeLogVersion < triggeringVersionCode;
		final boolean wasUsedBefore = changeLogVersion > 0;
		final boolean isNowAbove = currentVersionCode >= triggeringVersionCode;

		prefs.edit().putInt(PREFS_KEY_CHANGE_LOG_VERSION, currentVersionCode).commit();

		return /* wasUsedBefore && */wasBelow && isNowAbove;
	}
	
	public String getFindNearbyUserName()
	{
		return prefs.getString(PREFS_KEY_FIND_NEARBY_USER_NAME, "");
	}
	
	public String getFindNearbyUserName(String defaultUserName)
	{
		return prefs.getString(PREFS_KEY_FIND_NEARBY_USER_NAME, defaultUserName);
	}
	
	public void setFindNearbyUserName(@Nonnull String userName)
	{
		prefs.edit().putString(PREFS_KEY_FIND_NEARBY_USER_NAME, userName).commit();
	}
	
	public Uri getFindNearbyUserPhoto()
	{
		Uri uri = null;
		String uriStr = prefs.getString(PREFS_KEY_FIND_NEARBY_USER_PHOTO, null);
		if (uriStr != null)
			uri = Uri.parse(uriStr);
		
		return uri;
	}
	
	public void setFindNearbyUserPhoto(@Nullable Uri userPhotoUri)
	{
		if (userPhotoUri != null) {
			prefs.edit().putString(PREFS_KEY_FIND_NEARBY_USER_PHOTO, userPhotoUri.toString()).commit();
		} else {
			prefs.edit().remove(PREFS_KEY_FIND_NEARBY_USER_PHOTO).commit();
		}
	}

	public void registerOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener)
	{
		prefs.registerOnSharedPreferenceChangeListener(listener);
	}

	public void unregisterOnSharedPreferenceChangeListener(final OnSharedPreferenceChangeListener listener)
	{
		prefs.unregisterOnSharedPreferenceChangeListener(listener);
	}
}
